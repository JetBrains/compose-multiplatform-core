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

package androidx.compose.ui.platform.webgl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.skia.Rect

/**
 * Placement math behind [drawWebGLTexture]. Pure arithmetic, so it needs no canvas: the texture is
 * cropped where the scaled image covers the destination, and the destination is inset where it does
 * not.
 */
class WebGLTexturePlacementTests {

    private val wide = Size(64f, 32f)
    private val square = Size(100f, 100f)

    @Test
    fun cropFillsTheDestinationAndCropsTheLongerAxis() {
        val placement = placement(wide, square, ContentScale.Crop)
        // scale = max(100/64, 100/32) = 3.125, so 64x32 becomes 200x100: too wide by 100px.
        assertRect(Rect.makeXYWH(16f, 0f, 32f, 32f), placement.src, "src")
        assertRect(Rect.makeXYWH(0f, 0f, 100f, 100f), placement.dst, "dst")
    }

    @Test
    fun fitShowsTheWholeTextureAndCentersTheShorterAxis() {
        val placement = placement(wide, square, ContentScale.Fit)
        // scale = min(100/64, 100/32) = 1.5625, so 64x32 becomes 100x50: 50px of empty height.
        assertRect(Rect.makeXYWH(0f, 0f, 64f, 32f), placement.src, "src")
        assertRect(Rect.makeXYWH(0f, 25f, 100f, 50f), placement.dst, "dst")
    }

    @Test
    fun fillBoundsStretchesBothAxes() {
        val placement = placement(wide, square, ContentScale.FillBounds)
        assertRect(Rect.makeXYWH(0f, 0f, 64f, 32f), placement.src, "src")
        assertRect(Rect.makeXYWH(0f, 0f, 100f, 100f), placement.dst, "dst")
    }

    @Test
    fun noneCentersTheTextureAtItsOwnSize() {
        val placement = placement(wide, square, ContentScale.None)
        assertRect(Rect.makeXYWH(0f, 0f, 64f, 32f), placement.src, "src")
        assertRect(Rect.makeXYWH(18f, 34f, 64f, 32f), placement.dst, "dst")
    }

    @Test
    fun insideDownscalesOnlyWhenTheTextureIsLarger() {
        val larger = placement(Size(200f, 100f), square, ContentScale.Inside)
        // scale = min(1, min(0.5, 1)) = 0.5, so 200x100 becomes 100x50.
        assertRect(Rect.makeXYWH(0f, 0f, 200f, 100f), larger.src, "downscaled src")
        assertRect(Rect.makeXYWH(0f, 25f, 100f, 50f), larger.dst, "downscaled dst")

        val smaller = placement(wide, square, ContentScale.Inside)
        assertRect(Rect.makeXYWH(18f, 34f, 64f, 32f), smaller.dst, "untouched dst")
    }

    @Test
    fun dstOffsetShiftsTheDestination() {
        val cropped = placement(wide, square, ContentScale.Crop, Offset(10f, 20f))
        assertRect(Rect.makeXYWH(10f, 20f, 100f, 100f), cropped.dst, "cropped dst")

        // The offset applies before centering, so an inset axis keeps its inset.
        val fitted = placement(wide, square, ContentScale.Fit, Offset(10f, 20f))
        assertRect(Rect.makeXYWH(10f, 45f, 100f, 50f), fitted.dst, "fitted dst")
    }

    @Test
    fun nothingIsDrawnWithoutAUsableSize() {
        assertNull(
            webGLTexturePlacement(wide, Size.Unspecified, Offset.Zero, ContentScale.Crop),
            "unspecified destination"
        )
        assertNull(
            webGLTexturePlacement(wide, Size(100f, 0f), Offset.Zero, ContentScale.Crop),
            "empty destination"
        )
        assertNull(
            webGLTexturePlacement(Size(0f, 32f), square, Offset.Zero, ContentScale.Crop),
            "empty texture"
        )
        assertNull(
            webGLTexturePlacement(wide, square, Offset.Zero, ZeroScale),
            "zero scale factor"
        )
    }

    private fun placement(
        srcSize: Size,
        dstSize: Size,
        contentScale: ContentScale,
        dstOffset: Offset = Offset.Zero,
    ): WebGLTexturePlacement =
        assertNotNull(
            webGLTexturePlacement(srcSize, dstSize, dstOffset, contentScale),
            "no placement for $srcSize in $dstSize"
        )

    private fun assertRect(expected: Rect, actual: Rect, name: String) {
        assertEquals(expected.left, actual.left, "$name left")
        assertEquals(expected.top, actual.top, "$name top")
        assertEquals(expected.right, actual.right, "$name right")
        assertEquals(expected.bottom, actual.bottom, "$name bottom")
    }
}

private object ZeroScale : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size) =
        androidx.compose.ui.layout.ScaleFactor(0f, 0f)
}
