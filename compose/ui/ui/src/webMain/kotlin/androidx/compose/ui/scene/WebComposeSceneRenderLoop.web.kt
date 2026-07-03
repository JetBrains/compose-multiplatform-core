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

package androidx.compose.ui.scene

import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.CoroutineContext
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.w3c.dom.HTMLCanvasElement

/**
 * Owns the [SkiaLayer], [FrameRecomposer], and render-invalidation scheduling bound to a single
 * `<canvas>` element, so the same rendering setup can be reused for the main window canvas and
 * for per-[ComposeSceneLayer] canvases (see `CMP-8359-plan.md`).
 *
 * [scene] must be assigned before the first [resize] call, since resizing triggers the initial
 * render.
 */
internal class WebComposeSceneRenderLoop(
    private val canvas: HTMLCanvasElement,
    coroutineContext: CoroutineContext,
) {
    lateinit var scene: ComposeScene

    val frameRecomposer = FrameRecomposer(coroutineContext, invalidate = { skiaLayer.needRender() })

    private val renderingScope = SingleComposeSceneRenderingScope { skiaLayer.needRender() }

    val invalidateLayout: () -> Unit = renderingScope::onSceneInvalidation
    val invalidateDraw: () -> Unit = renderingScope::onSceneInvalidation

    private val skiaLayer: SkiaLayer = SkiaLayer().apply {
        renderDelegate = SkikoRenderDelegate { canvas, _, _, nanoTime ->
            with(renderingScope) {
                scene.render(frameRecomposer, canvas.asComposeCanvas(), nanoTime)
            }
        }
    }

    fun resize(sizeInPx: IntSize) {
        // we need to scale canvas both via CSS styling and HTML attributes
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI
        canvas.width = sizeInPx.width
        canvas.height = sizeInPx.height

        skiaLayer.attachTo(canvas)
        scene.size = sizeInPx
        skiaLayer.needRender()
    }

    fun dispose() {
        frameRecomposer.close()
        skiaLayer.detach()
    }
}
