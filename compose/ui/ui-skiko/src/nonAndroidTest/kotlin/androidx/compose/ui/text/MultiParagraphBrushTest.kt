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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Surface

class MultiParagraphBrushTest : SkikoComposeTestBase() {
    private val textMeasurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultLocaleList = LocaleList.current,
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
        cacheSize = 0
    )

    private fun multiParagraph(text: AnnotatedString) = textMeasurer.measure(
        text = text,
        style = TextStyle(fontSize = 20.sp),
        constraints = Constraints(maxWidth = 200)
    ).multiParagraph

    /**
     * Paints this [MultiParagraph] with [brush] and returns the image.
     *
     * Also checks that some text has actually been painted, so that the tests can't pass by
     * comparing empty images (e.g. on a platform where no font is available).
     */
    private fun MultiParagraph.paintedImage(brush: Brush): ImageBitmap {
        val width = ceil(width).toInt()
        val height = ceil(height).toInt()
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(0)
            paint(surface.canvas.asComposeCanvas(), brush)
            val image = surface.makeImageSnapshot().toComposeImageBitmap()
            assertTrue(image.pixels().any { it != 0 }, "No text was painted")
            return image
        } finally {
            surface.close()
        }
    }

    private fun ImageBitmap.pixels() = toPixelMap().buffer

    private fun assertSameImage(expected: ImageBitmap, actual: ImageBitmap) {
        assertContentEquals(expected.pixels(), actual.pixels())
    }

    private val lines = listOf("Lorem", "Ipsum", "Dolor", "Magna")

    @Test
    fun brushIsAppliedToTheWholeText() {
        val multiParagraph = multiParagraph(
            buildAnnotatedString {
                for (line in lines) {
                    withStyle(ParagraphStyle()) { append(line) }
                }
            }
        )
        val singleParagraph = multiParagraph(AnnotatedString(lines.joinToString("\n")))
        val brush = Brush.verticalGradient(listOf(Color.Blue, Color.Red))

        val expected = singleParagraph.paintedImage(brush)
        val actual = multiParagraph.paintedImage(brush)
        assertSameImage(expected, actual)
    }

    @Test
    fun brushWithReusedShaderPaintsTheSameEachTime() {
        val multiParagraph = multiParagraph(
            buildAnnotatedString {
                for (line in lines) {
                    withStyle(ParagraphStyle()) { append(line) }
                }
            }
        )
        // A brush that always returns the same shader instance
        val brush = ShaderBrush(
            LinearGradientShader(
                from = Offset.Zero,
                to = Offset(0f, multiParagraph.height),
                colors = listOf(Color.Blue, Color.Red)
            )
        )

        val first = multiParagraph.paintedImage(brush)
        val second = multiParagraph.paintedImage(brush)
        assertSameImage(first, second)
    }

    @Test
    fun drawingAgainWithTheSameBrushDoesNotRecreateTheShader() {
        val multiParagraph = multiParagraph(
            buildAnnotatedString {
                for (line in lines) {
                    withStyle(ParagraphStyle()) { append(line) }
                }
            }
        )
        var shaderCount = 0
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                shaderCount++
                return LinearGradientShader(
                    from = Offset.Zero,
                    to = Offset(0f, size.height),
                    colors = listOf(Color.Blue, Color.Red)
                )
            }
        }

        multiParagraph.paintedImage(brush)
        val shaderCountAfterFirstDraw = shaderCount
        multiParagraph.paintedImage(brush)

        // A new shader would mean that the paragraphs considered the brush changed, and had to
        // update their paint and lay out again
        assertEquals(shaderCountAfterFirstDraw, shaderCount)
    }
}
