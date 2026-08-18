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

package androidx.compose.ui.graphics.webgl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.LocalComposeWindow
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderbuffer
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_COMPLETE
import org.khronos.webgl.WebGLRenderingContext.Companion.RENDERBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.w3c.dom.HTMLCanvasElement

// WebGL2 only; see https://registry.khronos.org/OpenGL/api/GL/glcorearb.h
// #define GL_DEPTH24_STENCIL8 0x88F0
// #define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
private const val GL_DEPTH24_STENCIL8 = 0x88F0
private const val GL_DEPTH_STENCIL_ATTACHMENT = 0x821A

/**
 * An offscreen GPU surface that non-Compose WebGL code can render into, and that Compose can draw
 * without copying anything.
 *
 * It owns a texture allocated in the very same WebGL context Compose renders through, wrapped in a
 * framebuffer with a depth-stencil attachment. Skia adopts that texture, which turns it into an
 * [image] that can be drawn as many times as needed, anywhere in the composition, including inside
 * graphics layers (`clip`, `blur`, `graphicsLayer`).
 *
 * Obtain an instance with [rememberWebGLTextureSurface], render into it with [renderFrames] (or
 * [render]) and draw it with [androidx.compose.ui.graphics.webgl.drawWebGLTexture]:
 * ```
 * val surface = rememberWebGLTextureSurface(IntSize(1024, 640)) ?: return
 *
 * LaunchedEffect(surface) {
 *     surface.renderFrames {
 *         // `this` is a WebGLRenderScope: gl, canvas, framebuffer, size, generation, timing.
 *         myRenderer.render(gl, framebuffer, size, generation, deltaNanos)
 *     }
 * }
 *
 * Canvas(Modifier.fillMaxSize()) { drawWebGLTexture(surface) }
 * ```
 *
 * The texture is RGBA8 with premultiplied alpha, sampled with `LINEAR` filtering and
 * `CLAMP_TO_EDGE` wrapping, without mipmaps and without multisampling. Clearing it to a transparent
 * premultiplied color is what lets Compose content show through the drawn result.
 *
 * Instances are not thread-safe and are meant to be used from the frame loop only.
 */
@ExperimentalComposeUiApi
@Stable
class WebGLTextureSurface
internal constructor(
    private val canvas: HTMLCanvasElement,
    private val gl: WebGLRenderingContext,
    private val directContext: () -> DirectContext?,
    size: IntSize,
) {
    /**
     * The size of the color texture, in pixels, coerced to at least one pixel in each dimension.
     *
     * Changing it discards the current texture and [image] and allocates new ones on the next
     * [render], which also bumps [WebGLRenderScope.generation]. Allocating a texture is not cheap,
     * so avoid driving this from a value that changes every frame.
     */
    var size: IntSize = size.coerceAtLeastOnePixel()
        set(value) {
            field = value.coerceAtLeastOnePixel()
        }

    /**
     * The image that samples the color texture, or `null` until the first [render] succeeded.
     *
     * The surface owns it: it must not be closed, and it must not be retained across frames, since
     * a [size] change replaces it.
     */
    val image: Image?
        get() = adopted?.image

    private val _invalidation = mutableLongStateOf(0L)

    /**
     * A counter incremented after every successful [render].
     *
     * Read it from a draw scope rather than from composition, so that a rendered frame invalidates
     * the drawing without recomposing anything.
     * [androidx.compose.ui.graphics.webgl.drawWebGLTexture] already does that.
     */
    val invalidation: LongState
        get() = _invalidation

    private var adopted: AdoptedGLTexture? = null
    private var framebuffer: WebGLFramebuffer? = null
    private var depthStencil: WebGLRenderbuffer? = null
    private var webGLRenderScope: WegGLRenderScopeImpl? = null
    private var generation = 0
    private var isDisposed = false
    private var isRendering = false
    private var hasRenderedFrame = false
    private var previousFrameTimeNanos = 0L

    /**
     * Renders one frame of foreign WebGL content into this surface, then makes the result available
     * through [image] and bumps [invalidation].
     *
     * This must be called from a [withFrameNanos] callback, so that the texture already holds this
     * frame's content by the time Skia submits the frame that samples it; [renderFrames] does that
     * and is the recommended way to drive a surface. It must never be called from a draw or layout
     * scope: bumping [invalidation] there would invalidate the very drawing that is in progress.
     *
     * The call allocates the texture and the framebuffer if needed, binds the framebuffer, invokes
     * [block], and afterwards rebinds the default framebuffer and makes Skia drop the GL state it
     * had cached before [block] ran.
     *
     * @param frameTimeNanos the time of the frame being rendered, as received from
     *   [withFrameNanos]. It is what [WebGLRenderScope.frameTimeNanos] and
     *   [WebGLRenderScope.deltaNanos] report to [block].
     * @return `false` when the surface could not be prepared, which happens while Compose has not
     *   rendered its first frame yet and therefore has no GPU context to share; [block] is not
     *   invoked in that case.
     */
    fun render(frameTimeNanos: Long, block: WebGLRenderScope.() -> Unit): Boolean {
        if (isDisposed) return false
        check(!isRendering) {
            "render() is already running: it must not be called from within another render() call, " +
                "nor from a draw or layout scope"
        }
        val context = directContext() ?: return false
        val scope = prepareWebGLRenderScope(context, size)
        scope.frameTimeNanos = frameTimeNanos
        scope.deltaNanos = if (hasRenderedFrame) frameTimeNanos - previousFrameTimeNanos else 0L

        isRendering = true
        gl.bindFramebuffer(FRAMEBUFFER, scope.framebuffer)
        try {
            scope.block()
        } finally {
            isRendering = false
            gl.bindFramebuffer(FRAMEBUFFER, null)
            // Everything above went through the context Skia renders Compose with, so whatever Skia
            // believes about the GL state is stale by now.
            context.resetAll()
        }
        previousFrameTimeNanos = frameTimeNanos
        hasRenderedFrame = true
        _invalidation.value++
        return true
    }

    /**
     * Tells Compose that WebGL state was changed outside of [render], so that Skia drops the GL
     * state it had cached.
     *
     * Needed for GL work that cannot happen inside [render], typically a library's own teardown:
     * disposing programs and buffers touches the context Compose renders through as well.
     *
     * @return `false` when Compose has no GPU context to reset, in which case there is nothing to
     *   do.
     */
    fun resetSkiaState(): Boolean {
        val context = directContext() ?: return false
        gl.bindFramebuffer(FRAMEBUFFER, null)
        context.resetAll()
        return true
    }

    private fun prepareWebGLRenderScope(
        context: DirectContext,
        size: IntSize
    ): WegGLRenderScopeImpl {
        val current = adopted
        if (current != null && current.size == size) return webGLRenderScope!!

        current?.dispose()
        adopted = null

        val framebuffer = framebuffer ?: gl.createFramebuffer() ?: error("createFramebuffer failed")
        this.framebuffer = framebuffer
        val depthStencil = depthStencil ?: gl.createRenderbuffer() ?: error("createRenderbuffer failed")
        this.depthStencil = depthStencil

        val adopted = gl.adoptNewTexture(context, size)
        this.adopted = adopted

        gl.bindRenderbuffer(RENDERBUFFER, depthStencil)
        gl.renderbufferStorage(RENDERBUFFER, GL_DEPTH24_STENCIL8, size.width, size.height)
        gl.bindRenderbuffer(RENDERBUFFER, null)

        gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
        gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, adopted.texture, 0)
        gl.framebufferRenderbuffer(
            FRAMEBUFFER,
            GL_DEPTH_STENCIL_ATTACHMENT,
            RENDERBUFFER,
            depthStencil,
        )
        val status = gl.checkFramebufferStatus(FRAMEBUFFER)
        gl.bindFramebuffer(FRAMEBUFFER, null)
        check(status == FRAMEBUFFER_COMPLETE) {
            "the adopted texture is not a complete framebuffer attachment (status $status)"
        }

        webGLRenderScope = WegGLRenderScopeImpl(gl, canvas, framebuffer, size, ++generation)
        return webGLRenderScope!!
    }

    private class WegGLRenderScopeImpl(
        override val webGLContext: WebGLRenderingContext,
        override val htmlCanvas: HTMLCanvasElement,
        override val framebuffer: WebGLFramebuffer,
        override val size: IntSize,
        override val generation: Int,
    ) : WebGLRenderScope {
        override var frameTimeNanos: Long = 0L
        override var deltaNanos: Long = 0L
    }

    /**
     * Releases the texture, the image and the framebuffer. Called by [rememberWebGLTextureSurface]
     * when the surface leaves the composition; calling it twice is a no-op.
     */
    internal fun dispose() {
        if (isDisposed) return
        isDisposed = true
        adopted?.dispose()
        adopted = null
        webGLRenderScope = null
        framebuffer?.let(gl::deleteFramebuffer)
        framebuffer = null
        depthStencil?.let(gl::deleteRenderbuffer)
        depthStencil = null
        gl.bindFramebuffer(FRAMEBUFFER, null)
        directContext()?.resetAll()
    }
}

/**
 * Creates and remembers a [WebGLTextureSurface] of [size] pixels, disposing it when it leaves the
 * composition.
 *
 * @return `null` when Compose does not render through a WebGL2 canvas, in which case there is no
 *   context to share and no texture to adopt. Callers are expected to render a fallback.
 */
@ExperimentalComposeUiApi
@Composable
fun rememberWebGLTextureSurface(size: IntSize): WebGLTextureSurface? {
    val window = LocalComposeWindow.current ?: return null
    val surface =
        remember(window) {
            val canvas = window.htmlCanvas
            val gl = webGl2ContextOrNull(canvas)
            if (gl == null) {
                null
            } else {
                WebGLTextureSurface(canvas, gl, { window.skiaDirectContext }, size)
            }
        } ?: return null

    SideEffect(size) { surface.size = size }
    DisposableEffect(surface) { onDispose { surface.dispose() } }
    return surface
}

private fun IntSize.coerceAtLeastOnePixel(): IntSize =
    if (width >= 1 && height >= 1) this
    else IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
