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
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.platform.PlatformBlurFilter
import androidx.compose.ui.graphics.platform.PlatformColorFilter
import androidx.compose.ui.graphics.platform.PlatformGraphics
import androidx.compose.ui.graphics.platform.PlatformRenderEffect
import androidx.compose.ui.graphics.shadow.SkikoBlurFilter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorFilter as SkColorFilter
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorMatrix as SkColorMatrix
import org.jetbrains.skia.ColorSpace as SkColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.PathEffect as SkPathEffect
import org.jetbrains.skia.Shader as SkShader

/**
 * Skiko-side graphics implementation entry point, registered via
 * [androidx.compose.ui.platform.registerSkikoComposeImplementation].
 */
@OptIn(InternalComposeUiApi::class)
internal object SkikoGraphics : PlatformGraphics {
    override fun createCanvas(image: ImageBitmap): Canvas {
        val skiaBitmap = image.asSkiaBitmap()
        require(!skiaBitmap.isImmutable) {
            "Cannot draw on immutable ImageBitmap"
        }
        return SkCanvas(skiaBitmap).asComposeCanvas()
    }

    override fun createPaint(): Paint = SkPaint().asComposePaint()

    // Skia supports all Compose blend and tile modes.
    override fun isBlendModeSupported(blendMode: BlendMode): Boolean = true

    override fun isTileModeSupported(tileMode: TileMode): Boolean = true

    override fun createPath(): Path = SkiaBackedPath()

    override fun createPathIterator(
        path: Path,
        conicEvaluation: PathIterator.ConicEvaluation,
        tolerance: Float,
    ): PathIterator = SkiaPathIterator(path, conicEvaluation, tolerance)

    override fun createPathMeasure(): PathMeasure =
        SkiaBackedPathMeasure()

    override fun createImageBitmap(
        width: Int,
        height: Int,
        config: ImageBitmapConfig,
        hasAlpha: Boolean,
        colorSpace: ColorSpace,
    ): ImageBitmap {
        require(width > 0 && height > 0) { "width and height must be > 0" }
        val colorInfo = ColorInfo(
            config.toSkiaColorType(),
            if (hasAlpha) ColorAlphaType.PREMUL else ColorAlphaType.OPAQUE,
            colorSpace.toSkiaColorSpace(),
        )
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(colorInfo, width, height))
        return bitmap.asComposeImageBitmap()
    }

    override fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
        Image.makeFromEncoded(bytes).toComposeImageBitmap()

    override fun createLinearGradientShader(
        from: Offset,
        to: Offset,
        colors: List<Color>,
        colorStops: List<Float>?,
        tileMode: TileMode,
    ): androidx.compose.ui.graphics.Shader {
        validateColorStops(colors, colorStops)
        return SkShader.makeLinearGradient(
            from.x,
            from.y,
            to.x,
            to.y,
            colors.toSkiaGradient(
                colorStops = colorStops,
                tileMode = tileMode,
            ),
        ).asComposeShader()
    }

    override fun createRadialGradientShader(
        center: Offset,
        radius: Float,
        colors: List<Color>,
        colorStops: List<Float>?,
        tileMode: TileMode,
    ): androidx.compose.ui.graphics.Shader {
        validateColorStops(colors, colorStops)
        return SkShader.makeRadialGradient(
            center.x,
            center.y,
            radius,
            colors.toSkiaGradient(
                colorStops = colorStops,
                tileMode = tileMode,
            ),
        ).asComposeShader()
    }

    override fun createSweepGradientShader(
        center: Offset,
        colors: List<Color>,
        colorStops: List<Float>?,
    ): androidx.compose.ui.graphics.Shader {
        validateColorStops(colors, colorStops)
        return SkShader.makeSweepGradient(
            center.x,
            center.y,
            colors.toSkiaGradient(colorStops = colorStops),
        ).asComposeShader()
    }

    override fun createImageShader(
        image: ImageBitmap,
        tileModeX: TileMode,
        tileModeY: TileMode,
    ): androidx.compose.ui.graphics.Shader =
        image.asSkiaBitmap()
            .makeShader(tileModeX.toSkiaTileMode(), tileModeY.toSkiaTileMode())
            .asComposeShader()

    override fun createCompositeShader(
        destination: androidx.compose.ui.graphics.Shader,
        source: androidx.compose.ui.graphics.Shader,
        blendMode: BlendMode,
    ): androidx.compose.ui.graphics.Shader =
        SkShader.makeBlend(blendMode.toSkia(), destination.skiaShader, source.skiaShader)
            .asComposeShader()

    override fun transformShader(shader: Shader, matrix: Matrix): Shader =
        transformSkikoShader(shader, matrix)

    override fun createBlurRenderEffect(
        renderEffect: RenderEffect?,
        radiusX: Float,
        radiusY: Float,
        edgeTreatment: TileMode,
    ): PlatformRenderEffect =
        SkikoRenderEffect(
            if (renderEffect == null) {
                ImageFilter.makeBlur(
                    BlurEffect.convertRadiusToSigma(radiusX),
                    BlurEffect.convertRadiusToSigma(radiusY),
                    edgeTreatment.toSkiaTileMode(),
                )
            } else {
                ImageFilter.makeBlur(
                    BlurEffect.convertRadiusToSigma(radiusX),
                    BlurEffect.convertRadiusToSigma(radiusY),
                    edgeTreatment.toSkiaTileMode(),
                    renderEffect.skiaImageFilter,
                    null,
                )
            }
        )

    override fun createOffsetRenderEffect(
        renderEffect: RenderEffect?,
        offset: Offset,
    ): PlatformRenderEffect =
        SkikoRenderEffect(
            ImageFilter.makeOffset(offset.x, offset.y, renderEffect?.skiaImageFilter, null)
        )

    override fun createBlurFilter(radius: Float): PlatformBlurFilter =
        SkikoBlurFilter(
            MaskFilter.makeBlur(FilterBlurMode.NORMAL, BlurEffect.convertRadiusToSigma(radius))
        )

    override fun setBlurFilter(paint: Paint, blur: PlatformBlurFilter?) {
        require(blur == null || blur is SkikoBlurFilter) {
            "Expected SkikoBlurFilter, got ${blur?.let { it::class }}"
        }
        paint.skiaPaint.maskFilter = blur?.maskFilter
    }

    override fun createTintColorFilter(color: Color, blendMode: BlendMode): PlatformColorFilter =
        SkikoColorFilter(SkColorFilter.makeBlend(color.toArgb(), blendMode.toSkia()))

    override fun createColorMatrixColorFilter(colorMatrix: ColorMatrix): PlatformColorFilter {
        // Skia applies the color-matrix translation column unscaled (0..1) while Compose uses
        // 0..255, so rescale those entries before handing the matrix to skia.
        val remappedValues = colorMatrix.values.copyOf()
        remappedValues[4] *= (1f / 255f)
        remappedValues[9] *= (1f / 255f)
        remappedValues[14] *= (1f / 255f)
        remappedValues[19] *= (1f / 255f)
        return SkikoColorFilter(SkColorFilter.makeMatrix(SkColorMatrix(remappedValues)))
    }

    override fun createLightingColorFilter(multiply: Color, add: Color): PlatformColorFilter =
        SkikoColorFilter(SkColorFilter.makeLighting(multiply.toArgb(), add.toArgb()))

    // TODO: https://youtrack.jetbrains.com/issue/CMP-739
    override fun colorMatrixFromFilter(filter: PlatformColorFilter): ColorMatrix = ColorMatrix()

    override fun createCornerPathEffect(radius: Float): PathEffect =
        SkPathEffect.makeCorner(radius).asComposePathEffect()

    override fun createDashPathEffect(
        intervals: FloatArray,
        phase: Float,
    ): PathEffect =
        SkPathEffect.makeDash(intervals, phase).asComposePathEffect()

    override fun createChainPathEffect(
        outer: PathEffect,
        inner: PathEffect,
    ): PathEffect =
        outer.asSkiaPathEffect().makeCompose(inner.asSkiaPathEffect()).asComposePathEffect()

    override fun createStampedPathEffect(
        shape: Path,
        advance: Float,
        phase: Float,
        style: StampedPathEffectStyle,
    ): PathEffect =
        SkPathEffect.makePath1D(
            shape.materializeSkiaPath(),
            advance,
            phase,
            style.toSkiaStampedPathEffectStyle(),
        ).asComposePathEffect()
}

private fun ImageBitmapConfig.toSkiaColorType() = when (this) {
    ImageBitmapConfig.Argb8888 -> ColorType.N32
    ImageBitmapConfig.Alpha8 -> ColorType.ALPHA_8
    ImageBitmapConfig.Rgb565 -> ColorType.RGB_565
    ImageBitmapConfig.F16 -> ColorType.RGBA_F16
    else -> throw IllegalArgumentException("Unsupported ImageBitmapConfig: $this")
}

private fun ColorSpace.toSkiaColorSpace(): SkColorSpace = when (this) {
    ColorSpaces.Srgb -> SkColorSpace.sRGB
    ColorSpaces.LinearSrgb -> SkColorSpace.sRGBLinear
    ColorSpaces.DisplayP3 -> SkColorSpace.displayP3
    else -> SkColorSpace.sRGB
}

private fun List<Color>.toSkiaGradient(
    colorStops: List<Float>?,
    tileMode: TileMode = TileMode.Clamp,
): Gradient = Gradient(
    colors = Gradient.Colors(
        colors = toColor4fArray(),
        positions = colorStops?.toFloatArray(),
        tileMode = tileMode.toSkiaTileMode(),
    ),
    interpolation = Gradient.Interpolation(
        inPremul = Gradient.Interpolation.InPremul.YES,
    ),
)

private fun List<Color>.toColor4fArray(): Array<Color4f> =
    Array(size) { i ->
        val color = this[i]
        Color4f(color.red, color.green, color.blue, color.alpha)
    }

private fun validateColorStops(colors: List<Color>, colorStops: List<Float>?) {
    if (colorStops == null) {
        require(colors.size >= 2) {
            "colors must have length of at least 2 if colorStops is omitted."
        }
    } else {
        require(colors.size == colorStops.size) {
            "colors and colorStops arguments must have equal length."
        }
    }
}
