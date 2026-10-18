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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.platform.ParagraphLayouter
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.util.fastForEachIndexed
import org.jetbrains.skia.paragraph.LineMetrics

/**
 * Draws the [Bullet] markers of a paragraph into its leading margin. Skia's paragraph has no
 * leading margin span, so they are painted right after the text.
 */
internal class SkikoBulletPainter(private val layouter: ParagraphLayouter) {
    private var _paint: Paint? = null
    private val paint: Paint
        get() = _paint ?: Paint().also { _paint = it }

    /** Built on first use: nothing an outline depends on changes while this painter is alive. */
    private var _outlines: Array<Outline?>? = null
    private val outlines: Array<Outline?>
        get() = _outlines ?: arrayOfNulls<Outline>(layouter.bullets.size).also { _outlines = it }

    /**
     * @param lineMetricsForOffset metrics of the line the given text offset falls into
     * @param color color the text is drawn with, used for a bullet that defines neither a brush of
     *   its own nor [brush]
     * @param brush brush the text is drawn with, if any
     * @param alpha opacity the text is drawn with, used for a bullet whose own alpha is [Float.NaN]
     */
    fun paint(
        canvas: Canvas,
        textDirection: ResolvedTextDirection,
        lineMetricsForOffset: (Int) -> LineMetrics?,
        color: Color,
        brush: Brush?,
        alpha: Float,
    ) {
        val density = layouter.density
        val contextFontSize = layouter.defaultFont.size
        val textColor = color.takeOrElse { layouter.textStyle.color }
        val layoutDirection = when (textDirection) {
            ResolvedTextDirection.Rtl -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }

        layouter.bullets.fastForEachIndexed { index, range ->
            val bullet = range.item
            val widthPx = bullet.width.resolveBulletSizeToPx(density, contextFontSize)
            val heightPx = bullet.height.resolveBulletSizeToPx(density, contextFontSize)
            val gapPx = bullet.padding.resolveBulletSizeToPx(density, contextFontSize)
            if (widthPx.isNaN() || heightPx.isNaN() || gapPx.isNaN()) return@fastForEachIndexed

            val line = lineMetricsForOffset(range.start) ?: return@fastForEachIndexed
            val lineTop = (line.baseline - line.ascent).toFloat()
            val lineBottom = (line.baseline + line.descent).toFloat()
            val yCenter = (lineTop + lineBottom) / 2f
            val xStart = if (layoutDirection == LayoutDirection.Rtl) {
                line.right.toFloat() + gapPx
            } else {
                (line.left.toFloat() - (widthPx + gapPx)).coerceAtLeast(0f)
            }

            val size = Size(widthPx, heightPx)
            preparePaint(bullet, size, textColor, brush, alpha)

            val outlines = outlines
            val outline = outlines[index]
                ?: bullet.shape.createOutline(size, layoutDirection, density).also {
                    outlines[index] = it
                }

            canvas.save()
            canvas.translate(xStart, yCenter - heightPx / 2f)
            canvas.drawOutline(outline, paint)
            canvas.restore()
        }
    }

    /**
     * Sets [paint] up to draw [bullet] of the given [size], clearing what the previous bullet left
     * behind.
     */
    private fun preparePaint(
        bullet: Bullet,
        size: Size,
        textColor: Color,
        textBrush: Brush?,
        textAlpha: Float,
    ) {
        val drawStyle = bullet.drawStyle
        if (drawStyle is Stroke) {
            paint.style = PaintingStyle.Stroke
            paint.strokeWidth = drawStyle.width
            paint.strokeMiterLimit = drawStyle.miter
            paint.strokeCap = drawStyle.cap
            paint.strokeJoin = drawStyle.join
            paint.pathEffect = drawStyle.pathEffect
        } else {
            paint.style = PaintingStyle.Fill
            if (paint.pathEffect != null) paint.pathEffect = null
        }

        val alpha = if (bullet.alpha.isNaN()) textAlpha else bullet.alpha
        val brush = bullet.brush ?: textBrush
        if (brush != null) {
            brush.applyTo(size, paint, alpha)
        } else {
            if (paint.shader != null) paint.shader = null
            paint.color = textColor
            paint.alpha = alpha
        }
    }
}

/**
 * Resolves a [Bullet]'s size or padding to pixels, mirroring the Android implementation: an
 * unspecified value falls back to [contextFontSize], and an unknown [TextUnit] type yields
 * [Float.NaN] so that the caller can skip the bullet instead of failing.
 */
internal fun TextUnit.resolveBulletSizeToPx(density: Density, contextFontSize: Float): Float =
    when {
        this == TextUnit.Unspecified -> contextFontSize
        isSp -> with(density) { toPx() }
        isEm -> contextFontSize * value
        else -> Float.NaN
    }
