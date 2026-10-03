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
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit

/**
 * Draws [Bullet] markers into the leading margin of a paragraph.
 *
 * Skia's paragraph has no notion of a leading margin span, so the marker is painted separately
 * right after the text. The [Paint] is reused between calls because painting happens on every
 * frame.
 *
 * Note that the right-to-left placement has not been verified visually.
 */
internal class SkikoBulletPainter {
    private val paint = Paint()

    /**
     * @param xStart x coordinate of the left edge of the bullet's bounds
     * @param yCenter y coordinate of the center of the bullet's bounds
     * @param textColor color the rest of the text is drawn with, used when the bullet defines
     *   neither a brush of its own nor [textBrush]
     * @param textBrush brush the rest of the text is drawn with, if any
     * @param textAlpha opacity the rest of the text is drawn with, used when the bullet's own alpha
     *   is [Float.NaN]
     */
    fun paint(
        canvas: Canvas,
        bullet: Bullet,
        widthPx: Float,
        heightPx: Float,
        xStart: Float,
        yCenter: Float,
        layoutDirection: LayoutDirection,
        density: Density,
        textColor: Color,
        textBrush: Brush?,
        textAlpha: Float,
    ) {
        val size = Size(widthPx, heightPx)

        paint.shader = null
        paint.pathEffect = null
        paint.alpha = 1f

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
        }

        val alpha = if (bullet.alpha.isNaN()) textAlpha else bullet.alpha
        val brush = bullet.brush ?: textBrush
        if (brush != null) {
            brush.applyTo(size, paint, alpha)
        } else {
            paint.color = textColor
            paint.alpha = alpha
        }

        val top = yCenter - heightPx / 2f
        when (val outline = bullet.shape.createOutline(size, layoutDirection, density)) {
            is Outline.Rectangle -> {
                val rect = outline.rect
                canvas.drawRect(xStart, top, xStart + rect.width, top + rect.height, paint)
            }
            is Outline.Rounded -> {
                val roundRect = outline.roundRect
                if (roundRect.isSimple) {
                    canvas.drawRoundRect(
                        xStart,
                        top,
                        xStart + roundRect.width,
                        top + roundRect.height,
                        roundRect.topLeftCornerRadius.x,
                        roundRect.topLeftCornerRadius.y,
                        paint,
                    )
                } else {
                    val path = Path().apply { addRoundRect(roundRect) }
                    canvas.save()
                    canvas.translate(xStart, top)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                }
            }
            is Outline.Generic -> {
                canvas.save()
                canvas.translate(xStart, top)
                canvas.drawPath(outline.path, paint)
                canvas.restore()
            }
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
