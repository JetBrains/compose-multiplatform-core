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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntSize
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement

/**
 * The frame being rendered: everything [WebGLRenderTarget.render] guarantees for the duration of
 * its block, and nothing else.
 *
 * It is the receiver of that block, so a renderer can reach the context and the framebuffer it
 * draws into without qualifying them:
 * ```
 * renderTarget.render {
 *     webGLContext.viewport(0, 0, size.width, size.height)
 *     webGLContext.clearColor(0f, 0.2f, 0.4f, 1f)
 *     webGLContext.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
 * }
 * ```
 *
 * Deliberately narrower than [WebGLRenderTarget] itself: rendering, disposal and
 * [WebGLRenderTarget.markGLStateStale] make no sense while a frame is being drawn - the last one
 * would even unbind [framebuffer] halfway through it - so they are out of scope here. Reach them
 * through the target itself if you really mean to.
 *
 * Valid only for the duration of one [WebGLRenderTarget.render] call. Everything it exposes can
 * change with the next frame, so read it per frame rather than keeping it around.
 */
@ExperimentalComposeUiApi
class WebGLRenderScope internal constructor(private val renderTarget: WebGLRenderTarget) {

    /** The `<canvas>` Compose renders into, which owns [webGLContext]. */
    val htmlCanvas: HTMLCanvasElement
        get() = renderTarget.htmlCanvas

    /** The WebGL2 context to render with - the very one Compose renders itself with. */
    val webGLContext: WebGLRenderingContext
        get() = renderTarget.webGLContext

    /**
     * Size in pixels of the area to render into: pass it to [WebGLRenderingContext.viewport] and
     * base projection matrices on it.
     */
    val size: IntSize
        get() = renderTarget.size

    /**
     * Bumped whenever the attachments behind [framebuffer] are reallocated, which happens on the
     * first frame and after every size change. Use it to drop anything derived from [framebuffer]
     * or [size], such as projection matrices or a third-party render target wrapping them.
     */
    val generation: Int
        get() = renderTarget.generation

    /**
     * The framebuffer this frame draws into, bound for the whole block. Exposed for engines that
     * need the raw handle, such as three.js. The same one for the whole life of the target, so only
     * its attachments change when [size] does - watch [generation] for that.
     *
     * Rebinding it is allowed as long as the binding is restored before the block returns. Deleting
     * it or its attachments is not - they belong to the target.
     */
    val framebuffer: WebGLFramebuffer
        get() = renderTarget.framebuffer

    override fun toString(): String = "WebGLRenderScope(size=$size, generation=$generation)"
}
