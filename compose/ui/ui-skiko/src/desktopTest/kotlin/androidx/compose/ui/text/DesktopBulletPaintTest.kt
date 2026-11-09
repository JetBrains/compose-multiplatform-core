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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DesktopBulletPaintTest : SkikoComposeTestBase() {

    private val fontFamilyResolver = createFontFamilyResolver()
    private val defaultDensity = Density(density = 1f)
    private val canvasWidth = 200
    private val canvasHeight = 60

    private val fontSize = 20.sp
    private val bulletSizePx = 10
    private val paddingPx = 4
    private val bullet = Bullet(
        shape = RectangleShape,
        width = bulletSizePx.sp,
        height = bulletSizePx.sp,
        padding = paddingPx.sp,
        brush = SolidColor(Color.Red)
    )

    /** The paragraph is indented by one em, see [paragraph]. */
    private val indentPx = with(defaultDensity) { fontSize.toPx() }

    @Test
    fun bullet_isPaintedIntoTheLeadingMargin() {
        val bounds = checkNotNull(
            redBounds(paint(bullets = listOf(Range(bullet, 0, TEXT.length))))
        ) { "no bullet was painted" }

        // The text starts after the indentation ...
        assertThat(paragraph(bullets = emptyList()).getLineLeft(0)).isEqualTo(indentPx)

        // ... and the bullet is drawn at its requested size, right aligned in the margin the
        // indentation leaves, one padding away from the text.
        assertThat(bounds.left).isEqualTo(indentPx.toInt() - paddingPx - bulletSizePx)
        assertThat(bounds.width).isEqualTo(bulletSizePx)

        // Vertically the bullet is centered on the line, so it may bleed into an extra blended row.
        assertThat(bounds.height).isIn(bulletSizePx..(bulletSizePx + 1))
    }

    @Test
    fun withoutBulletAnnotation_nothingIsPaintedIntoTheLeadingMargin() {
        assertThat(redBounds(paint(bullets = emptyList()))).isNull()
    }

    private fun paragraph(bullets: List<Range<Bullet>>): Paragraph {
        val intrinsics = ParagraphIntrinsics(
            text = TEXT,
            style = TextStyle(
                fontSize = fontSize,
                color = Color.Black,
                textIndent = TextIndent(firstLine = 1.em, restLine = 1.em)
            ),
            annotations = bullets,
            density = defaultDensity,
            fontFamilyResolver = fontFamilyResolver
        )
        return Paragraph(
            paragraphIntrinsics = intrinsics,
            constraints = Constraints(maxWidth = canvasWidth),
            overflow = TextOverflow.Clip
        )
    }

    private fun paint(bullets: List<Range<Bullet>>): ImageBitmap {
        val image = ImageBitmap(canvasWidth, canvasHeight)
        paragraph(bullets).paint(Canvas(image))
        return image
    }

    private fun redBounds(image: ImageBitmap): PixelBounds? {
        val pixels = image.toPixelMap()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = pixels[x, y]
                // Blending against the transparent canvas keeps the hue but not the exact color.
                if (color.red > 0.5f && color.green < 0.5f && color.blue < 0.5f) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < left) null else PixelBounds(left, top, right, bottom)
    }

    private data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
    }

    private companion object {
        const val TEXT = "Item"
    }
}
