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

package androidx.compose.ui.graphics.shadow

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Group of parameters that represent how a drop shadow should be rendered.
 *
 * @param radius The blur radius of the shadow
 * @param spread Spread parameter that adds to the size of the shadow
 * @param color The color of the shadow, only consumed if the Brush is not provided
 * @param brush The brush to use for the shadow. If null, the color parameter is consumed instead
 * @param alpha Opacity of the shadow
 * @param blendMode Blending algorithm used by the shadow
 */
@Stable
class DropShadow
private constructor(
    val radius: Dp,
    val spread: Dp,
    color: Color,
    brush: Brush?,
    alpha: Float,
    val blendMode: BlendMode,
) {

    /**
     * Color of the shadow. If Color.Unspecified is provided, Color.Black will be used as a default
     * This color is only used if [brush] is null
     */
    val color: Color

    /** Optional brush to render the shadow with. */
    val brush: Brush?

    /** Opacity of the shadow */
    val alpha: Float

    init {
        // If the brush we are given can be represented by a Color, just consume that directly
        // in order to leverage more efficient tinting through a ColorFilter
        // Otherwise consume the Brush directly so that it blended against the shadow geometry
        // specified by a BitmapShader
        if (brush is SolidColor) {
            this.color = brush.value
            this.brush = null
        } else {
            this.color = color
            this.brush = brush
        }
        this.alpha = alpha.coerceIn(0f, 1f)
    }

    /**
     * Create a [DropShadow] parameter that is to be rendered with the corresponding [Brush]
     * parameter. This brush will be masked against the geometry of the shadow.
     */
    constructor(
        radius: Dp,
        brush: Brush,
        spread: Dp = 0.dp,
        alpha: Float = 1f,
        blendMode: BlendMode = DefaultBlendMode,
    ) : this(
        radius = radius,
        spread = spread,
        color = Color.Black,
        brush = brush,
        alpha = alpha,
        blendMode = blendMode,
    )

    /**
     * Create a [DropShadow] parameter that is to be rendered with the corresponding [Brush]
     * parameter. The shadow will be tinted with the provided color.
     */
    constructor(
        radius: Dp,
        color: Color = Color.Black,
        spread: Dp = 0.dp,
        alpha: Float = 1f,
        blendMode: BlendMode = DefaultBlendMode,
    ) : this(
        radius = radius,
        spread = spread,
        color = if (color.isSpecified) color else Color.Black,
        brush = null,
        alpha = alpha,
        blendMode = blendMode,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropShadow) return false

        if (radius != other.radius) return false
        if (spread != other.spread) return false
        if (alpha != other.alpha) return false
        if (blendMode != other.blendMode) return false
        if (color != other.color) return false
        if (brush != other.brush) return false

        return true
    }

    override fun hashCode(): Int {
        var result = radius.hashCode()
        result = 31 * result + spread.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + blendMode.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + (brush?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "DropShadow(radius=$radius, spread=$spread, alpha=$alpha, blendMode=$blendMode, " +
            "color=$color, brush=$brush)"
    }
}
