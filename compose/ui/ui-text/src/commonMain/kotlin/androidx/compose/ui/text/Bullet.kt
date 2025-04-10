/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * A bullet annotation applied to the AnnotatedString that draws a bullet.
 *
 * Note that it's up to the caller of this API to make sure there's enough space between the edge of
 * the text composable and the start of the paragraph inside that composable to fit the bullet with
 * its padding.
 *
 * @param shape a shape of the bullet to draw
 * @param size a size of the bullet
 * @param padding a padding between the end of the bullet and the start of the paragraph
 * @param brush a brush to draw a bullet with
 * @param alpha an alpha to apply when drawing a bullet
 * @param drawStyle defines the draw style of the bullet, e.g. a fill or an outline
 */
internal class Bullet(
    val shape: Shape,
    val size: TextUnit, // Make TextUnitSize or something similar when making public
    val padding: TextUnit,
    val brush: Brush?,
    val alpha: Float,
    val drawStyle: DrawStyle
) : AnnotatedString.Annotation {
    /** Copies the existing [Bullet] replacing some of the fields as desired. */
    fun copy(
        shape: Shape = this.shape,
        size: TextUnit = this.size,
        padding: TextUnit = this.padding,
        brush: Brush? = this.brush,
        alpha: Float = this.alpha,
        drawStyle: DrawStyle = this.drawStyle,
    ) = Bullet(shape, size, padding, brush, alpha, drawStyle)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Bullet) return false

        if (shape != other.shape) return false
        if (size != other.size) return false
        if (padding != other.padding) return false
        if (brush != other.brush) return false
        if (alpha != other.alpha) return false
        if (drawStyle != other.drawStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + brush.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + drawStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "Bullet(shape=$shape, size=$size, padding=$padding, brush=$brush, alpha=$alpha, drawStyle=$drawStyle)"
    }
}

/** Default indentation between the start edge of the Text composable and start of paragraph */
internal val DefaultBulletIndentation = 1.em
/** Default size of the bullet */
private val DefaultBulletSize = 0.25.em
/** Default padding between a bullet and start of a paragraph */
private val DefaultBulletPadding = 0.25.em
/** Default bullet used in AnnotatedString's bullet list */
internal val DefaultBullet =
    Bullet(CircleShape, DefaultBulletSize, DefaultBulletPadding, null, 1f, Fill)

private object CircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cornerRadius = CornerRadius(size.minDimension / 2f)
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = cornerRadius,
                topRight = cornerRadius,
                bottomRight = cornerRadius,
                bottomLeft = cornerRadius
            )
        )
    }
}
