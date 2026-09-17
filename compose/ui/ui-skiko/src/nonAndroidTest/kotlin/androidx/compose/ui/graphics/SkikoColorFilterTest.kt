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
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.skia.Surface

@OptIn(InternalComposeUiApi::class)
class SkikoColorFilterTest {

    @BeforeTest
    fun setup() {
        registerSkikoComposeImplementation()
    }

    @AfterTest
    fun tearDown() {
        clearSkikoComposeImplementation()
    }

    @Test
    fun tintWithABlendThatCannotChangeTheColorSucceeds() {
        for ((color, blendMode) in blendsSkiaTreatsAsNoOps) {
            assertNotNull(
                ColorFilter.tint(color, blendMode).asSkiaColorFilter(),
                "tint($color, $blendMode)"
            )
        }
    }

    @Test
    fun tintWithABlendThatChangesTheColorSucceeds() {
        for (blendMode in listOf(BlendMode.SrcAtop, BlendMode.SrcOver, BlendMode.DstIn)) {
            assertNotNull(
                ColorFilter.tint(Color.Red.copy(alpha = 0.5f), blendMode).asSkiaColorFilter(),
                "tint(half transparent red, $blendMode)"
            )
        }
    }

    @Test
    fun anAlphaRoundingDownToTransparentCountsAsTransparent() {
        val almostTransparent = Color.Red.copy(alpha = 0.001f)

        assertNotNull(ColorFilter.tint(almostTransparent, BlendMode.SrcAtop).asSkiaColorFilter())
    }

    @Test
    fun aTintThatCannotChangeTheColorDrawsTheColorUnchanged() {
        for ((color, blendMode) in blendsSkiaTreatsAsNoOps) {
            assertEquals(
                Color.Red,
                drawRed(ColorFilter.tint(color, blendMode)),
                "tint($color, $blendMode) should leave the drawn color alone"
            )
        }
    }

    @Test
    fun aTintThatChangesTheColorIsStillApplied() {
        assertEquals(Color.Blue, drawRed(ColorFilter.tint(Color.Blue, BlendMode.SrcIn)))
    }

    /** Fills a small surface with [Color.Red] through [colorFilter] and reads a pixel back. */
    private fun drawRed(colorFilter: ColorFilter): Color {
        val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
        val paint = Paint().apply {
            color = Color.Red
            this.colorFilter = colorFilter
        }

        surface.canvas.asComposeCanvas()
            .drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
        surface.flushAndSubmit(true)

        val pixels = surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap()
        return pixels[SIZE / 2, SIZE / 2]
    }

    private companion object {
        const val SIZE = 4

        /** Every color and blend pair for which skia reports no filter at all. */
        val blendsSkiaTreatsAsNoOps = listOf(
            Color.Red to BlendMode.Dst,
            Color.Transparent to BlendMode.Dst,
            Color.Transparent to BlendMode.SrcOver,
            Color.Transparent to BlendMode.DstOver,
            Color.Transparent to BlendMode.DstOut,
            Color.Transparent to BlendMode.SrcAtop,
            Color.Transparent to BlendMode.Xor,
            Color.Transparent to BlendMode.Darken,
            Color.Red to BlendMode.DstIn,
        )
    }
}
