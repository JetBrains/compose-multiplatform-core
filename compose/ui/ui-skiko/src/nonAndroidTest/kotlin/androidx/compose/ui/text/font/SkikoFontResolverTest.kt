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

import androidx.compose.runtime.State
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.platform.FontLoadResult
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.test.IgnoreJsTarget
import kotlinx.test.IgnoreWasmTarget
import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Loads [SkikoFont]s through a real [FontFamily.Resolver] backed by [SkiaFontLoader].
 *
 * Each test uses its own [SkiaFontLoader], so results don't leak between tests through the global
 * typeface caches, which are keyed by the loader's cache key.
 */
@IgnoreJsTarget
@IgnoreWasmTarget
@OptIn(InternalComposeUiApi::class)
class SkikoFontResolverTest {

    @Test
    fun resolve_asyncFont_returnsFallbackThenLoadedTypeface() = runTest {
        val typeface = CompletableDeferred<SkTypeface>()
        val font = AsyncTestFont("resolver-async", GateTypefaceLoader(typeface))
        val resolver = createResolver()

        val result = resolver.resolve(font.toFontFamily())
        assertFalse(result.isLoaded(font), "async font must not be available before it loads")

        typeface.complete(SkTypeface.makeEmpty())
        advanceUntilIdle()
        assertTrue(result.isLoaded(font))
    }

    @Test
    fun preload_asyncFont_resolvesImmediatelyAfterwards() = runTest {
        val typefaceLoader = CountingTypefaceLoader { SkTypeface.makeEmpty() }
        val font = AsyncTestFont("resolver-preload", typefaceLoader)
        val resolver = createResolver()

        resolver.preload(font.toFontFamily())

        assertTrue(resolver.resolve(font.toFontFamily()).isLoaded(font))
        assertEquals(1, typefaceLoader.loadCount)
    }

    @Test
    fun resolve_asyncFont_nullLoadIsPermanentFailure() = runTest {
        val typefaceLoader = CountingTypefaceLoader { null }
        val font = AsyncTestFont("resolver-null", typefaceLoader)
        val resolver = createResolver()

        resolver.resolve(font.toFontFamily())
        advanceUntilIdle()
        assertEquals(1, typefaceLoader.loadCount)

        // An equal font instance hits the cached failure instead of loading again.
        val sameFont = AsyncTestFont("resolver-null", typefaceLoader)
        val result = resolver.resolve(sameFont.toFontFamily())
        advanceUntilIdle()

        assertFalse(result.isLoaded(font))
        assertEquals(1, typefaceLoader.loadCount)
    }

    @Test
    fun preload_asyncFont_nullLoadThrowsIllegalStateException() = runTest {
        val font = AsyncTestFont("resolver-preload-null", CountingTypefaceLoader { null })
        val resolver = createResolver()

        assertFailsWith<IllegalStateException> {
            resolver.preload(font.toFontFamily())
        }
        Unit
    }

    /**
     * Runs async font loads on the test dispatcher instead of the main dispatcher.
     *
     * Only the dispatcher is passed: the resolver parents its own SupervisorJob to any injected
     * Job, and that job never completes, so passing the test's Job would keep runTest waiting.
     */
    private fun TestScope.createResolver(): FontFamily.Resolver =
        createPlatformFontFamilyResolver(
            SkiaFontLoader(),
            coroutineContext[ContinuationInterceptor]!!,
        )
}

private fun State<Any>.isLoaded(font: SkikoFont): Boolean =
    (value as? FontLoadResult)?.aliases?.contains(font.cacheKey) == true

private class AsyncTestFont(
    override val identity: String,
    typefaceLoader: SkikoFont.TypefaceLoader,
) : SkikoFont(FontLoadingStrategy.Async, typefaceLoader, FontVariation.Settings()) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}

private class GateTypefaceLoader(
    private val typeface: CompletableDeferred<SkTypeface>
) : SkikoFont.TypefaceLoader {
    override fun loadBlocking(font: SkikoFont): SkTypeface? = error("should not be called")

    override suspend fun awaitLoad(font: SkikoFont): SkTypeface = typeface.await()
}

private class CountingTypefaceLoader(
    private val load: () -> SkTypeface?
) : SkikoFont.TypefaceLoader {
    var loadCount = 0
        private set

    override fun loadBlocking(font: SkikoFont): SkTypeface? = error("should not be called")

    override suspend fun awaitLoad(font: SkikoFont): SkTypeface? {
        loadCount++
        return load()
    }
}
