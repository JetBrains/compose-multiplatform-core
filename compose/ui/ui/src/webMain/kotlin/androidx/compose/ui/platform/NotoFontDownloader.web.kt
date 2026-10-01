/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import androidx.collection.IntList
import androidx.collection.ObjectList
import androidx.collection.mutableIntListOf
import androidx.collection.mutableIntSetOf
import androidx.collection.mutableObjectListOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilterNotNull
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlin.collections.plusAssign
import kotlinx.browser.window
import org.jetbrains.skiko.loadBytesFromPath

/**
 * Idea and some implementation details are adapted from
 * https://github.com/flutter/flutter/blob/master/engine/src/flutter/lib/web_ui/lib/src/engine/font_fallbacks.dart
 */
internal class NotoFontDownloader : FallbackFontDownloader {
    private val codePointsWithNoKnownFont = mutableIntSetOf()
    private val codePointToComponents by lazy { UnicodePropertyLookup.create() }

    override suspend fun downloadFallbackFont(codepoints: Set<Int>): List<FontFamily> {
        val fontsToDownload = getFontsToDownload(codepoints)
        val fonts = fontsToDownload.fastMap { font ->
            val fontUrl = FONT_FALLBACK_BASE_URL + font.font.url
            try {
                val bytes = loadBytesFromPath(fontUrl)
                FontFamily(Font(font.font.name, bytes))
            } catch (e: Throwable) {
                println("Failed to download fallback font [$fontUrl]: $e")
                null
            }
        }
        if (fonts.isNotEmpty() && fonts.fastAll { it == null }) {
            // we need to throw an error because we want to retry it later
            error("Failed to download fallback fonts for codepoints: $codepoints")
        }

        return fonts.fastFilterNotNull()
    }

    internal fun getFontsToDownload(
        codepoints: Set<Int>,
        language: String = window.navigator.language
    ): List<IndexedNotoFont> {
        if (codepoints.isEmpty()) return emptyList()

        val missingCodePoints = mutableIntListOf()
        val requiredComponents = mutableObjectListOf<FallbackFontComponent>()
        val candidateFonts = mutableListOf<IndexedNotoFont>()

        for (codePoint in codepoints) {
            if (codePoint in codePointsWithNoKnownFont || codePoint !in 0..MAX_CODE_POINT) continue

            val component = codePointToComponents.lookup(codePoint)
            if (component.fonts.isEmpty()) {
                missingCodePoints += codePoint
            } else {
                if (component.coverCount == 0) {
                    requiredComponents += component
                }
                component.coverCount++
            }
        }
        if (missingCodePoints.isNotEmpty()) {
            missingCodePoints.forEach { element -> codePointsWithNoKnownFont.plusAssign(element) }
        }

        if (requiredComponents.isEmpty()) return emptyList()

        requiredComponents.forEach { component ->
            component.fonts.fastForEach { font ->
                if (font.coverCount == 0) {
                    candidateFonts += font
                }
                font.coverCount += component.coverCount
                font.coverComponents += component
            }
        }

        val selectedFonts = mutableListOf<IndexedNotoFont>()
        while (candidateFonts.isNotEmpty()) {
            val selectedFont = candidateFonts.selectFont(language)
            selectedFonts += selectedFont

            selectedFont.coverComponents.toList().fastForEach { component ->
                component.fonts.fastForEach { font ->
                    font.coverCount -= component.coverCount
                    font.coverComponents.remove(component)
                }
                component.coverCount = 0
            }

            candidateFonts.removeAll { it.coverCount == 0 }
        }

        return selectedFonts.fastDistinctBy { it.index }
    }

    internal fun getCodepointsWithNoKnownFont(): Set<Int> {
        return buildSet {
            codePointsWithNoKnownFont.forEach(::add)
        }
    }

    private fun List<IndexedNotoFont>.selectFont(language: String): IndexedNotoFont {
        val fonts = this
        var maxCodePointsCovered = -1
        val bestFonts = mutableListOf<IndexedNotoFont>()
        var bestFont: IndexedNotoFont? = null

        fonts.fastForEach { font ->
            when {
                font.coverCount > maxCodePointsCovered -> {
                    bestFonts.clear()
                    bestFonts += font
                    bestFont = font
                    maxCodePointsCovered = font.coverCount
                }

                font.coverCount == maxCodePointsCovered -> {
                    bestFonts += font
                    if (bestFont == null || font.index < bestFont.index) {
                        bestFont = font
                    }
                }
            }
        }

        var bestFontForLanguage: IndexedNotoFont? = null
        if (bestFonts.size > 1) {
            if (bestFonts.fastAll { it.font.isCjkFont }) {
                bestFontForLanguage =
                    bestFonts.selectBestFontForLanguage(language)
                        ?: fonts.selectBestFontForLanguage(language)
            } else {
                bestFont =
                    bestFonts.fastFirstOrNull { it.font.isNotoColorEmoji() }
                        ?: bestFonts.fastFirstOrNull { it.font.isNotoSansSymbols() }
                        ?: bestFonts.fastFirstOrNull { it.font.isNotoSansSC() }
                        ?: bestFont
            }
        }

        return bestFontForLanguage ?: bestFont ?: error("No fallback font selected")
    }

    private fun List<IndexedNotoFont>.selectBestFontForLanguage(language: String): IndexedNotoFont? {
        val fonts = this
        return when (language) {
            "zh-Hans", "zh-CN", "zh-SG", "zh-MY" -> fonts.fastFirstOrNull { it.font.isNotoSansSC() }
            "zh-Hant", "zh-TW", "zh-MO" -> fonts.fastFirstOrNull { it.font.isNotoSansTC() }
            "zh-HK" -> fonts.fastFirstOrNull { it.font.isNotoSansHK() }
            "ja" -> fonts.fastFirstOrNull { it.font.isNotoSansJP() }
            "ko" -> fonts.fastFirstOrNull { it.font.isNotoSansKR() }
            else -> null
        }
    }
}

internal class IndexedNotoFont(val index: Int, val font: NotoFont) {
    var coverCount: Int = 0
    val coverComponents = mutableListOf<FallbackFontComponent>()
}

internal class FallbackFontComponent(val fonts: List<IndexedNotoFont>) {
    var coverCount: Int = 0
}

private class UnicodePropertyLookup(
    private val boundaries: IntList,
    private val values: ObjectList<FallbackFontComponent>,
) {
    fun lookup(value: Int): FallbackFontComponent {
        var start = 0
        var end = boundaries.size
        while (true) {
            if (start == end) {
                return values[start]
            }
            val mid = start + (end - start) / 2
            if (value >= boundaries[mid]) {
                start = mid + 1
            } else {
                end = mid
            }
        }
    }

    companion object {
        val fallbackFonts by lazy { getNotoFonts() }

        fun create(): UnicodePropertyLookup {
            val packedData = encodedNotoFontSetRanges
            val propertyEnumValues = decodeFontComponents(encodedNotoFontSets)

            val boundaries = mutableIntListOf()
            val values = mutableObjectListOf<FallbackFontComponent>()

            var start = 0
            var prefix = 0
            var size = 1

            for (ch in packedData) {
                val code = ch.code
                when {
                    code in RANGE_VALUE_DIGIT_0 until RANGE_VALUE_DIGIT_0 + RANGE_VALUE_RADIX -> {
                        val index = prefix * RANGE_VALUE_RADIX + (code - RANGE_VALUE_DIGIT_0)
                        val value = propertyEnumValues[index]
                        start += size
                        boundaries += start
                        values += value
                        prefix = 0
                        size = 1
                    }
                    code in RANGE_SIZE_DIGIT_0 until RANGE_SIZE_DIGIT_0 + RANGE_SIZE_RADIX -> {
                        size = prefix * RANGE_SIZE_RADIX + (code - RANGE_SIZE_DIGIT_0) + 2
                        prefix = 0
                    }
                    code in PREFIX_DIGIT_0 until PREFIX_DIGIT_0 + PREFIX_RADIX -> {
                        prefix = prefix * PREFIX_RADIX + (code - PREFIX_DIGIT_0)
                    }
                    else -> error("Unexpected encoded range character: $ch")
                }
            }

            check(start == MAX_CODE_POINT + 1) {
                "Bad fallback map size: $start"
            }

            return UnicodePropertyLookup(boundaries, values)
        }

        private fun decodeFontComponents(data: String): List<FallbackFontComponent> {
            return data.split(',').fastMap { componentData ->
                FallbackFontComponent(decodeFontSet(componentData))
            }
        }

        private fun decodeFontSet(data: String): List<IndexedNotoFont> {
            val result = mutableListOf<IndexedNotoFont>()
            var previousIndex = -1
            var prefix = 0

            for (ch in data) {
                val code = ch.code
                when {
                    code in FONT_INDEX_DIGIT_0 until FONT_INDEX_DIGIT_0 + FONT_INDEX_RADIX -> {
                        val delta = prefix * FONT_INDEX_RADIX + (code - FONT_INDEX_DIGIT_0)
                        val index = previousIndex + delta + 1
                        result += IndexedNotoFont(index, fallbackFonts[index])
                        previousIndex = index
                        prefix = 0
                    }
                    code in PREFIX_DIGIT_0 until PREFIX_DIGIT_0 + PREFIX_RADIX -> {
                        prefix = prefix * PREFIX_RADIX + (code - PREFIX_DIGIT_0)
                    }
                    else -> error("Unexpected encoded font-set character: $ch")
                }
            }

            return result
        }
    }
}

private val NotoFont.isCjkFont: Boolean
    get() = isNotoSansSC() || isNotoSansTC() || isNotoSansHK() || isNotoSansJP() || isNotoSansKR()
private fun NotoFont.isNotoSansSC(): Boolean = name.startsWith("Noto Sans SC")
private fun NotoFont.isNotoSansTC(): Boolean = name.startsWith("Noto Sans TC")
private fun NotoFont.isNotoSansHK(): Boolean = name.startsWith("Noto Sans HK")
private fun NotoFont.isNotoSansJP(): Boolean = name.startsWith("Noto Sans JP")
private fun NotoFont.isNotoSansKR(): Boolean = name.startsWith("Noto Sans KR")
private fun NotoFont.isNotoColorEmoji(): Boolean = name.startsWith("Noto Color Emoji")
private fun NotoFont.isNotoSansSymbols(): Boolean = name.startsWith("Noto Sans Symbols")

private const val FONT_FALLBACK_BASE_URL = "https://fonts.gstatic.com/s/"
private const val PREFIX_DIGIT_0 = 48
private const val PREFIX_RADIX = 10
private const val FONT_INDEX_DIGIT_0 = 97
private const val FONT_INDEX_RADIX = 26
private const val RANGE_SIZE_DIGIT_0 = 97
private const val RANGE_SIZE_RADIX = 26
private const val RANGE_VALUE_DIGIT_0 = 65
private const val RANGE_VALUE_RADIX = 26
private const val MAX_CODE_POINT = 0x10FFFF