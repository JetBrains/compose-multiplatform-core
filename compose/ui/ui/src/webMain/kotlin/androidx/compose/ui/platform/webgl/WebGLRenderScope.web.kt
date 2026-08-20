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
 * Scope provided to external renderers for drawing a frame into a [WebGLRenderTarget].
 *
 * Valid only inside [WebGLRenderTarget.render]—do not retain this scope or its resources.
 * Compose automatically restores the default framebuffer and clears cached GL state after each call.
 *
 * **Renderer expectations:**
 * - Do not rely on GL state persisting between frames.
 * - Do not resize [htmlCanvas] or manipulate context lifecycle.
 * - Do not delete [framebuffer] or its attached textures.
 */
@ExperimentalComposeUiApi
sealed interface WebGLRenderScope {
    /** The WebGL2 context shared with Compose */
    val webGLContext: WebGLRenderingContext

    /** The `<canvas>` element owned by Compose, exposed for third-party library initialization. */
    val htmlCanvas: HTMLCanvasElement

    /**
     * The framebuffer owned by this render target. It is bound when the render block starts,
     * but external code may temporarily change the binding and must restore it before returning.
     */
    val framebuffer: WebGLFramebuffer

    /** Framebuffer dimensions in pixels. */
    val size: IntSize

    /**
     * Incremented whenever [framebuffer] or its attachments are recreated (initial setup or size changes).
     * Renderers should check this value to invalidate cached viewports, matrices, or descriptors.
     */
    val generation: Int
}
