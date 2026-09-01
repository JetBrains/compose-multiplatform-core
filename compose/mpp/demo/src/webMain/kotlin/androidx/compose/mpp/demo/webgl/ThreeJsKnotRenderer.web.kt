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

package androidx.compose.mpp.demo.webgl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.webgl.WebGLRenderTarget

/**
 * A three.js renderer of a lit torus knot, rendering into whatever framebuffer Compose hands it.
 *
 * Everything here is three.js-specific; nothing here knows about Skia, textures or Compose drawing:
 * that is what [WebGLRenderTarget] takes care of.
 */
internal class ThreeJsKnotRenderer
private constructor(
    private val three: ThreeModule,
    private val webGLRenderTarget: WebGLRenderTarget,
) {
    companion object {
        /** Loads three.js, or returns `null` when the module is unavailable. */
        suspend fun createOrNull(webGLRenderTarget: WebGLRenderTarget): ThreeJsKnotRenderer? =
            loadThreeModule()?.let { ThreeJsKnotRenderer(it, webGLRenderTarget) }
    }

    // The angle is the main dynamic state in this demo, it's updated every frame.
    private var knotAngle = 0.0

    // Other knot properties.
    var hue: Double = 0.85
    var spin: Double = 2.0
    var roughness: Double = 0.3
    var metalness: Double = 0.6
    var opacity: Double = 0.8
    var lightIntensity: Double = 3.0

    var status: String = "waiting for the first frame"
        private set

    private var renderer: ThreeRenderer? = createThreeRenderer(three, webGLRenderTarget.htmlCanvas, webGLRenderTarget.webGLContext)
    private var knotScene: ThreeKnotScene? = createKnotScene(three)
    private var renderTarget: ThreeRenderTarget? = null
    private var targetGeneration = 0
    private var failed = false
    private var previousFrameTimeNanos = 0L

    fun renderFrame(frameTimeNanos: Long) {
        webGLRenderTarget.render { renderFrameInTarget(frameTimeNanos) }
    }

    private fun renderFrameInTarget(frameTimeNanos: Long): Unit =
        with(webGLRenderTarget) {
            if (failed) return
            val deltaNanos =
                if (previousFrameTimeNanos == 0L) 0L
                else frameTimeNanos - previousFrameTimeNanos
            previousFrameTimeNanos = frameTimeNanos
            try {
                val renderer = renderer ?: error("the renderer was disposed")
                val knotScene = knotScene ?: error("the scene was disposed")
                val renderTarget = ensureRenderTarget(knotScene)

                knotAngle += deltaNanos / 1_000_000_000.0 * spin
                knotScene.updateValues()

                // Skia rendered the previous frame through this very context, so everything
                // three.js
                // believes about the GL state is stale.
                renderer.resetState()
                // Our framebuffer, with the texture Skia adopted attached to it.
                renderer.setRenderTargetFramebuffer(renderTarget, framebuffer)
                renderer.setRenderTarget(renderTarget)
                renderer.render(knotScene.scene, knotScene.camera)
                // Hand the default framebuffer — the one Skia renders Compose into — back.
                renderer.setRenderTarget(null)

                status = "three.js renders into one adopted ${size.width}×${size.height} texture"
            } catch (throwable: Throwable) {
                failed = true
                status = "failed: ${throwable.message}"
            }
        }

    /**
     * The render target is only a descriptor for the framebuffer Compose owns, so it has to be
     * replaced whenever Compose recreated that framebuffer.
     */
    private fun WebGLRenderTarget.ensureRenderTarget(knotScene: ThreeKnotScene): ThreeRenderTarget {
        val current = renderTarget
        if (current != null && targetGeneration == generation) return current

        targetGeneration = generation
        knotScene.camera.aspect = size.width.toDouble() / size.height.toDouble()
        knotScene.camera.updateProjectionMatrix()
        return createRenderTarget(three, size.width, size.height).also { renderTarget = it }
    }

    private fun ThreeKnotScene.updateValues() {
        knot.rotation.x = knotAngle * 0.6
        knot.rotation.y = knotAngle
        material.roughness = roughness
        material.metalness = metalness
        material.opacity = opacity
        material.color.setHSL(hue, 0.75, 0.6)
        keyLight.intensity = lightIntensity
    }

    /**
     * Releases three's own GL objects. Since that touches the context Compose renders through,
     * [WebGLRenderTarget.markGLStateStale] has to be called afterwards.
     */
    fun dispose(renderTarget: WebGLRenderTarget?) {
        knotScene?.let(::disposeKnotScene)
        knotScene = null
        renderer?.dispose()
        renderer = null
        this@ThreeJsKnotRenderer.renderTarget = null
        targetGeneration = 0
        renderTarget?.markGLStateStale()
    }
}
