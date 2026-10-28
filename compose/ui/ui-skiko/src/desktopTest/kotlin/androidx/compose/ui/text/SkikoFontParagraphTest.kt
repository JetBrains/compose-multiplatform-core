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

package androidx.compose.ui.text

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SkikoFont
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface
import org.junit.Test

/** Builds paragraphs with [SkikoFont]s through a real [FontFamily.Resolver]. */
class SkikoFontParagraphTest : SkikoComposeTestBase() {

    private val fontBytes =
        javaClass.classLoader.getResourceAsStream("font_desktop/sample_font.ttf")!!.readBytes()

    private fun loadTypeface(): Typeface =
        FontMgr.default.makeFromData(Data.makeFromBytes(fontBytes))!!

    @Test
    fun asyncFont_newParagraphWithEqualStyleUsesLoadedTypeface() = runTest {
        val resolver = createResolver()
        val loaded = CompletableDeferred<Unit>()
        val asyncFamily = FontFamily(
            TestFont("async-equal-style", FontLoadingStrategy.Async, GatedLoader(loaded))
        )
        val blockingFamily = FontFamily(
            TestFont("async-equal-style-expected", FontLoadingStrategy.Blocking, GatedLoader(loaded))
        )
        val expectedWidth = width(blockingFamily, resolver)

        val fallbackWidth = width(asyncFamily, resolver)
        assertNotEquals(expectedWidth, fallbackWidth)

        loaded.complete(Unit)
        advanceUntilIdle()

        assertEquals(expectedWidth, width(asyncFamily, resolver))
    }

    private fun width(family: FontFamily, resolver: FontFamily.Resolver): Float =
        Paragraph(
            text = Text,
            style = TextStyle(fontFamily = family, fontSize = FontSize),
            constraints = Constraints(),
            density = Density(1f),
            fontFamilyResolver = resolver,
        ).maxIntrinsicWidth

    /** Runs async font loads on the test dispatcher instead of the main dispatcher. */
    private fun TestScope.createResolver(): FontFamily.Resolver =
        createFontFamilyResolver(coroutineContext[ContinuationInterceptor]!!)

    private inner class GatedLoader(private val loaded: CompletableDeferred<Unit>) :
        SkikoFont.TypefaceLoader {
        override fun loadBlocking(font: SkikoFont): Typeface = loadTypeface()

        override suspend fun awaitLoad(font: SkikoFont): Typeface {
            loaded.await()
            return loadTypeface()
        }
    }

    private class TestFont(
        override val identity: String,
        loadingStrategy: FontLoadingStrategy,
        typefaceLoader: TypefaceLoader,
    ) : SkikoFont(loadingStrategy, typefaceLoader, FontVariation.Settings()) {
        override val weight: FontWeight = FontWeight.Normal
        override val style: FontStyle = FontStyle.Normal
    }

    private companion object {
        const val Text = "AAAA"
        val FontSize = 10.sp
    }
}
