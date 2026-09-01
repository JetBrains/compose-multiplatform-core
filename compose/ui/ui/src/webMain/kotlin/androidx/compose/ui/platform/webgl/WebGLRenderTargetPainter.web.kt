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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Rect

/**
 * The [Painter] behind [WebGLRenderTarget.painter]: draws the last frame rendered into
 * [renderTarget], scaled to whatever bounds the caller gives it.
 *
 * Follows the [Painter] contract and fills the size it receives, rather than placing the frame
 * itself. Scaling and alignment then come from the caller - `Image`, `Modifier.paint` - which
 * derive them from [intrinsicSize].
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class WebGLRenderTargetPainter(
    private val renderTarget: WebGLRenderTarget,
) : Painter() {

    /**
     * The size of the rendered frame, or `Size.Unspecified` while there is none, which lets the
     * painter fill the bounds it is given instead of collapsing them to zero.
     *
     * Read during layout, so it relies on [WebGLRenderTarget.size] being snapshot state to have a
     * new size trigger a new layout.
     */
    override val intrinsicSize: Size
        get() {
            val size = renderTarget.size
            return if (size == IntSize.Zero) Size.Unspecified
            else Size(size.width.toFloat(), size.height.toFloat())
        }

    override fun DrawScope.onDraw() {
        // Schedules the next redraw once a new frame is rendered, without recomposing.
        renderTarget.observeInvalidation()

        val image = renderTarget.adoptedTexture?.image ?: return
        if (size.width <= 0f || size.height <= 0f) return
        if (image.width <= 0 || image.height <= 0) return

        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(size.width, size.height),
            )
        }
    }

    /**
     * Identity, unlike the value equality [Painter] asks for: this painter stands for the mutable
     * GPU resources of one target, which nothing else can be equal to. [WebGLRenderTarget.painter]
     * hands out a single instance per target, so identity is all callers can observe anyway.
     */
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is WebGLRenderTargetPainter && renderTarget === other.renderTarget)

    override fun hashCode(): Int = renderTarget.hashCode()

    override fun toString(): String = "WebGLRenderTargetPainter(size=${renderTarget.size})"
}
