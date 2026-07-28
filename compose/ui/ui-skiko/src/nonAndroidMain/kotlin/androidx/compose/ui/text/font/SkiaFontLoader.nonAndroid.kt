/*
 * Copyright 2022 The Android Open Source Project
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

@file:OptIn(InternalComposeUiApi::class)

package androidx.compose.ui.text.font

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.font.FontLoadingStrategy.Companion.Async
import androidx.compose.ui.text.font.FontLoadingStrategy.Companion.Blocking
import androidx.compose.ui.text.font.FontLoadingStrategy.Companion.OptionalLocal
import androidx.compose.ui.text.platform.FontCache
import androidx.compose.ui.text.platform.FontLoadResult
import androidx.compose.ui.text.platform.PlatformFont
import androidx.compose.ui.text.platform.cloneWithVariationSettings
import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Skia-backed [PlatformTypefacesLoader]. ui-text adapts this into its internal `PlatformFontLoader`
 * via `createPlatformFontFamilyResolver`, so no skia type or ui-text internal leaks across modules.
 */
internal class SkiaFontLoader(
    fontCacheProvider: () -> FontCache
) : PlatformTypefacesLoader {

    constructor(fontCache: FontCache = FontCache()) : this(fontCacheProvider = { fontCache })

    private val fontCache: FontCache by lazy(fontCacheProvider)

    override val fontCollection: Any
        get() = fontCache.fonts

    override fun loadBlocking(font: Font): FontLoadResult? {
        if (font is SkikoFont) {
            val unvaried = fontCache.get(font.baseCacheKey)
                ?: font.typefaceLoader.loadBlocking(font)
                ?: return null
            return finishSkikoFont(font, unvaried)
        }

        if (font !is PlatformFont) {
            if (font.loadingStrategy != OptionalLocal) {
                throw IllegalArgumentException("Unsupported font type: $font")
            }
            return null
        }

        return when (font.loadingStrategy) {
            Blocking -> fontCache.load(font)
            OptionalLocal -> kotlin.runCatching { fontCache.load(font) }.getOrNull()
            Async -> throw UnsupportedOperationException("Unsupported Async font load path")
            else -> throw IllegalArgumentException(
                "Unknown loading type ${font.loadingStrategy}"
            )
        }
    }

    override fun loadPlatformTypes(
        fontFamily: FontFamily,
        fontWeight: FontWeight,
        fontStyle: FontStyle
    ): Any = fontCache.loadPlatformTypes(fontFamily, fontWeight, fontStyle)

    override suspend fun awaitLoad(font: Font): FontLoadResult? {
        return when (font) {
            is SkikoFont -> {
                val unvaried = fontCache.get(font.baseCacheKey)
                    ?: font.typefaceLoader.awaitLoad(font)
                    ?: return null
                finishSkikoFont(font, unvaried)
            }
            // Built-in PlatformFont types remain blocking-only.
            is PlatformFont -> loadBlocking(font)
            else -> throw IllegalArgumentException("Unsupported font type: $font")
        }
    }

    override val cacheKey: Any?
        get() = fontCache // results are valid for all shared caches

    /**
     * Two-Level cache for [SkikoFont]
     * 1. Cache unvaried typeface under [SkikoFont.baseCacheKey]
     * 2. Clone variation and cache it under [SkikoFont.cacheKey]
     */
    private fun finishSkikoFont(font: SkikoFont, unvaried: SkTypeface): FontLoadResult {
        fontCache.put(font.baseCacheKey, unvaried)
        return fontCache.register(
            unvaried.cloneWithVariationSettings(font.variationSettings),
            font.cacheKey,
        )
    }
}
