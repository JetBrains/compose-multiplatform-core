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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntSize
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement

/**
 * Everything a non-Compose renderer needs in order to render one frame into a
 * [WebGLTextureSurface].
 *
 * The scope is only valid inside the [WebGLTextureSurface.render] call that provided it: neither it
 * nor any of its values may be retained, because the surface may replace the underlying GL objects
 * between frames (see [generation]).
 *
 * Everything here is shared with Compose, so a renderer is expected to:
 * - restore any global GL state it changes, or at the very least not rely on the state it left
 *   behind in the previous frame, because Skia rendered a frame through the same context in
 *   between;
 * - never resize [htmlCanvas], change its pixel ratio or force a context loss on [webGLContext];
 * - never delete [framebuffer] or the texture attached to it.
 *
 * Compose restores the default framebuffer and lets Skia recover its own cached GL state after
 * every [WebGLTextureSurface.render] call, so a renderer does not need to do that.
 */
@ExperimentalComposeUiApi
sealed interface WebGLRenderScope {
    /**
     * The WebGL2 context Compose renders through.
     *
     * Rendering through this very context is what makes the result usable by Compose without a
     * copy: WebGL has no share groups, so a texture created in another context could never be read
     * by Skia.
     */
    val webGLContext: WebGLRenderingContext

    /**
     * The `<canvas>` element Compose renders into. Exposed because libraries commonly require a
     * canvas alongside a context; its size and its context are owned by Compose.
     */
    val htmlCanvas: HTMLCanvasElement

    /**
     * The framebuffer the frame has to be rendered into. It is bound as [FRAMEBUFFER] when the
     * render block is entered, and has a color texture and a depth-stencil attachment of [size].
     */
    val framebuffer: WebGLFramebuffer

    /** The size of [framebuffer], in pixels. */
    val size: IntSize

    /**
     * The time of the frame being rendered, in nanoseconds, as reported by
     * [androidx.compose.runtime.withFrameNanos].
     */
    val frameTimeNanos: Long

    /**
     * The time elapsed since the previously rendered frame, in nanoseconds, or `0` for the first
     * frame rendered into the surface. This is the value to advance animations by.
     */
    val deltaNanos: Long

    /**
     * Incremented every time the surface recreated [framebuffer] and its attachments, which happens
     * on the first frame and whenever [WebGLTextureSurface.size] changed.
     *
     * Renderers that cache anything derived from the render target — a render target descriptor, a
     * viewport, a projection matrix — should refresh it whenever this value differs from the one
     * seen in the previous frame.
     */
    val generation: Int
}
