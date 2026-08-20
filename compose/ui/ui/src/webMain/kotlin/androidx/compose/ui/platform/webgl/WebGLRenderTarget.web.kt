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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_COMPLETE
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.RENDERBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.khronos.webgl.WebGLTexture
import org.w3c.dom.HTMLCanvasElement

// WebGL2 only; see https://registry.khronos.org/OpenGL/api/GL/glcorearb.h
// #define GL_DEPTH24_STENCIL8 0x88F0
// #define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
private const val GL_DEPTH24_STENCIL8 = 0x88F0
private const val GL_DEPTH_STENCIL_ATTACHMENT = 0x821A

/**
 * Represents a render target backed by an offscreen WebGL texture created in the same WebGL context
 * that Compose uses for rendering.
 * Its primary purpose is to render WebGL content into a texture that can be drawn alongside Compose
 * content in the same <canvas>.
 *
 * Obtain an instance with [rememberWebGLRenderTarget].
 * [render] allows callers to execute custom WebGL rendering code using the provided context.
 * After a successful [render], the target’s texture will be implicitly used by [drawWebGLTexture].
 * Use [drawWebGLTexture] to draw the frame.
 *
 * Usage example:
 * ```
 * val renderTarget = rememberWebGLRenderTarget(IntSize(1024, 640)) ?: return
 *
 * LaunchedEffect(renderTarget) {
 *     while (true) {
 *         withFrameNanos { frameTimeNanos ->
 *             renderTarget.render {
 *                val phase = (frameTimeNanos % 1_000_000_000L).toFloat() / 1_000_000_000f
 *                webGLContext.clearColor(phase, 0.2f, 0.4f, 1f)
 *                webGLContext.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
 *             }
 *         }
 *     }
 * }
 *
 * Canvas(Modifier.fillMaxSize()) { drawWebGLTexture(renderTarget) }
 * ```
 */
@ExperimentalComposeUiApi
@Stable
class WebGLRenderTarget
internal constructor(
    val htmlCanvas: HTMLCanvasElement,
    val webGLContext: WebGLRenderingContext,
    private val directContext: () -> DirectContext?,
    private val textureFactory: (IntSize) -> WebGLTexture,
    initialSize: IntSize,
) {

    /**
     * Color texture size in pixels (minimum 1x1).
     *
     * Modifying this triggers a texture reallocation on the next [render] and bumps
     * [WebGLRenderScope.generation]. Avoid updating this per-frame due to allocation cost.
     */
    var size: IntSize = initialSize.coerceAtLeastOnePixel()
        set(value) {
            field = value.coerceAtLeastOnePixel()
        }

    /**
     * A lightweight Skiko [Image] wrapping [adoptedTexture]'s GPU memory without copying pixel data.
     *
     * Returns `null` if no texture is currently adopted.
     */
    internal val image: Image?
        get() = adoptedTexture?.image

    private val _invalidation = mutableLongStateOf(0L)

    /**
     * Observes frame invalidation from a draw operation
     */
    internal fun observeInvalidation() {
        _invalidation.value
    }

    private var adoptedTexture: AdoptedGLTexture? = null
    private var framebuffer: WebGLFramebuffer? = null
    private var depthStencil: WebGLRenderbuffer? = null
    private var webGLRenderScope: WegGLRenderScopeImpl? = null
    private var generation = 0
    private var isDisposed = false
    private var isRendering = false

    /**
     * Renders a frame of WebGL content into this surface, updates [image], and triggers a Compose redraw.
     *
     * Must be called within a [withFrameNanos] callback (before Skia samples the frame) and never
     * inside a draw or layout scope.
     *
     * Allocates resources as needed, binds the offscreen framebuffer, executes [block], and restores
     * the default GL state afterward.
     *
     * @return `false` (and skips [block]) if the GPU context is unavailable, such as before Compose's first frame.
     */
    fun render(block: WebGLRenderScope.() -> Unit): Boolean {
        if (isDisposed) return false
        check(!isRendering) {
            "render() is already running: it must not be called from within another render() call, " +
                "nor from a draw or layout scope"
        }
        val context = directContext() ?: return false
        val scope = prepareWebGLRenderScope(context, size)
        isRendering = true
        webGLContext.bindFramebuffer(FRAMEBUFFER, scope.framebuffer)
        try {
            scope.block()
        } finally {
            isRendering = false
            webGLContext.bindFramebuffer(FRAMEBUFFER, null)
            // Everything above went through the context Skia renders Compose with, so whatever Skia
            // believes about the GL state is stale by now.
            context.resetAll()
        }
        _invalidation.value++
        return true
    }

    /**
     * Restores the rendering context back to a clean state expected by Compose.
     *
     * Compose assumes exclusive control over the underlying graphics context.
     * This call informs the context that the GL state was modified outiside of [render].
     *
     * Note: Calling this frequently carries a performance penalty due to GL state cache invalidation.
     */
    fun restoreGLState(): Boolean {
        val context = directContext() ?: return false
        webGLContext.bindFramebuffer(FRAMEBUFFER, null)
        context.resetAll()
        return true
    }

    private fun prepareWebGLRenderScope(
        context: DirectContext,
        size: IntSize,
    ): WegGLRenderScopeImpl {
        val current = adoptedTexture
        if (current != null && current.size == size) return webGLRenderScope!!

        current?.dispose()
        adoptedTexture = null

        val framebuffer =
            framebuffer ?: webGLContext.createFramebuffer() ?: error("createFramebuffer failed")
        this.framebuffer = framebuffer
        val depthStencil =
            depthStencil ?: webGLContext.createRenderbuffer() ?: error("createRenderbuffer failed")
        this.depthStencil = depthStencil

        val adopted = webGLContext.adoptNewTexture(context, size, textureFactory(size))
        this.adoptedTexture = adopted

        webGLContext.bindRenderbuffer(RENDERBUFFER, depthStencil)
        webGLContext.renderbufferStorage(RENDERBUFFER, GL_DEPTH24_STENCIL8, size.width, size.height)
        webGLContext.bindRenderbuffer(RENDERBUFFER, null)

        webGLContext.bindFramebuffer(FRAMEBUFFER, framebuffer)
        webGLContext.framebufferTexture2D(
            FRAMEBUFFER,
            COLOR_ATTACHMENT0,
            TEXTURE_2D,
            adopted.texture,
            0,
        )
        webGLContext.framebufferRenderbuffer(
            FRAMEBUFFER,
            GL_DEPTH_STENCIL_ATTACHMENT,
            RENDERBUFFER,
            depthStencil,
        )
        val status = webGLContext.checkFramebufferStatus(FRAMEBUFFER)
        webGLContext.bindFramebuffer(FRAMEBUFFER, null)
        check(status == FRAMEBUFFER_COMPLETE) {
            "the adopted texture is not a complete framebuffer attachment (status $status)"
        }

        webGLRenderScope =
            WegGLRenderScopeImpl(webGLContext, htmlCanvas, framebuffer, size, ++generation)
        return webGLRenderScope!!
    }

    private class WegGLRenderScopeImpl(
        override val webGLContext: WebGLRenderingContext,
        override val htmlCanvas: HTMLCanvasElement,
        override val framebuffer: WebGLFramebuffer,
        override val size: IntSize,
        override val generation: Int,
    ) : WebGLRenderScope

    /**
     * Releases the texture, the image and the framebuffer. Called by [rememberWebGLRenderTarget]
     * when the surface leaves the composition; calling it twice is a no-op.
     */
    internal fun dispose() {
        if (isDisposed) return
        isDisposed = true
        adoptedTexture?.dispose()
        adoptedTexture = null
        webGLRenderScope = null
        framebuffer?.let(webGLContext::deleteFramebuffer)
        framebuffer = null
        depthStencil?.let(webGLContext::deleteRenderbuffer)
        depthStencil = null
        webGLContext.bindFramebuffer(FRAMEBUFFER, null)
        directContext()?.resetAll()
    }
}

private fun WebGLRenderingContext.defaultWebGLTexture(size: IntSize): WebGLTexture {
    val gl = this
    val texture = gl.createTexture() ?: error("gl.createTexture() returned null")
    gl.bindTexture(TEXTURE_2D, texture)
    // Configure the texture
    gl.texImage2D(TEXTURE_2D, 0, RGBA, size.width, size.height, 0, RGBA, UNSIGNED_BYTE, null)
    // LINEAR for smoother scaling:
    gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
    gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
    // Prevents Edge Artifacts
    gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
    gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
    gl.bindTexture(TEXTURE_2D, null)
    return texture
}

/**
 * Remembers a [WebGLRenderTarget] of the given [size], automatically disposing it when
 * leaving the composition.
 *
 * Updating [size] recreates the underlying GPU resources.
 *
 * @return The target, or `null` if WebGL2 is unsupported.
 */
@ExperimentalComposeUiApi
@Composable
fun rememberWebGLRenderTarget(
    size: IntSize
): WebGLRenderTarget? {
    val window = LocalComposeWindow.current ?: return null
    val renderTarget = remember(window) {
        val canvas = window.htmlCanvas
        val gl = webGl2ContextOrNull(canvas)
        if (gl == null) {
            null
        } else {
            WebGLRenderTarget(
                htmlCanvas = canvas,
                webGLContext = gl,
                directContext = { window.skiaDirectContext },
                textureFactory = { size ->
                    gl.defaultWebGLTexture(size)
                },
                initialSize = size
            )
        }
    } ?: return null
    SideEffect(size) { renderTarget.size = size }
    DisposableEffect(renderTarget) { onDispose { renderTarget.dispose() } }
    return renderTarget
}

private fun IntSize.coerceAtLeastOnePixel(): IntSize =
    if (width >= 1 && height >= 1) this
    else IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
