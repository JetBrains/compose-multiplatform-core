/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Matrix33

// TODO: Do not expose skiko types to common
//  https://youtrack.jetbrains.com/issue/CMP-219
actual typealias Shader = org.jetbrains.skia.Shader

internal actual class TransformShader {
    private var _shader: Shader? = null
    private var _wrapper: Shader? = null
    private var _matrix: Matrix33? = null

    actual fun transform(matrix: Matrix?) {
        _matrix = if (matrix != null) {
            Matrix33.makeTranslate(0f, 0f).apply { setFrom(matrix) }
        } else null
        _wrapper = null
    }

    actual var shader: Shader?
        get() {
            val matrix = _matrix ?: return _shader
            if (_wrapper == null) {
                _wrapper = _shader?.makeWithLocalMatrix(matrix)
            }
            return _wrapper
        }
        set(value) {
            _shader = value
            _wrapper = null
        }
}

internal actual fun ActualLinearGradientShader(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader {
    validateColorStops(colors, colorStops)
    return Shader.makeLinearGradient(
        from.x, from.y, to.x, to.y, colors.toIntArray(), colorStops?.toFloatArray(),
        GradientStyle(tileMode.toSkiaTileMode(), true, identityMatrix33())
    )
}

internal actual fun ActualRadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader {
    validateColorStops(colors, colorStops)
    return Shader.makeRadialGradient(
        center.x,
        center.y,
        radius,
        colors.toIntArray(),
        colorStops?.toFloatArray(),
        GradientStyle(tileMode.toSkiaTileMode(), true, identityMatrix33())
    )
}

internal actual fun ActualSweepGradientShader(
    center: Offset,
    colors: List<Color>,
    colorStops: List<Float>?
): Shader {
    validateColorStops(colors, colorStops)
    return Shader.makeSweepGradient(
        center.x,
        center.y,
        colors.toIntArray(),
        colorStops?.toFloatArray()
    )
}

internal actual fun ActualImageShader(
    image: ImageBitmap,
    tileModeX: TileMode,
    tileModeY: TileMode
): Shader {
    return image.asSkiaBitmap().makeShader(
        tileModeX.toSkiaTileMode(),
        tileModeY.toSkiaTileMode()
    )
}

internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
    org.jetbrains.skia.Shader.makeBlend(mode = blendMode.toSkia(), dst = dst, src = src)

private fun List<Color>.toIntArray(): IntArray =
    IntArray(size) { i -> this[i].toArgb() }

private fun validateColorStops(colors: List<Color>, colorStops: List<Float>?) {
    if (colorStops == null) {
        if (colors.size < 2) {
            throw IllegalArgumentException(
                "colors must have length of at least 2 if colorStops " +
                    "is omitted."
            )
        }
    } else if (colors.size != colorStops.size) {
        throw IllegalArgumentException(
            "colors and colorStops arguments must have" +
                " equal length."
        )
    }
}