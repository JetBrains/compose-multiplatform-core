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

import org.jetbrains.skia.BlendMode as SkBlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.VertexMode as SkVertexMode

internal fun BlendMode.toSkia() = when (this) {
    BlendMode.Clear -> SkBlendMode.CLEAR
    BlendMode.Src -> SkBlendMode.SRC
    BlendMode.Dst -> SkBlendMode.DST
    BlendMode.SrcOver -> SkBlendMode.SRC_OVER
    BlendMode.DstOver -> SkBlendMode.DST_OVER
    BlendMode.SrcIn -> SkBlendMode.SRC_IN
    BlendMode.DstIn -> SkBlendMode.DST_IN
    BlendMode.SrcOut -> SkBlendMode.SRC_OUT
    BlendMode.DstOut -> SkBlendMode.DST_OUT
    BlendMode.SrcAtop -> SkBlendMode.SRC_ATOP
    BlendMode.DstAtop -> SkBlendMode.DST_ATOP
    BlendMode.Xor -> SkBlendMode.XOR
    BlendMode.Plus -> SkBlendMode.PLUS
    BlendMode.Modulate -> SkBlendMode.MODULATE
    BlendMode.Screen -> SkBlendMode.SCREEN
    BlendMode.Overlay -> SkBlendMode.OVERLAY
    BlendMode.Darken -> SkBlendMode.DARKEN
    BlendMode.Lighten -> SkBlendMode.LIGHTEN
    BlendMode.ColorDodge -> SkBlendMode.COLOR_DODGE
    BlendMode.ColorBurn -> SkBlendMode.COLOR_BURN
    BlendMode.Hardlight -> SkBlendMode.HARD_LIGHT
    BlendMode.Softlight -> SkBlendMode.SOFT_LIGHT
    BlendMode.Difference -> SkBlendMode.DIFFERENCE
    BlendMode.Exclusion -> SkBlendMode.EXCLUSION
    BlendMode.Multiply -> SkBlendMode.MULTIPLY
    BlendMode.Hue -> SkBlendMode.HUE
    BlendMode.Saturation -> SkBlendMode.SATURATION
    BlendMode.Color -> SkBlendMode.COLOR
    BlendMode.Luminosity -> SkBlendMode.LUMINOSITY
    else -> SkBlendMode.SRC_OVER
}

internal fun TileMode.toSkiaTileMode(): FilterTileMode = when (this) {
    TileMode.Clamp -> FilterTileMode.CLAMP
    TileMode.Repeated -> FilterTileMode.REPEAT
    TileMode.Mirror -> FilterTileMode.MIRROR
    TileMode.Decal -> FilterTileMode.DECAL
    else -> FilterTileMode.CLAMP
}

internal fun VertexMode.toSkiaVertexMode(): SkVertexMode = when (this) {
    VertexMode.Triangles -> SkVertexMode.TRIANGLES
    VertexMode.TriangleStrip -> SkVertexMode.TRIANGLE_STRIP
    VertexMode.TriangleFan -> SkVertexMode.TRIANGLE_FAN
    else -> SkVertexMode.TRIANGLES
}

internal fun identityMatrix33() = Matrix33(
    1f, 0f, 0f,
    0f, 1f, 0f,
    0f, 0f, 1f
)

internal fun Matrix.setFrom(matrix: Matrix33) {
    val v = values
    val m = matrix.mat
    val scaleX = m[0] // MSCALE_X
    val skewX = m[1] // MSKEW_X
    val translateX = m[2] // MTRANS_X
    val skewY = m[3] // MSKEW_Y
    val scaleY = m[4] // MSCALE_Y
    val translateY = m[5] // MTRANS_Y
    val persp0 = m[6] // MPERSP_0
    val persp1 = m[7] // MPERSP_1
    val persp2 = m[8] // MPERSP_2

    v[Matrix.ScaleX] = scaleX // 0
    v[Matrix.SkewY] = skewY // 1
    v[2] = 0f // 2
    v[Matrix.Perspective0] = persp0 // 3
    v[Matrix.SkewX] = skewX // 4
    v[Matrix.ScaleY] = scaleY // 5
    v[6] = 0f // 6
    v[Matrix.Perspective1] = persp1 // 7
    v[8] = 0f // 8
    v[9] = 0f // 9
    v[Matrix.ScaleZ] = 1.0f // 10
    v[11] = 0f // 11
    v[Matrix.TranslateX] = translateX // 12
    v[Matrix.TranslateY] = translateY // 13
    v[14] = 0f // 14
    v[Matrix.Perspective2] = persp2 // 15
}

internal fun Matrix33.setFrom(matrix: Matrix) {
    val scaleX = matrix.values[Matrix.ScaleX]
    val skewY = matrix.values[Matrix.SkewY]
    val value2 = matrix.values[2]
    val persp0 = matrix.values[Matrix.Perspective0]
    val skewX = matrix.values[Matrix.SkewX]
    val scaleY = matrix.values[Matrix.ScaleY]
    val value6 = matrix.values[6]
    val persp1 = matrix.values[Matrix.Perspective1]
    val value8 = matrix.values[8]

    val translateX = matrix.values[Matrix.TranslateX]
    val translateY = matrix.values[Matrix.TranslateY]
    val persp2 = matrix.values[Matrix.Perspective2]

    val values = matrix.values
    values[0] = scaleX
    values[1] = skewX
    values[2] = translateX
    values[3] = skewY
    values[4] = scaleY
    values[5] = translateY
    values[6] = persp0
    values[7] = persp1
    values[8] = persp2

    for (index in 0..8) {
        mat[index] = values[index]
    }

    values[Matrix.ScaleX] = scaleX
    values[Matrix.SkewY] = skewY
    values[2] = value2
    values[Matrix.Perspective0] = persp0
    values[Matrix.SkewX] = skewX
    values[Matrix.ScaleY] = scaleY
    values[6] = value6
    values[Matrix.Perspective1] = persp1
    values[8] = value8
}

// Constant used to convert blur radius into a corresponding sigma value
// for the gaussian blur algorithm used within SkImageFilter.
// This constant approximates the scaling done in the software path's
// "high quality" mode, in SkBlurMask::Blur() (1 / sqrt(3)).
private val BlurSigmaScale = 0.57735f

internal fun BlurEffect.Companion.convertRadiusToSigma(radius: Float) =
    if (radius > 0) {
        BlurSigmaScale * radius + 0.5f
    } else {
        0.0f
    }
