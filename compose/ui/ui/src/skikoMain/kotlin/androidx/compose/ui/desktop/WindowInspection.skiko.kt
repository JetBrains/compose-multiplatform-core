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

package androidx.compose.ui.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach

/**
 * Draws on top of a [Window]'s content after the scene drew its frame, outside any composition.
 * The canvas is the scene's, so coordinates match `LayoutCoordinates.boundsInWindow()`.
 */
@InternalComposeUiApi
fun interface WindowOverlayRenderer {
    fun onRenderOverlay(canvas: Canvas, density: Density)
}

/** Tooling access to a [Window]: its host [FrameRecomposer] and an overlay hook. See [Window.inspection]. */
@InternalComposeUiApi
interface WindowInspection {
    /** The host recomposer driving this window's scene. */
    val frameRecomposer: FrameRecomposer

    /** The application session the window belongs to; lets tooling open sibling windows. */
    val session: ApplicationSession

    /** Adds [renderer] on top of the window content; closing the handle removes it. */
    fun addOverlayRenderer(renderer: WindowOverlayRenderer): AutoCloseable

    /** Requests a redraw of the window so overlays that changed outside a frame become visible. */
    fun requestRedraw()
}

@OptIn(InternalComposeUiApi::class)
internal class WindowInspectionImpl(
    override val frameRecomposer: FrameRecomposer,
    private val sessionProvider: () -> ApplicationSession,
    private val density: () -> Density,
    private val requestRedraw: () -> Unit,
) : WindowInspection {
    private val renderers = mutableListOf<WindowOverlayRenderer>()

    override val session: ApplicationSession
        get() = sessionProvider()

    override fun addOverlayRenderer(renderer: WindowOverlayRenderer): AutoCloseable {
        renderers.add(renderer)
        requestRedraw()
        return AutoCloseable {
            renderers.remove(renderer)
            requestRedraw()
        }
    }

    override fun requestRedraw() = requestRedraw.invoke()

    /** Called by the window after the scene drew a frame into [canvas]. */
    fun renderOverlays(canvas: Canvas) {
        if (renderers.isEmpty()) return
        val density = density()
        renderers.toList().fastForEach { it.onRenderOverlay(canvas, density) }
    }
}
