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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
import org.khronos.webgl.WebGLRenderingContext.Companion.NO_ERROR

/** Opaque red as `0xRRGGBBAA`, chosen because every channel is exact in RGBA8. */
private const val OPAQUE_RED = (255 shl 24) or 255
private const val OPAQUE_GREEN = (255 shl 16) or 255

private fun clearTo(target: WebGLRenderTarget, red: Float, green: Float, blue: Float): Boolean =
    target.render {
        target.webGLContext.viewport(0, 0, target.size.width, target.size.height)
        target.webGLContext.clearColor(red, green, blue, 1f)
        target.webGLContext.clear(COLOR_BUFFER_BIT)
    }

private fun clearToRed(target: WebGLRenderTarget): Boolean = clearTo(target, 1f, 0f, 0f)

private fun clearToGreen(target: WebGLRenderTarget): Boolean = clearTo(target, 0f, 1f, 0f)

class WebGLRenderTargetTests : OnCanvasTests {

    @Test
    fun dpSizeIsConvertedToPixelsUsingCurrentDensity() = runApplicationTest {
        var renderTarget: WebGLRenderTarget? = null

        createComposeWindow {
            CompositionLocalProvider(LocalDensity provides Density(3f)) {
                renderTarget = rememberWebGLRenderTarget(DpSize(32.dp, 16.dp))
            }
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        awaitAnimationFrame()
        awaitIdle()

        assertTrue(clearToRed(target), "render() did not run")
        assertEquals(IntSize(96, 48), target.size, "DpSize was converted incorrectly")
    }

    /**
     * The simplest possible renderer: clear the target to a known color. Verifies that a frame
     * reaches the texture, that Compose draws it, and that WebGL reports no error along the way.
     */
    @Test
    fun clearingToAKnownColorProducesAFrameWithoutGLErrors() = runApplicationTest {
        val frames = 3
        var renderTarget: WebGLRenderTarget? = null
        var renderedFrames = 0
        var drawnFrames = 0

        createComposeWindow {
            val target = rememberWebGLRenderTarget(IntSize(64, 64))
            renderTarget = target
            if (target != null) {
                LaunchedEffect(target) {
                    repeat(frames) {
                        withFrameNanos {
                            if (clearToRed(target)) renderedFrames++
                        }
                    }
                }
                // Drawn by hand rather than with Image, so that the counter sits in the very draw
                // scope that a new frame has to repeat.
                Canvas(Modifier.size(64.dp)) {
                    drawnFrames++
                    val bounds = this.size
                    with(target.painter) { draw(bounds) }
                }
            }
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        repeat(frames + 2) { awaitAnimationFrame() }
        awaitIdle()

        assertTrue(renderedFrames > 0, "render() never ran the block")
        assertTrue(drawnFrames > 0, "the painter was never drawn")

        assertEquals(IntSize(64, 64), target.size, "unexpected allocated size")
        assertNotNull(target.adoptedTexture?.image, "the color texture was not adopted")
        assertTrue(target.generation > 0, "generation was never bumped")

        // Read the frame back from the target's own framebuffer, which render() keeps bound.
        var centerPixel = 0
        var glError = -1
        val rendered = target.render {
            with (target) {
                webGLContext.viewport(0, 0, size.width, size.height)
                webGLContext.clearColor(1f, 0f, 0f, 1f)
                webGLContext.clear(COLOR_BUFFER_BIT)
                centerPixel = readPixelRgba8(webGLContext, size.width / 2, size.height / 2)
                glError = webGLContext.getError()

                val attachedTexture = webGLContext.getFramebufferAttachmentParameter(
                    FRAMEBUFFER,
                    COLOR_ATTACHMENT0,
                    FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,
                )
                assertSame(target.webGlTexture, attachedTexture, "unexpected framebuffer texture")
            }
        }

        assertTrue(rendered, "render() did not run after Compose's first frame")
        assertEquals(NO_ERROR, glError, "the WebGL context reported an error")
        assertEquals(
            OPAQUE_RED.toHexString(),
            centerPixel.toHexString(),
            "the cleared color did not reach the texture"
        )
    }

    /** A new size given to [rememberWebGLRenderTarget] must reallocate on the next render. */
    @Test
    fun changingTheSizeReallocatesTheTexture() = runApplicationTest {
        val requestedSize = mutableStateOf(IntSize(32, 32))
        var renderTarget: WebGLRenderTarget? = null

        createComposeWindow {
            val size by requestedSize
            val target = rememberWebGLRenderTarget(size)
            renderTarget = target
            if (target != null) {
                Image(
                    painter = target.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
        awaitAnimationFrame()
        awaitIdle()

        assertTrue(clearToRed(target), "the first render() did not run")
        assertEquals(IntSize(32, 32), target.size, "unexpected initial size")
        val generationBefore = target.generation
        val framebufferBefore = target.framebuffer
        val textureBefore = target.webGlTexture
        var invalidationCount = 0
        target.onTextureWillBeInvalidated = {
            invalidationCount++
            assertSame(textureBefore, target.webGlTexture, "the old texture was replaced too early")
            assertTrue(
                target.webGLContext.isTexture(textureBefore),
                "the old texture was deleted before the invalidation callback",
            )
        }

        requestedSize.value = IntSize(48, 24)
        awaitAnimationFrame()
        awaitIdle()

        assertTrue(clearToRed(target), "render() did not run after the size change")
        assertEquals(1, invalidationCount, "the texture invalidation listener was not invoked")
        assertEquals(IntSize(48, 24), target.size, "the new size was not applied")
        assertTrue(
            target.generation > generationBefore,
            "generation did not change although the texture was reallocated"
        )
        // Only the attachments are reallocated, so an engine may keep the handle from setup.
        assertSame(
            framebufferBefore,
            target.framebuffer,
            "the framebuffer itself was replaced by the size change"
        )
        assertNotSame(
            textureBefore,
            target.webGlTexture,
            "the texture adopted and deleted by Skia was reused after the size change",
        )
        assertEquals(NO_ERROR, target.webGLContext.getError(), "reallocation reported a GL error")

        val textureAfterResize = target.webGlTexture
        target.onTextureWillBeInvalidated = { error("expected invalidation failure") }
        requestedSize.value = IntSize(24, 48)
        awaitAnimationFrame()
        awaitIdle()
        assertFailsWith<IllegalStateException> { clearToRed(target) }
        assertEquals(null, target.adoptedTexture, "a failing callback kept the adopted image")
        assertNotSame(
            textureAfterResize,
            target.webGlTexture,
            "a failing callback kept the old texture as the current texture",
        )
        target.onTextureWillBeInvalidated = null
        assertTrue(clearToGreen(target), "render() did not recover after the callback failed")
        assertEquals(IntSize(24, 48), target.size, "the size was not applied after recovery")
    }

    /**
     * The whole pipeline: a frame rendered into the texture must end up in the pixels of the
     * Compose canvas, drawn where the composable is.
     *
     * Reading those pixels needs `preserveDrawingBuffer`, since WebGL discards the drawing buffer
     * once the browser has composited it.
     */
    @Test
    fun theRenderedFrameReachesTheComposeCanvasBeforeAndAfterResize() = runApplicationTest {
        assertTrue(forcePreserveDrawingBuffer(), "could not force preserveDrawingBuffer")
        try {
            var renderTarget: WebGLRenderTarget? = null
            val requestedSize = mutableStateOf(IntSize(64, 64))

            createComposeWindow {
                val size by requestedSize
                val target = rememberWebGLRenderTarget(size)
                renderTarget = target
                if (target != null) {
                    LaunchedEffect(target) {
                        repeat(30) { withFrameNanos { clearToRed(target) } }
                    }
                    // Setting the density to 2 so the test works correctly on all displays
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Image(
                            painter = target.painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(100.dp),
                        )
                    }
                }
            }

            val target = renderTarget ?: return@runApplicationTest skipWithoutWebGL2()
            val gl = target.webGLContext
            assertEquals(
                "true",
                contextAttribute(gl, "preserveDrawingBuffer"),
                "the Compose context ignored the forced attribute"
            )

            // readPixels() counts rows from the bottom, Compose from the top.
            val canvasHeight = getCanvas().height
            val insideY = canvasHeight - 100
            val outsideY = canvasHeight - 300

            val inside = awaitCanvasPixel(gl, x = 100, y = insideY, expected = OPAQUE_RED)
            assertEquals(
                OPAQUE_RED.toHexString(),
                inside.toHexString(),
                "the texture did not reach the canvas inside the composable"
            )
            assertNotEquals(
                OPAQUE_RED.toHexString(),
                readCanvasPixelRgba8(gl, 600, outsideY).toHexString(),
                "the texture was drawn outside the composable"
            )

            requestedSize.value = IntSize(48, 24)
            awaitAnimationFrame()
            awaitIdle()
            assertTrue(clearToGreen(target), "render() did not run after the size change")
            val resized = awaitCanvasPixel(gl, x = 100, y = insideY, expected = OPAQUE_GREEN)
            assertEquals(
                OPAQUE_GREEN.toHexString(),
                resized.toHexString(),
                "the resized texture did not reach the canvas inside the composable",
            )
        } finally {
            restorePreserveDrawingBuffer()
        }
    }

    /** Polls up to [frames] Compose frames for [expected] to show up at ([x], [y]). */
    private suspend fun WebApplicationScope.awaitCanvasPixel(
        gl: WebGLRenderingContext,
        x: Int,
        y: Int,
        expected: Int,
        frames: Int = 30,
    ): Int {
        var pixel = 0
        repeat(frames) {
            awaitAnimationFrame()
            pixel = readCanvasPixelRgba8(gl, x, y)
            if (pixel == expected) return pixel
        }
        return pixel
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

private fun Int.toHexString(): String = toUInt().toString(16)

/** Reads one pixel of the bound framebuffer as `0xRRGGBBAA`. */
// language=js
private fun readPixelRgba8(gl: WebGLRenderingContext, x: Int, y: Int): Int = js(
    """(function() {
        const pixel = new Uint8Array(4);
        gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);
        return (pixel[0] << 24) | (pixel[1] << 16) | (pixel[2] << 8) | pixel[3];
    })()"""
)

// language=js
private fun forcePreserveDrawingBuffer(): Boolean = js(
    """(function() {
        const proto = HTMLCanvasElement.prototype;
        const original = proto.__composeTestOriginalGetContext || proto.getContext;
        proto.__composeTestOriginalGetContext = original;
        proto.getContext = function(type, attributes) {
            if (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl') {
                attributes = Object.assign({}, attributes || {}, { preserveDrawingBuffer: true });
            }
            return original.call(this, type, attributes);
        };
        return true;
    })()"""
)

// language=js
private fun contextAttribute(gl: WebGLRenderingContext, name: String): String = js(
    """(function() {
        const attributes = gl.getContextAttributes();
        return attributes ? String(attributes[name]) : 'n/a';
    })()"""
)

/** Undoes [forcePreserveDrawingBuffer], so that other tests see the default context attributes. */
// language=js
private fun restorePreserveDrawingBuffer(): Unit = js(
    """(function() {
        const proto = HTMLCanvasElement.prototype;
        if (proto.__composeTestOriginalGetContext) {
            proto.getContext = proto.__composeTestOriginalGetContext;
            proto.__composeTestOriginalGetContext = undefined;
        }
    })()"""
)

/** Reads one pixel of the *default* framebuffer (the Compose canvas) as `0xRRGGBBAA`. */
// language=js
private fun readCanvasPixelRgba8(gl: WebGLRenderingContext, x: Int, y: Int): Int = js(
    """(function() {
        const previous = gl.getParameter(gl.FRAMEBUFFER_BINDING);
        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        const pixel = new Uint8Array(4);
        gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);
        gl.bindFramebuffer(gl.FRAMEBUFFER, previous);
        return (pixel[0] << 24) | (pixel[1] << 16) | (pixel[2] << 8) | pixel[3];
    })()"""
)
