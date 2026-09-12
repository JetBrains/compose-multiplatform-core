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

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.ui.platform.webgl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT

/**
 * Draws [painter] over [size], sparing every call site the `with(painter) { draw(size) }` dance
 * that [Painter.draw] being a member extension otherwise forces.
 */
private fun DrawScope.drawPainter(painter: Painter, size: Size = this.size) {
    with(painter) { draw(size) }
}

/** The whole "renderer": clear the target to opaque red, like the ColorPulse demo. */
private fun clearToRed(target: WebGLRenderTarget): Boolean = target.render {
    target.webGLContext.viewport(0, 0, target.size.width, target.size.height)
    target.webGLContext.clearColor(1f, 0f, 0f, 1f)
    target.webGLContext.clear(COLOR_BUFFER_BIT)
}

class WebGLRenderTargetPainterTests : OnCanvasTests {

    /**
     * A painter with no frame yet must not claim a size, so that layout keeps giving it the bounds
     * it would get from any other painter without an intrinsic size.
     */
    @Test
    fun theIntrinsicSizeIsUnspecifiedUntilTheFirstFrame() = runApplicationTest {
        var renderTarget: WebGLRenderTarget? = null

        createComposeWindow {
            renderTarget = rememberWebGLRenderTarget(IntSize(32, 32))
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        awaitAnimationFrame()
        awaitIdle()

        assertEquals(IntSize.Zero, target.size, "the target allocated without a render()")
        assertTrue(
            target.painter.intrinsicSize.isUnspecified,
            "the painter claimed a size although no frame was rendered"
        )
    }

    /**
     * Once a frame exists, the painter is as big as that frame - and it follows the frame when the
     * requested size changes, which is what makes layout pick up a new size.
     */
    @Test
    fun theIntrinsicSizeFollowsTheRenderedFrame() = runApplicationTest {
        val requestedSize = mutableStateOf(IntSize(32, 32))
        var renderTarget: WebGLRenderTarget? = null

        createComposeWindow {
            val currentSize by requestedSize
            val target = rememberWebGLRenderTarget(currentSize)
            renderTarget = target
            if (target != null) {
                // The way callers are meant to use the painter, rather than drawing it by hand.
                Box(Modifier.size(32.dp).paint(target.painter))
            }
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        awaitAnimationFrame()
        awaitIdle()

        assertTrue(clearToRed(target), "the first render() did not run")
        assertEquals(
            Size(32f, 32f),
            target.painter.intrinsicSize,
            "the painter did not take the size of the first frame"
        )

        requestedSize.value = IntSize(48, 24)
        awaitAnimationFrame()
        awaitIdle()

        assertTrue(clearToRed(target), "render() did not run after the size change")
        assertEquals(
            Size(48f, 24f),
            target.painter.intrinsicSize,
            "the painter kept the size of the previous frame"
        )
    }

    /**
     * Drawing the painter must repeat on every new frame, and must do so from the draw phase alone:
     * a frame that recomposes would defeat the point of drawing an already rendered texture.
     */
    @Test
    fun everyRenderedFrameIsDrawnAgainWithoutRecomposing() = runApplicationTest {
        val frames = 3
        var renderTarget: WebGLRenderTarget? = null
        var compositions = 0
        var renderedFrames = 0
        var draws = 0

        createComposeWindow {
            compositions++
            val target = rememberWebGLRenderTarget(IntSize(64, 64))
            renderTarget = target
            if (target != null) {
                LaunchedEffect(target) {
                    repeat(frames) {
                        withFrameNanos { if (clearToRed(target)) renderedFrames++ }
                    }
                }
                // Drawn by hand, so that the draw counter sits in the very scope that the
                // painter's invalidation has to repeat.
                Canvas(Modifier.size(64.dp)) {
                    draws++
                    drawPainter(target.painter)
                }
            }
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        val compositionsBefore = compositions
        repeat(frames + 2) { awaitAnimationFrame() }
        awaitIdle()

        assertTrue(renderedFrames > 0, "render() never ran the block")
        assertTrue(draws >= renderedFrames, "a rendered frame was not drawn again: $draws draws")
        assertEquals(
            compositionsBefore,
            compositions,
            "drawing the painter recomposed instead of only redrawing"
        )
        assertEquals(
            Size(64f, 64f),
            target.painter.intrinsicSize,
            "the painter did not take the size of the rendered frames"
        )
    }

    /**
     * The painter is part of the target's identity: handing out a new instance per read would make
     * `Image(target.painter, ...)` restart its layout and drawing on every recomposition.
     */
    @Test
    fun thePainterIsOneStableInstancePerTarget() = runApplicationTest {
        var renderTarget: WebGLRenderTarget? = null

        createComposeWindow {
            renderTarget = rememberWebGLRenderTarget(IntSize(16, 16))
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        awaitIdle()

        val painter = target.painter
        assertSame(painter, target.painter, "the target handed out a second painter")
        assertEquals(painter, target.painter, "the painter is not equal to itself")
        assertEquals(painter.hashCode(), target.painter.hashCode(), "unstable hashCode")
    }

    /**
     * Compose only creates a render target when it renders through a WebGL2 canvas, so a missing
     * target is a valid outcome - as long as the canvas really has no WebGL2 context.
     */
    private fun skipWithoutWebGL2() {
        assertFalse(
            getCanvas().getContext("webgl2") != null,
            "no render target although the Compose canvas has a WebGL2 context"
        )
        println("skipped: the Compose canvas does not have a WebGL2 context")
    }
}
