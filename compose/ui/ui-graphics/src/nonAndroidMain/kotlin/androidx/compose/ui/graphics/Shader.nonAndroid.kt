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

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.platform.PlatformGraphicsRegistry
import androidx.compose.ui.graphics.platform.PlatformShader

actual class Shader @InternalComposeUiApi constructor(
    @property:InternalComposeUiApi val platformShader: PlatformShader,
)

@OptIn(InternalComposeUiApi::class)
internal actual class TransformShader {
    private var _shader: Shader? = null
    private var _wrapper: Shader? = null
    private var _matrix: Matrix? = null

    actual fun transform(matrix: Matrix?) {
        _matrix = matrix
        _wrapper = null
    }

    actual var shader: Shader?
        get() {
            val matrix = _matrix ?: return _shader
            if (_wrapper == null) {
                _wrapper = _shader?.let { shader ->
                    PlatformGraphicsRegistry.requireCurrent()
                        .transformShader(shader, matrix)
                }
            }
            return _wrapper
        }
        set(value) {
            _shader = value
            _wrapper = null
        }
}

@OptIn(InternalComposeUiApi::class)
internal actual fun ActualLinearGradientShader(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader =
    PlatformGraphicsRegistry.requireCurrent().createLinearGradientShader(
        from = from,
        to = to,
        colors = colors,
        colorStops = colorStops,
        tileMode = tileMode,
    )

@OptIn(InternalComposeUiApi::class)
internal actual fun ActualRadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode
): Shader =
    PlatformGraphicsRegistry.requireCurrent().createRadialGradientShader(
        center = center,
        radius = radius,
        colors = colors,
        colorStops = colorStops,
        tileMode = tileMode,
    )

@OptIn(InternalComposeUiApi::class)
internal actual fun ActualSweepGradientShader(
    center: Offset,
    colors: List<Color>,
    colorStops: List<Float>?
): Shader =
    PlatformGraphicsRegistry.requireCurrent().createSweepGradientShader(
        center = center,
        colors = colors,
        colorStops = colorStops,
    )

@OptIn(InternalComposeUiApi::class)
internal actual fun ActualImageShader(
    image: ImageBitmap,
    tileModeX: TileMode,
    tileModeY: TileMode
): Shader =
    PlatformGraphicsRegistry.requireCurrent().createImageShader(
        image = image,
        tileModeX = tileModeX,
        tileModeY = tileModeY,
    )

@OptIn(InternalComposeUiApi::class)
internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
    PlatformGraphicsRegistry.requireCurrent().createCompositeShader(
        destination = dst,
        source = src,
        blendMode = blendMode,
    )
