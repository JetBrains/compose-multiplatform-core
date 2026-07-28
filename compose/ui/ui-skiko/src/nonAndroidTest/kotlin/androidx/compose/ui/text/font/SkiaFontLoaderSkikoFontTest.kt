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

package androidx.compose.ui.text.font

import androidx.compose.ui.text.platform.FontLoadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.test.IgnoreJsTarget
import kotlinx.test.IgnoreWasmTarget
import org.jetbrains.skia.Typeface as SkTypeface

@IgnoreJsTarget
@IgnoreWasmTarget
class SkiaFontLoaderSkikoFontTest {

    private val loader = SkiaFontLoader()

    private fun createTestFont(
        identity: String = "test-font",
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
        loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking,
        typefaceLoader: SkikoFont.TypefaceLoader = BlockingTypefaceLoader(),
        variationSettings: FontVariation.Settings = FontVariation.Settings()
    ): TestSkikoFontImpl = TestSkikoFontImpl(
        identity = identity,
        weight = weight,
        style = style,
        loadingStrategy = loadingStrategy,
        typefaceLoader = typefaceLoader,
        variationSettings = variationSettings
    )

    // --- loadBlocking ---

    @Test
    fun loadBlocking_skikoFont_returnsNonNull() {
        val font = createTestFont()
        val result = loader.loadBlocking(font) as? FontLoadResult
        assertNotNull(result)
        assertNotNull(result.typeface)
    }

    @Test
    fun loadBlocking_skikoFont_returnsNullWhenLoaderReturnsNull() {
        val font = createTestFont(
            typefaceLoader = object : SkikoFont.TypefaceLoader {
                override fun loadBlocking(font: SkikoFont): SkTypeface? = null
                override suspend fun awaitLoad(font: SkikoFont): SkTypeface? = null
            }
        )
        val result = loader.loadBlocking(font)
        assertNull(result)
    }

    @Test
    fun loadBlocking_skikoFont_resultAliasesContainCacheKey() {
        val font = createTestFont(identity = "my-font")
        val result = loader.loadBlocking(font) as FontLoadResult
        assertEquals(1, result.aliases.size)
        assertEquals(font.cacheKey, result.aliases.first())
    }

    @Test
    fun loadBlocking_skikoFont_cachedOnSecondCall() {
        val font = createTestFont(identity = "cached-font")
        val result1 = loader.loadBlocking(font) as FontLoadResult
        val result2 = loader.loadBlocking(font) as FontLoadResult
        assertSame(result1.typeface, result2.typeface)
    }

    @Test
    fun loadBlocking_skikoFont_differentFontsProduceDifferentResults() {
        val font1 = createTestFont(identity = "font-1")
        val font2 = createTestFont(identity = "font-2")
        val result1 = loader.loadBlocking(font1) as FontLoadResult
        val result2 = loader.loadBlocking(font2) as FontLoadResult
        assertEquals(
            "SkikoFont|font-1|weight=400|style=Normal|variationSettings=[]",
            result1.aliases.first()
        )
        assertEquals(
            "SkikoFont|font-2|weight=400|style=Normal|variationSettings=[]",
            result2.aliases.first()
        )
    }

    @Test
    fun loadBlocking_skikoFont_differentWeightsProduceDifferentCacheKeys() {
        val font1 = createTestFont(identity = "font", weight = FontWeight.Normal)
        val font2 = createTestFont(identity = "font", weight = FontWeight.Bold)
        val result1 = loader.loadBlocking(font1) as FontLoadResult
        val result2 = loader.loadBlocking(font2) as FontLoadResult
        val alias1 = result1.aliases.first()
        val alias2 = result2.aliases.first()
        assertTrue(alias1.contains("weight=400"))
        assertTrue(alias2.contains("weight=700"))
    }

    @Test
    fun loadBlocking_skikoFont_withVariationSettings_returnsNonNull() {
        val settings = FontVariation.Settings(FontVariation.Setting("wght", 600f))
        val font = createTestFont(identity = "var-font", variationSettings = settings)
        val result = loader.loadBlocking(font) as? FontLoadResult
        assertNotNull(result)
        assertNotNull(result.typeface)
        assertEquals(font.cacheKey, result.aliases.first())
    }

    @Test
    fun loadBlocking_sameBaseDifferentVariation_loadsUnvariedOnce() {
        var loadCount = 0
        val typefaceLoader = object : SkikoFont.TypefaceLoader {
            override fun loadBlocking(font: SkikoFont): SkTypeface {
                loadCount++
                return SkTypeface.makeEmpty()
            }

            override suspend fun awaitLoad(font: SkikoFont): SkTypeface = SkTypeface.makeEmpty()
        }
        val fontA = createTestFont(
            identity = "shared-base",
            typefaceLoader = typefaceLoader,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f)),
        )
        val fontB = createTestFont(
            identity = "shared-base",
            typefaceLoader = typefaceLoader,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 700f)),
        )
        assertEquals(fontA.baseCacheKey, fontB.baseCacheKey)
        val resultA = loader.loadBlocking(fontA) as FontLoadResult
        val resultB = loader.loadBlocking(fontB) as FontLoadResult
        assertEquals(1, loadCount)
        assertEquals(fontA.cacheKey, resultA.aliases.first())
        assertEquals(fontB.cacheKey, resultB.aliases.first())
        assertNotEquals(resultA.aliases.first(), resultB.aliases.first())
        assertNotNull(resultA.typeface)
        assertNotNull(resultB.typeface)
    }

    @Test
    fun loadBlocking_sameIdentityDifferentWeight_loadsUnvariedOnce() {
        var loadCount = 0
        val typefaceLoader = object : SkikoFont.TypefaceLoader {
            override fun loadBlocking(font: SkikoFont): SkTypeface {
                loadCount++
                return SkTypeface.makeEmpty()
            }

            override suspend fun awaitLoad(font: SkikoFont): SkTypeface = SkTypeface.makeEmpty()
        }
        val regular = createTestFont(
            identity = "variable-file",
            weight = FontWeight.Normal,
            typefaceLoader = typefaceLoader,
        )
        val bold = createTestFont(
            identity = "variable-file",
            weight = FontWeight.Bold,
            typefaceLoader = typefaceLoader,
        )
        assertEquals(regular.baseCacheKey, bold.baseCacheKey)
        loader.loadBlocking(regular)
        loader.loadBlocking(bold)
        assertEquals(1, loadCount)
    }

    @Test
    fun awaitLoad_sameBaseDifferentVariation_loadsUnvariedOnce() {
        runBlocking {
            var loadCount = 0
            val typefaceLoader = object : SkikoFont.TypefaceLoader {
                override fun loadBlocking(font: SkikoFont): SkTypeface? =
                    error("should not be called")

                override suspend fun awaitLoad(font: SkikoFont): SkTypeface {
                    loadCount++
                    return SkTypeface.makeEmpty()
                }
            }
            val fontA = createTestFont(
                identity = "shared-base-async",
                typefaceLoader = typefaceLoader,
                variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f)),
            )
            val fontB = createTestFont(
                identity = "shared-base-async",
                typefaceLoader = typefaceLoader,
                variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 700f)),
            )
            assertEquals(fontA.baseCacheKey, fontB.baseCacheKey)
            val resultA = loader.awaitLoad(fontA) as FontLoadResult
            val resultB = loader.awaitLoad(fontB) as FontLoadResult
            assertEquals(1, loadCount)
            assertEquals(fontA.cacheKey, resultA.aliases.first())
            assertEquals(fontB.cacheKey, resultB.aliases.first())
            assertNotNull(resultA.typeface)
            assertNotNull(resultB.typeface)
        }
    }

    @Test
    fun loadBlocking_nullUnvaried_isNotCached() {
        var loadCount = 0
        val typefaceLoader = object : SkikoFont.TypefaceLoader {
            override fun loadBlocking(font: SkikoFont): SkTypeface? {
                loadCount++
                return if (loadCount == 1) null else SkTypeface.makeEmpty()
            }

            override suspend fun awaitLoad(font: SkikoFont): SkTypeface? = null
        }
        val font = createTestFont(identity = "nullable-base", typefaceLoader = typefaceLoader)
        assertNull(loader.loadBlocking(font))
        val second = loader.loadBlocking(font) as? FontLoadResult
        assertNotNull(second)
        assertEquals(2, loadCount)
    }

    // --- awaitLoad ---

    @Test
    fun awaitLoad_skikoFont_returnsNonNull() {
        runBlocking {
            val font = createTestFont()
            val result = loader.awaitLoad(font) as? FontLoadResult
            assertNotNull(result)
            assertNotNull(result.typeface)
        }
    }

    @Test
    fun awaitLoad_nullUnvaried_isNotCached() {
        runBlocking {
            var loadCount = 0
            val typefaceLoader = object : SkikoFont.TypefaceLoader {
                override fun loadBlocking(font: SkikoFont): SkTypeface? =
                    error("should not be called")

                override suspend fun awaitLoad(font: SkikoFont): SkTypeface? {
                    loadCount++
                    return if (loadCount == 1) null else SkTypeface.makeEmpty()
                }
            }
            val font = createTestFont(
                identity = "nullable-base-async",
                typefaceLoader = typefaceLoader,
            )
            assertNull(loader.awaitLoad(font))
            val second = loader.awaitLoad(font) as? FontLoadResult
            assertNotNull(second)
            assertEquals(2, loadCount)
        }
    }

    @Test
    fun awaitLoad_skikoFont_returnsNullWhenLoaderReturnsNull() {
        runBlocking {
            val font = createTestFont(
                typefaceLoader = object : SkikoFont.TypefaceLoader {
                    override fun loadBlocking(font: SkikoFont): SkTypeface? = null
                    override suspend fun awaitLoad(font: SkikoFont): SkTypeface? = null
                }
            )
            val result = loader.awaitLoad(font)
            assertNull(result)
        }
    }

    @Test
    fun awaitLoad_skikoFont_resultAliasesContainCacheKey() {
        runBlocking {
            val font = createTestFont(identity = "async-font")
            val result = loader.awaitLoad(font) as FontLoadResult
            assertEquals(1, result.aliases.size)
            assertEquals(font.cacheKey, result.aliases.first())
        }
    }

    @Test
    fun awaitLoad_skikoFont_usesAwaitLoadOfTypefaceLoader() {
        runBlocking {
            var awaitLoadCalled = false
            val typefaceLoader = object : SkikoFont.TypefaceLoader {
                override fun loadBlocking(font: SkikoFont): SkTypeface? =
                    error("should not be called")

                override suspend fun awaitLoad(font: SkikoFont): SkTypeface {
                    awaitLoadCalled = true
                    return SkTypeface.makeEmpty()
                }
            }
            val font = createTestFont(typefaceLoader = typefaceLoader)
            loader.awaitLoad(font)
            assertTrue(
                awaitLoadCalled,
                "awaitLoad should call TypefaceLoader.awaitLoad, not loadBlocking"
            )
        }
    }

    @Test
    fun awaitLoad_skikoFont_withVariationSettings_returnsNonNull() {
        runBlocking {
            val settings = FontVariation.Settings(FontVariation.Setting("wght", 600f))
            val font = createTestFont(identity = "async-var-font", variationSettings = settings)
            val result = loader.awaitLoad(font) as? FontLoadResult
            assertNotNull(result)
            assertNotNull(result.typeface)
        }
    }

    @Test
    fun awaitLoad_unknownFont_throws() {
        runBlocking {
            val font = object : Font {
                override val weight: FontWeight = FontWeight.Normal
                override val style: FontStyle = FontStyle.Normal
                override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking
            }
            assertFailsWith<IllegalArgumentException> {
                loader.awaitLoad(font)
            }
        }
    }

    @Test
    fun loadBlocking_optionalLocalUnknownFont_returnsNull() {
        val font = object : Font {
            override val weight: FontWeight = FontWeight.Normal
            override val style: FontStyle = FontStyle.Normal
            override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.OptionalLocal
        }
        // loadBlocking path for OptionalLocal unknown fonts returns null without throwing.
        assertNull(loader.loadBlocking(font))
    }
}

private class TestSkikoFontImpl(
    override val identity: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal,
    loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader: SkikoFont.TypefaceLoader = BlockingTypefaceLoader(),
    variationSettings: FontVariation.Settings = FontVariation.Settings(),
) : SkikoFont(loadingStrategy, typefaceLoader, variationSettings)

private class BlockingTypefaceLoader : SkikoFont.TypefaceLoader {
    override fun loadBlocking(font: SkikoFont): SkTypeface = SkTypeface.makeEmpty()
    override suspend fun awaitLoad(font: SkikoFont): SkTypeface = SkTypeface.makeEmpty()
}
