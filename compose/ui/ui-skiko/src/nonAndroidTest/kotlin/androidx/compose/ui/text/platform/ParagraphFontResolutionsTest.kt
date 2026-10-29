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

package androidx.compose.ui.text.platform

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.SkikoComposeTestBase
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.PlatformTypefacesLoader
import androidx.compose.ui.text.font.SkiaFontLoader
import androidx.compose.ui.text.font.createPlatformFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

@OptIn(InternalComposeUiApi::class)
class ParagraphFontResolutionsTest : SkikoComposeTestBase() {
    @Test
    fun repeatedFontResolutionDoesNotAccumulateReads() = withFonts { fonts ->
        val intrinsics = fonts.intrinsics()
        val initialReadCount = countFontReads(intrinsics)
        assertTrue(initialReadCount > 1, "The async font state must be observed")

        repeat(100) {
            Paragraph(intrinsics, Constraints(maxWidth = 100 + it))
        }

        assertEquals(initialReadCount, countFontReads(intrinsics))
    }

    @Test
    fun resolvingUpdatedFontPreservesStaleState() = withFonts { fonts ->
        val intrinsics = fonts.intrinsics()
        assertFalse(intrinsics.hasStaleResolvedFonts)

        fonts.loadedFont.complete(FontLoadResult(null, emptyList()))
        assertTrue(intrinsics.hasStaleResolvedFonts)

        Paragraph(intrinsics, Constraints(maxWidth = 100))
        Paragraph(intrinsics, Constraints(maxWidth = 200))
        assertTrue(intrinsics.hasStaleResolvedFonts)
    }

    @Test
    fun asyncFontUpdateIsAppliedOnlyToNewIntrinsics() = withFonts { fonts ->
        fun characterWidth(intrinsics: ParagraphIntrinsics) =
            Paragraph(intrinsics, Constraints(maxWidth = 500)).getBoundingBox(0).width

        val original = fonts.intrinsics()
        val fallbackWidth = characterWidth(original)
        val fallbackIntrinsicWidth = original.maxIntrinsicWidth
        assertFalse(original.hasStaleResolvedFonts)

        val reference = fonts.intrinsics(fontFamily = FontFamily.Monospace)
        val loadedWidth = characterWidth(reference)
        fonts.loadedFont.complete(fonts.loadPlatformFont(FontFamily.Monospace))
        assertTrue(original.hasStaleResolvedFonts)

        val updated = fonts.intrinsics()
        assertFalse(updated.hasStaleResolvedFonts)
        assertEquals(reference.maxIntrinsicWidth, updated.maxIntrinsicWidth)
        assertEquals(loadedWidth, characterWidth(updated))

        // Equal computed styles must keep using the font selected by their own intrinsics,
        // even when old and new paragraphs alternate accesses to the shared Skia style cache.
        repeat(2) {
            assertEquals(fallbackIntrinsicWidth, original.maxIntrinsicWidth)
            assertEquals(fallbackWidth, characterWidth(original))
            assertTrue(original.hasStaleResolvedFonts)
            assertEquals(loadedWidth, characterWidth(updated))
            assertFalse(updated.hasStaleResolvedFonts)
        }
    }

    @Test
    fun differentFontRequestsAreResolvedSeparately() = withFonts { fonts ->
        val singleFontReadCount = countFontReads(fonts.intrinsics())
        val intrinsics = fonts.intrinsics(
            listOf(
                AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, 1),
                AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), 1, 2),
                AnnotatedString.Range(SpanStyle(fontSynthesis = FontSynthesis.All), 2, 3),
            )
        )
        val multipleFontsReadCount = countFontReads(intrinsics)
        assertTrue(multipleFontsReadCount > singleFontReadCount)

        repeat(10) {
            Paragraph(intrinsics, Constraints(maxWidth = 100 + it))
        }
        assertEquals(multipleFontsReadCount, countFontReads(intrinsics))
    }

    private fun countFontReads(intrinsics: ParagraphIntrinsics): Int {
        var reads = 0
        val stale = Snapshot.observe(readObserver = { reads++ }) {
            intrinsics.hasStaleResolvedFonts
        }
        assertFalse(stale)
        return reads
    }

    private fun withFonts(test: (TestFonts) -> Unit) {
        val fonts = TestFonts()
        try {
            test(fonts)
        } finally {
            fonts.loaderJob.cancel()
        }
    }

    private class TestFonts {
        val loadedFont = CompletableDeferred<Any>()
        val loaderJob = Job()
        private val backend = object : PlatformTypefacesLoader by SkiaFontLoader() {
            override val cacheKey = Any()

            override suspend fun awaitLoad(font: Font): Any = loadedFont.await()
        }
        private val resolver = createPlatformFontFamilyResolver(
            backend,
            Dispatchers.Unconfined + loaderJob,
        )
        private val family = FontFamily(object : Font {
            override val weight = FontWeight.Normal
            override val style = FontStyle.Normal
            override val loadingStrategy = FontLoadingStrategy.Async
        })

        fun loadPlatformFont(fontFamily: FontFamily): FontLoadResult =
            backend.loadPlatformTypes(fontFamily, FontWeight.Normal, FontStyle.Normal) as FontLoadResult

        fun intrinsics(
            annotations: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
            fontFamily: FontFamily = family,
        ) =
            ParagraphIntrinsics(
                text = "Text",
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = 16.sp,
                    fontSynthesis = FontSynthesis.None,
                ),
                annotations = annotations,
                density = Density(1f),
                fontFamilyResolver = resolver,
            )
    }
}
