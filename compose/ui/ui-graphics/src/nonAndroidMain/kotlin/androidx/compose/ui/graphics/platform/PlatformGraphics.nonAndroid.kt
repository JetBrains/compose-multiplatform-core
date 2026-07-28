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

package androidx.compose.ui.graphics.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.colorspace.ColorSpace

/**
 * Factory surface a graphics backend implements to provide ui-graphics' platform primitives.
 *
 * The implementation is registered at runtime, so downstream libraries compile against ui-graphics
 * without depending on a specific backend.
 */
@InternalComposeUiApi
interface PlatformGraphics {
    /** Creates a backend [Canvas] that draws into [image]. */
    fun createCanvas(image: ImageBitmap): Canvas

    /** Creates an empty backend [Paint]. */
    fun createPaint(): Paint

    /** Returns whether the backend can render the given [blendMode]. */
    fun isBlendModeSupported(blendMode: BlendMode): Boolean

    /** Returns whether the backend can render the given [tileMode]. */
    fun isTileModeSupported(tileMode: TileMode): Boolean

    /** Creates an empty backend [Path]. */
    fun createPath(): Path

    /**
     * Creates a [PathIterator] over [path], approximating conics per [conicEvaluation] within
     * [tolerance].
     */
    fun createPathIterator(
        path: Path,
        conicEvaluation: PathIterator.ConicEvaluation,
        tolerance: Float,
    ): PathIterator

    /** Creates a backend [PathMeasure]. */
    fun createPathMeasure(): PathMeasure

    /** Creates a backend [ImageBitmap] of the given dimensions and configuration. */
    fun createImageBitmap(
        width: Int,
        height: Int,
        config: ImageBitmapConfig,
        hasAlpha: Boolean,
        colorSpace: ColorSpace,
    ): ImageBitmap

    /** Decodes an encoded image (e.g. PNG/JPEG) from [bytes] into an [ImageBitmap]. */
    fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

    /** Creates a linear-gradient [Shader] running from [from] to [to]. */
    fun createLinearGradientShader(
        from: Offset,
        to: Offset,
        colors: List<Color>,
        colorStops: List<Float>?,
        tileMode: TileMode,
    ): Shader

    /** Creates a radial-gradient [Shader] centered at [center] with the given [radius]. */
    fun createRadialGradientShader(
        center: Offset,
        radius: Float,
        colors: List<Color>,
        colorStops: List<Float>?,
        tileMode: TileMode,
    ): Shader

    /** Creates a sweep-gradient [Shader] revolving around [center]. */
    fun createSweepGradientShader(
        center: Offset,
        colors: List<Color>,
        colorStops: List<Float>?,
    ): Shader

    /** Creates a [Shader] that tiles [image] using the given per-axis tile modes. */
    fun createImageShader(
        image: ImageBitmap,
        tileModeX: TileMode,
        tileModeY: TileMode,
    ): Shader

    /** Creates a [Shader] compositing [source] over [destination] with [blendMode]. */
    fun createCompositeShader(
        destination: Shader,
        source: Shader,
        blendMode: BlendMode,
    ): Shader

    /** Returns a copy of [shader] with [matrix] applied to its local coordinate space. */
    fun transformShader(shader: Shader, matrix: Matrix): Shader

    /**
     * Creates a blur [PlatformRenderEffect], optionally chained onto [renderEffect], blurring by
     * [radiusX]/[radiusY] with the given [edgeTreatment].
     */
    fun createBlurRenderEffect(
        renderEffect: RenderEffect?,
        radiusX: Float,
        radiusY: Float,
        edgeTreatment: TileMode,
    ): PlatformRenderEffect

    /** Creates an offset [PlatformRenderEffect], optionally chained onto [renderEffect]. */
    fun createOffsetRenderEffect(renderEffect: RenderEffect?, offset: Offset): PlatformRenderEffect

    /**
     * Creates the platform blur-filter implementation used by
     * [androidx.compose.ui.graphics.shadow.BlurFilter].
     *
     * The returned value must be consumable by [setBlurFilter].
     */
    fun createBlurFilter(radius: Float): PlatformBlurFilter

    /** Applies [blur] (or clears it when `null`) to [paint]. */
    fun setBlurFilter(paint: Paint, blur: PlatformBlurFilter?)

    /** Creates a tint [PlatformColorFilter] applying [color] with [blendMode]. */
    fun createTintColorFilter(color: Color, blendMode: BlendMode): PlatformColorFilter

    /** Creates a [PlatformColorFilter] applying the given [colorMatrix]. */
    fun createColorMatrixColorFilter(colorMatrix: ColorMatrix): PlatformColorFilter

    /** Creates a lighting [PlatformColorFilter] that scales by [multiply] and offsets by [add]. */
    fun createLightingColorFilter(multiply: Color, add: Color): PlatformColorFilter

    /** Recovers the [ColorMatrix] backing a color-matrix [filter]. */
    fun colorMatrixFromFilter(filter: PlatformColorFilter): ColorMatrix

    /** Creates a corner-rounding [PathEffect] with the given corner [radius]. */
    fun createCornerPathEffect(radius: Float): PathEffect

    /** Creates a dashing [PathEffect] from the given [intervals] and starting [phase]. */
    fun createDashPathEffect(intervals: FloatArray, phase: Float): PathEffect

    /** Creates a [PathEffect] that applies [inner] before [outer]. */
    fun createChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect

    /** Creates a [PathEffect] that stamps [shape] along the path per [style]. */
    fun createStampedPathEffect(
        shape: Path,
        advance: Float,
        phase: Float,
        style: StampedPathEffectStyle,
    ): PathEffect
}
