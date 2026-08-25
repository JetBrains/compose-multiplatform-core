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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.Painter
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
 * An offscreen render target that lets WebGL content take part in Compose rendering: WebGL code
 * draws into it inside [render], and Compose displays the result through its [painter].
 *
 * It takes care of everything that hand-off needs. It owns the GPU resources — a [framebuffer] with
 * a color texture and a depth/stencil buffer, in the very WebGL context and `<canvas>` Compose
 * renders with — restores the GL state Compose's renderer expects after each frame, and redraws the
 * Compose content once a new frame is ready. Compose draws the color texture as it is, copying no
 * pixels.
 *
 * Obtain an instance with [rememberWebGLRenderTarget], which also disposes it when it leaves the
 * composition.
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
 *                webGLContext.viewport(0, 0, size.width, size.height)
 *                webGLContext.clearColor(phase, 0.2f, 0.4f, 1f)
 *                webGLContext.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
 *             }
 *         }
 *     }
 * }
 *
 * Image(
 *     painter = renderTarget.painter,
 *     contentDescription = null,
 *     contentScale = ContentScale.Crop,
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 */
@ExperimentalComposeUiApi
@Stable
class WebGLRenderTarget internal constructor(
    val htmlCanvas: HTMLCanvasElement,
    val webGLContext: WebGLRenderingContext,
    private val directContext: () -> DirectContext?,
    initialSize: IntSize,
) {

    private var requestedSize: IntSize = initialSize.coerceAtLeastOnePixel()

    /**
     * Size in pixels of the [framebuffer], i.e. the area to render into: pass it to
     * [WebGLRenderingContext.viewport] and base projection matrices on it.
     * [IntSize.Zero] until the first successful [render].
     *
     * Changing the size passed to [rememberWebGLRenderTarget] updates this on the next [render], so
     * it always describes the framebuffer the current frame draws into.
     *
     * Backed by snapshot state, so layout that derives from it - such as a [Painter] sized by
     * [Painter.intrinsicSize] - is redone when the size changes. Only written from [render], which
     * must never run while Compose is drawing.
     */
    var size: IntSize by mutableStateOf(IntSize.Zero)
        private set

    /** Applied by the next [render]; see the `size` parameter of [rememberWebGLRenderTarget]. */
    internal fun requestNewSize(size: IntSize) {
        requestedSize = size.coerceAtLeastOnePixel()
    }

    /**
     * The framebuffer to render into, bound for the duration of the [render] block. Exposed for
     * engines that need the raw handle, such as three.js.
     *
     * Created together with this target and never replaced, so it can be handed to an engine once,
     * at setup: a size change reallocates its attachments, not the framebuffer itself. It only
     * becomes a complete framebuffer once the first [render] allocated those attachments, and
     * [dispose] deletes it, after which it must not be used.
     *
     * Rebinding it inside [render] is allowed as long as the binding is restored before returning.
     * Deleting it or its attachments is not — they belong to this target.
     */
     val framebuffer: WebGLFramebuffer by lazy {
        webGLContext.createFramebuffer() ?: error("gl.createFramebuffer() returned null")
    }

    /**
     * The WebGL texture backing this render target. The texture object remains stable for the
     * lifetime of the [WebGLRenderTarget]. Its storage is configured and resized by the
     * [WebGLRenderTarget]. Callers may bind and attach it to their own framebuffer, but must not
     * delete it or change its storage or texture parameters.
     */
    val webGlTexture: WebGLTexture by lazy {
        webGLContext.createTexture() ?: error("gl.createTexture() returned null")
    }

    /** The depth/stencil attachment of [framebuffer]; like it, created once and only resized. */
    private val depthStencil: WebGLRenderbuffer by lazy {
        webGLContext.createRenderbuffer() ?: error("gl.createRenderbuffer() returned null")
    }

    /**
     * Bumped whenever the attachments of [framebuffer] are reallocated, which happens on the first
     * [render] and after every size change. Use it to drop anything derived from [size] or from the
     * color texture, such as projection matrices or a third-party render target wrapping them.
     * [framebuffer] itself is stable, so it never needs to be read again.
     */
    internal var generation: Int = 0
        private set

    /** The Skia image sampling the color texture, or `null` until the first successful [render]. */
    internal val image: Image?
        get() = adoptedTexture?.image

    /**
     * Draws the last frame rendered into this target, for the standard Compose drawing APIs:
     * ```
     * Image(renderTarget.painter, contentDescription = null, contentScale = ContentScale.Crop)
     * Box(Modifier.paint(renderTarget.painter, contentScale = ContentScale.Fit))
     * Canvas(Modifier.fillMaxSize()) { with(renderTarget.painter) { draw(size) } }
     * ```
     *
     * Its [Painter.intrinsicSize] is [size] as soon as a frame exists, and `Size.Unspecified`
     * before that, so scaling and alignment are up to the caller, as for any other painter. Prefer
     * `Image`, which clips the frame to its bounds: the painter fills the size it is given, so
     * `ContentScale.Crop` scales the frame beyond that size and `Modifier.paint` alone would let it
     * spill over its neighbours unless `Modifier.clipToBounds` is added. Note also that
     * `Modifier.paint` defaults to `ContentScale.Inside`, which never scales a frame up.
     *
     * Drawing it issues no GL commands, only a draw of the texture that [render] filled, so it is
     * safe inside graphics layers such as `clip` and `blur`, and can draw the same frame in several
     * places. Each new frame repeats the drawing on its own, without recomposing.
     *
     * The same instance is returned every time, so that drawing it does not restart on every
     * recomposition.
     */
    val painter: Painter by lazy { WebGLRenderTargetPainter(this) }

    /**
     * Called before the current texture-backed render resource becomes unavailable.
     * It happens when the texture is about to be reconfigured for a new size or the
     * [WebGLRenderTarget] is being disposed.
     */
    var onTextureWillBeInvalidated: (() -> Unit)? = null

    private val _invalidation = mutableLongStateOf(0L)

    /** Makes the calling draw operation repeat whenever a new frame is rendered. */
    internal fun observeInvalidation() {
        _invalidation.value
    }

    private var adoptedTexture: AdoptedGLTexture? = null
    private var isDisposed = false
    private var isRendering = false

    /**
     * Renders one frame into this target and invalidates everything that draws its [painter], so
     * it all shows the new frame.
     *
     * Allocates or reallocates GPU resources if needed, binds [framebuffer], runs [block], then
     * restores the GL state Compose's renderer expects. The [block] runs while this target's
     * framebuffer is bound; use this target's context, size and framebuffer to draw.
     *
     * Prefer calling this from a [withFrameNanos] callback: the frame is then ready before Compose
     * draws, so the new content appears immediately. Rendering at another time is allowed, but the
     * content only appears in a later Compose frame.
     *
     * Never call this from a draw scope, such as a `Canvas` or `Modifier.drawBehind`: the GL state
     * would be reset while Compose is drawing the frame, and the invalidation would come from
     * within the drawing it invalidates, keeping that drawing repeating with no loop to stop:
     * ```
     * Canvas(Modifier.fillMaxSize()) {
     *     // Wrong: render() must not run while Compose is drawing.
     *     renderTarget.render { renderer.drawFrame(renderTarget) }
     *     with(renderTarget.painter) { draw(size) }
     * }
     * ```
     *
     * @return `false`, skipping [block], if the GPU context is not available yet — which is the
     *   case until Compose has drawn its first frame.
     */
    fun render(block: () -> Unit): Boolean {
        if (isDisposed) return false
        check(!isRendering) {
            "render() is already running: it must not be called from within another render() call, " +
                "nor from a draw or layout scope"
        }
        val context = directContext() ?: return false
        prepareAttachments(context, requestedSize)
        isRendering = true
        webGLContext.bindFramebuffer(FRAMEBUFFER, framebuffer)
        try {
            block()
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
     * Marks the GL state as changed outside of [render], so that Compose's renderer stops assuming
     * the state it last set is still in place.
     *
     * [render] does this for its own block already, so this is only needed when code touches
     * [webGLContext] on its own — typically while setting up or tearing down a third-party engine.
     * The renderer then has to reapply its whole state, so avoid calling this per frame.
     */
    fun markGLStateStale() {
        val context = directContext() ?: return
        webGLContext.bindFramebuffer(FRAMEBUFFER, null)
        context.resetAll()
    }

    /** Allocates the attachments of [framebuffer] for [size], unless they already have that size. */
    private fun prepareAttachments(
        context: DirectContext,
        size: IntSize,
    ) {
        val current = adoptedTexture
        if (current != null && current.size == size) return

        if (current != null) {
            onTextureWillBeInvalidated?.invoke()
            current.dispose()
        }

        adoptedTexture = null

        webGLContext.configureWebGLTexture(webGlTexture, size)
        val adopted = webGLContext.adoptNewTexture(context, size, webGlTexture)
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

        this.size = size
        generation++
    }

    /**
     * Releases the framebuffer and its attachments. Called by [rememberWebGLRenderTarget] when the
     * target leaves the composition; calling it twice is a no-op.
     */
    internal fun dispose() {
        if (isDisposed) return
        isDisposed = true
        if (adoptedTexture != null) {
            onTextureWillBeInvalidated?.invoke()
            adoptedTexture?.dispose()
            adoptedTexture = null
        }
        size = IntSize.Zero
        webGLContext.deleteFramebuffer(framebuffer)
        webGLContext.deleteRenderbuffer(depthStencil)
        webGLContext.bindFramebuffer(FRAMEBUFFER, null)
        directContext()?.resetAll()
    }
}

private fun WebGLRenderingContext.configureWebGLTexture(
    texture: WebGLTexture,
    size: IntSize
) {
    val gl = this
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
}

/**
 * Remembers a [WebGLRenderTarget] of [size] pixels, disposing it when it leaves the composition.
 *
 * This is the only way to size the target: a changed [size] reallocates its GPU resources on the
 * next [WebGLRenderTarget.render], so avoid changing it per frame.
 *
 * @return The target, or `null` if the browser does not support WebGL2.
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
                initialSize = size
            )
        }
    } ?: return null
    SideEffect(size) { renderTarget.requestNewSize(size) }
    DisposableEffect(renderTarget) { onDispose { renderTarget.dispose() } }
    return renderTarget
}

private fun IntSize.coerceAtLeastOnePixel(): IntSize =
    if (width >= 1 && height >= 1) this
    else IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
