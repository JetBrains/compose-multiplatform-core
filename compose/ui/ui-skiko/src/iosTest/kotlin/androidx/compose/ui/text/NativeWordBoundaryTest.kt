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

package androidx.compose.ui.text

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class, ExperimentalTextApi::class)
class NativeWordBoundaryTest {
    private val initialFlagValue = IosComposeUiTextFlags.isNativeTextTokenizerEnabled

    @BeforeTest
    fun setup() {
        registerSkikoComposeImplementation()
    }

    @AfterTest
    fun tearDown() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = initialFlagValue
        clearSkikoComposeImplementation()
    }

    // Lazy so the registry is populated by [setup] before the resolver is created.
    private val fontFamilyResolver by lazy { createFontFamilyResolver() }

    private fun paragraph(text: String) = Paragraph(
        text = text,
        style = TextStyle(),
        constraints = Constraints(maxWidth = 10_000),
        density = Density(density = 1f),
        fontFamilyResolver = fontFamilyResolver,
        defaultLocaleList = LocaleList.current
    )

    private fun Paragraph.wordBoundaries(text: String) =
        (0..text.length).map { getWordBoundary(it) }

    @Test
    fun isEnabledByDefault() {
        assertTrue(IosComposeUiTextFlags.isNativeTextTokenizerEnabled)
    }

    @Test
    fun chineseIsSegmentedIntoWords() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val text = "我在学习中文"
        val paragraph = paragraph(text)

        assertEquals(TextRange(0, 1), paragraph.getWordBoundary(0))
        assertEquals(TextRange(1, 2), paragraph.getWordBoundary(1))
        assertEquals(TextRange(2, 4), paragraph.getWordBoundary(2))
        assertEquals(TextRange(2, 4), paragraph.getWordBoundary(3))
        assertEquals(TextRange(4, 6), paragraph.getWordBoundary(4))
        assertEquals(TextRange(4, 6), paragraph.getWordBoundary(5))
        assertEquals(TextRange(4, 6), paragraph.getWordBoundary(6))
    }

    @Test
    fun chineseMixedWithLatin() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val text = "我在学习 language 中文。"
        val paragraph = paragraph(text)

        // Whitespace after a word belongs to that word, as with Skia.
        assertEquals(TextRange(2, 4), paragraph.getWordBoundary(text.indexOf(' ')))
        assertEquals(TextRange(5, 13), paragraph.getWordBoundary(text.indexOf('g')))
        assertEquals(TextRange(14, 16), paragraph.getWordBoundary(text.indexOf('中')))
        assertEquals(TextRange(16, 17), paragraph.getWordBoundary(text.indexOf('。')))
    }

    @Test
    fun emojiSequenceIsNotSplit() {
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val family = "👨‍👩‍👧"
        val text = "a $family b"
        val paragraph = paragraph(text)

        val start = text.indexOf(family)
        val familyRange = TextRange(start, start + family.length)
        for (offset in start until start + family.length) {
            assertEquals(familyRange, paragraph.getWordBoundary(offset), "offset $offset")
        }
    }

    @Test
    fun latinMatchesSkia() {
        val text = "Hello, world! It's 3.14 o'clock now. אבג דה"

        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = false
        val skia = paragraph(text).wordBoundaries(text)
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        val native = paragraph(text).wordBoundaries(text)

        assertEquals(skia, native)
    }

    @Test
    fun followsTheFlag() {
        val text = "我在学习中文"
        val paragraph = paragraph(text)

        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = true
        assertEquals(TextRange(2, 4), paragraph.getWordBoundary(2))

        // Skia's ICU data on iOS lacks the CJK dictionary, so it can't find this word.
        IosComposeUiTextFlags.isNativeTextTokenizerEnabled = false
        assertNotEquals(TextRange(2, 4), paragraph.getWordBoundary(2))
    }
}
