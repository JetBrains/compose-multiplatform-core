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

@file:OptIn(ExperimentalTextApi::class)

package androidx.compose.ui.text.font

import androidx.compose.ui.text.ExperimentalTextApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.skia.Typeface as SkTypeface

private class TestSkikoFont(
    override val identity: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader: SkikoFont.TypefaceLoader = NoOpTypefaceLoader,
    variationSettings: FontVariation.Settings = FontVariation.Settings()
) : SkikoFont(loadingStrategy, typefaceLoader, variationSettings)

private object NoOpTypefaceLoader : SkikoFont.TypefaceLoader {
    override fun loadBlocking(font: SkikoFont): SkTypeface? = null
    override suspend fun awaitLoad(font: SkikoFont): SkTypeface? = null
}

class SkikoFontTest {

    @Test
    fun baseCacheKey_isIdentityOnly() {
        val settings = FontVariation.Settings(FontVariation.Setting("wght", 600f))
        val bold = TestSkikoFont(
            identity = "test-font",
            weight = FontWeight.Bold,
            style = FontStyle.Italic,
            variationSettings = settings,
        )
        assertEquals("SkikoFont|test-font", bold.baseCacheKey)
        assertTrue(
            bold.cacheKey.startsWith(
                "SkikoFont|test-font|weight=700|style=Italic|variationSettings="
            )
        )
        assertTrue(bold.cacheKey.contains("wght"))
        val normal = TestSkikoFont(identity = "test-font")
        assertEquals(bold.baseCacheKey, normal.baseCacheKey)
    }

    @Test
    fun baseCacheKey_sameIdentityDifferentWeight_shareBase() {
        val normal = TestSkikoFont(identity = "vf", weight = FontWeight.Normal)
        val bold = TestSkikoFont(identity = "vf", weight = FontWeight.Bold)
        assertEquals(normal.baseCacheKey, bold.baseCacheKey)
        assertNotEquals(normal.cacheKey, bold.cacheKey)
    }

    @Test
    fun cacheKey_defaultWeightAndStyle() {
        val font = TestSkikoFont(identity = "test-font")
        assertEquals(
            "SkikoFont|test-font|weight=400|style=Normal|variationSettings=[]",
            font.cacheKey
        )
    }

    @Test
    fun cacheKey_boldWeight() {
        val font = TestSkikoFont(identity = "bold-font", weight = FontWeight.Bold)
        assertEquals(
            "SkikoFont|bold-font|weight=700|style=Normal|variationSettings=[]",
            font.cacheKey
        )
    }

    @Test
    fun cacheKey_italicStyle() {
        val font = TestSkikoFont(identity = "italic-font", style = FontStyle.Italic)
        assertEquals(
            "SkikoFont|italic-font|weight=400|style=Italic|variationSettings=[]",
            font.cacheKey
        )
    }

    @Test
    fun cacheKey_boldItalic() {
        val font = TestSkikoFont(
            identity = "bold-italic-font",
            weight = FontWeight.Bold,
            style = FontStyle.Italic,
        )
        assertEquals(
            "SkikoFont|bold-italic-font|weight=700|style=Italic|variationSettings=[]",
            font.cacheKey
        )
    }

    @Test
    fun cacheKey_withVariationSettings() {
        val settings = FontVariation.Settings(
            FontVariation.Setting("wght", 600f)
        )
        val font = TestSkikoFont(identity = "var-font", variationSettings = settings)
        val key = font.cacheKey
        assertTrue(key.startsWith("SkikoFont|var-font|weight=400|style=Normal|variationSettings="))
        assertTrue(key.contains("wght"))
    }

    @Test
    fun cacheKey_differentIdentities_produceDifferentKeys() {
        val font1 = TestSkikoFont(identity = "font-a")
        val font2 = TestSkikoFont(identity = "font-b")
        assertNotEquals(font1.cacheKey, font2.cacheKey)
    }

    @Test
    fun cacheKey_differentWeights_produceDifferentKeys() {
        val font1 = TestSkikoFont(identity = "font", weight = FontWeight.Normal)
        val font2 = TestSkikoFont(identity = "font", weight = FontWeight.Bold)
        assertNotEquals(font1.cacheKey, font2.cacheKey)
    }

    @Test
    fun cacheKey_differentStyles_produceDifferentKeys() {
        val font1 = TestSkikoFont(identity = "font", style = FontStyle.Normal)
        val font2 = TestSkikoFont(identity = "font", style = FontStyle.Italic)
        assertNotEquals(font1.cacheKey, font2.cacheKey)
    }

    @Test
    fun cacheKey_differentVariationSettings_produceDifferentKeys() {
        val font1 = TestSkikoFont(
            identity = "font",
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f)),
        )
        val font2 = TestSkikoFont(
            identity = "font",
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 700f)),
        )
        assertNotEquals(font1.cacheKey, font2.cacheKey)
    }

    @Test
    fun cacheKey_sameParameters_produceSameKey() {
        val font1 = TestSkikoFont(
            identity = "font",
            weight = FontWeight.Bold,
            style = FontStyle.Italic,
        )
        val font2 = TestSkikoFont(
            identity = "font",
            weight = FontWeight.Bold,
            style = FontStyle.Italic,
        )
        assertEquals(font1.cacheKey, font2.cacheKey)
    }

    @Test
    fun loadingStrategy_isPreserved() {
        val blocking = TestSkikoFont(
            identity = "font",
            loadingStrategy = FontLoadingStrategy.Blocking,
        )
        assertEquals(FontLoadingStrategy.Blocking, blocking.loadingStrategy)

        val async = TestSkikoFont(
            identity = "font",
            loadingStrategy = FontLoadingStrategy.Async,
        )
        assertEquals(FontLoadingStrategy.Async, async.loadingStrategy)

        val optionalLocal = TestSkikoFont(
            identity = "font",
            loadingStrategy = FontLoadingStrategy.OptionalLocal,
        )
        assertEquals(FontLoadingStrategy.OptionalLocal, optionalLocal.loadingStrategy)
    }

    @Test
    fun typefaceLoader_isPreserved() {
        val loader = NoOpTypefaceLoader
        val font = TestSkikoFont(identity = "font", typefaceLoader = loader)
        assertSame(loader, font.typefaceLoader)
    }

    @Test
    fun variationSettings_isPreserved() {
        val settings = FontVariation.Settings(
            FontVariation.Setting("wght", 500f),
            FontVariation.Setting("ital", 1f)
        )
        val font = TestSkikoFont(identity = "font", variationSettings = settings)
        assertEquals(2, font.variationSettings.settings.size)
        assertEquals("wght", font.variationSettings.settings[0].axisName)
        assertEquals("ital", font.variationSettings.settings[1].axisName)
    }

    @Test
    fun equals_sameDescription_isEqual() {
        val settings = FontVariation.Settings(FontVariation.Setting("wght", 500f))
        val font1 = TestSkikoFont(identity = "font", variationSettings = settings)
        val font2 = TestSkikoFont(identity = "font", variationSettings = settings)
        assertEquals(font1, font2)
        assertEquals(font1.hashCode(), font2.hashCode())
    }

    @Test
    fun equals_ignoresTypefaceLoader() {
        val font1 = TestSkikoFont(identity = "font", typefaceLoader = NoOpTypefaceLoader)
        val font2 = TestSkikoFont(identity = "font", typefaceLoader = OtherTypefaceLoader)
        assertEquals(font1, font2)
    }

    @Test
    fun equals_differentDescription_isNotEqual() {
        val font = TestSkikoFont(identity = "font")
        assertNotEquals(font, TestSkikoFont(identity = "other"))
        assertNotEquals(font, TestSkikoFont(identity = "font", weight = FontWeight.Bold))
        assertNotEquals(font, TestSkikoFont(identity = "font", style = FontStyle.Italic))
        assertNotEquals(
            font,
            TestSkikoFont(identity = "font", loadingStrategy = FontLoadingStrategy.Async)
        )
        assertNotEquals(
            font,
            TestSkikoFont(
                identity = "font",
                variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 500f)),
            )
        )
    }

    @Test
    fun equals_differentSubclass_isNotEqual() {
        val font = TestSkikoFont(identity = "font")
        val other = OtherTestSkikoFont(identity = "font")
        assertNotEquals<SkikoFont>(font, other)
    }
}

private class OtherTestSkikoFont(
    override val identity: String,
) : SkikoFont(FontLoadingStrategy.Blocking, NoOpTypefaceLoader, FontVariation.Settings()) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}

private object OtherTypefaceLoader : SkikoFont.TypefaceLoader {
    override fun loadBlocking(font: SkikoFont): SkTypeface? = null
    override suspend fun awaitLoad(font: SkikoFont): SkTypeface? = null
}
