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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.mpp.demo.webgl

import androidx.compose.ui.unit.IntSize
import kotlin.js.ExperimentalWasmJsInterop
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderbuffer
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_ATTACHMENT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_COMPONENT16
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_COMPLETE
import org.khronos.webgl.WebGLRenderingContext.Companion.RENDERBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLTexture
import org.w3c.dom.HTMLCanvasElement

internal class ThreeJsAdoptedScene private constructor(
    private val gl: WebGLRenderingContext,
    private val threeJsModule: ThreeModule,
    private val canvas: HTMLCanvasElement,
) {
    companion object {
        /**
         * Loads three.js and binds it to the WebGL2 context managed by Skiko
         */
        suspend fun createOrNull(canvas: HTMLCanvasElement): ThreeJsAdoptedScene? {
            val gl = webGl2ContextOf(canvas) ?: return null
            val three = loadThreeModule() ?: return null
            return ThreeJsAdoptedScene(gl, three, canvas)
        }
    }

    // angle is the main dynaminc state in this demo, it's updated every frame
    private var knotAngle = 0.0

    // other knot properties
    var hue: Double = 0.85
    var spin: Double = 2.0
    var roughness: Double = 0.3
    var metalness: Double = 0.6
    var opacity: Double = 0.8
    var lightIntensity: Double = 3.0
    var textureSize: IntSize = IntSize(1024, 640)
    var status: String = "waiting for the first frame"
        private set
    var adoptedTextureId: Int = -1
        private set
    var adoptedTextureCount: Int = 0
        private set

    private var threeJsObjects: ThreeJsObjects? = null
    private var framebuffer: WebGLFramebuffer? = null
    private var renderedBuffer: WebGLRenderbuffer? = null
    private var renderTarget: ThreeRenderTarget? = null
    private var target: AdoptedGlTexture? = null
    private var adoptedSize = IntSize.Zero
    private var failed = false


    /**
     * Skiko Image which adopted the WebGL texture
     */
    val imageToRender: Image? get() = target?.image

    /**
     * Renders one frame with three.js into the adopted texture. Call once per frame.
     */
    fun renderFrame(context: DirectContext, deltaSeconds: Float) {
        if (failed) return

        val size = IntSize(
            width = textureSize.width.coerceIn(16, 4096),
            height = textureSize.height.coerceIn(16, 4096),
        )

        try {
            val (renderer, knotScene) = ensureThreeJsObjects()

            ensureAdoptedTexture(context, size, knotScene)
            knotAngle += deltaSeconds * spin
            knotScene.updateValues(
                angle = knotAngle,
                roughness = roughness,
                metalness = metalness,
                opacity = opacity,
                lightIntensity = lightIntensity,
                hue = hue
            )

            renderThreeFrame(renderer, knotScene, framebuffer = ensureFramebuffer())
            context.resetAll()

            status = "three.js renders into one adopted ${size.width}×${size.height} texture"
        } catch (throwable: Throwable) {
            failed = true
            status = "failed: ${throwable.message}"
        }
    }

    private fun ThreeKnotScene.updateValues(
        angle: Double,
        roughness: Double,
        metalness: Double,
        opacity: Double,
        lightIntensity: Double,
        hue: Double,
    ) {
        knot.rotation.x = (angle * 0.6f)
        knot.rotation.y = angle
        material.roughness = roughness
        material.metalness = metalness
        material.opacity = opacity
        material.color.setHSL(hue, 0.75, 0.6)
        keyLight.intensity = lightIntensity
    }

    private fun ensureThreeJsObjects(): ThreeJsObjects = threeJsObjects
        ?: ThreeJsObjects(
            renderer = createThreeRenderer(threeJsModule, canvas, gl),
            knotScene = createKnotScene(threeJsModule),
        ).also { threeJsObjects = it }

    /**
     * Returns the adopted texture to draw,
     * creating a new one if there is none yet or the texture size changed.
     */
    private fun ensureAdoptedTexture(
        context: DirectContext,
        size: IntSize,
        knotScene: ThreeKnotScene,
    ): AdoptedGlTexture {
        val current = target
        if (current != null && adoptedSize == size) return current

        current?.image?.close() // after resize

        val adopted = gl.adoptNewTexture(context, size)
        target = adopted
        adoptedSize = size
        adoptedTextureId = adopted.textureId
        adoptedTextureCount++

        adopted.texture.attachToFramebuffer(ensureFramebuffer(), size)
        renderTarget = createRenderTarget(threeJsModule, size.width, size.height)
        knotScene.camera.aspect = size.width.toDouble() / size.height.toDouble()
        knotScene.camera.updateProjectionMatrix()
        return adopted
    }

    /**
     * Renders one three.js frame into the adopted texture.
     */
    private fun renderThreeFrame(
        renderer: ThreeRenderer,
        knotScene: ThreeKnotScene,
        framebuffer: WebGLFramebuffer,
    ) {
        val renderTarget = renderTarget ?: error("the render target was not created")
        // Skia rendered the previous frame through this very context, so everything three.js
        // believes about the GL state is stale.
        renderer.resetState()
        // Our framebuffer, with the texture Skia adopted attached to it.
        renderer.setRenderTargetFramebuffer(renderTarget, framebuffer)
        renderer.setRenderTarget(renderTarget)
        renderer.render(knotScene.scene, knotScene.camera)
        // Hand the default framebuffer — the one Skia renders Compose into — back.
        renderer.setRenderTarget(null)
        gl.bindFramebuffer(FRAMEBUFFER, null)
    }

    private fun ensureFramebuffer(): WebGLFramebuffer = framebuffer
        ?: (gl.createFramebuffer() ?: error("gl.createFramebuffer() returned null"))
            .also { this.framebuffer = it }

    /**
     * [context] is only used to let Skia recover from the GL work done here, since three's own
     * disposal touches the shared context as well.
     */
    fun dispose(context: DirectContext?) {
        target?.image?.close()
        target = null
        adoptedSize = IntSize.Zero
        adoptedTextureId = -1
        renderTarget = null

        threeJsObjects?.let { (renderer, knotScene) ->
            disposeKnotScene(knotScene)
            renderer.dispose()
        }
        threeJsObjects = null

        framebuffer?.let { gl.deleteFramebuffer(it) }
        framebuffer = null
        renderedBuffer?.let { gl.deleteRenderbuffer(it) }
        renderedBuffer = null

        gl.bindFramebuffer(FRAMEBUFFER, null)
        context?.resetAll()
    }

    private fun ensureRendererBuffer(): WebGLRenderbuffer {
        return renderedBuffer ?: (gl.createRenderbuffer() ?: error("gl.createRenderbuffer() returned null"))
                .also { this.renderedBuffer = it }
    }

    private fun WebGLTexture.attachToFramebuffer(
        framebuffer: WebGLFramebuffer,
        size: IntSize,
    ) {
        val renderbuffer = ensureRendererBuffer()
        gl.bindRenderbuffer(RENDERBUFFER, renderbuffer)
        gl.renderbufferStorage(RENDERBUFFER, DEPTH_COMPONENT16, size.width, size.height)
        gl.bindRenderbuffer(RENDERBUFFER, null)

        gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
        gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, this, 0)
        gl.framebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, renderbuffer)
        check(gl.checkFramebufferStatus(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE) {
            "the adopted texture is not a complete framebuffer attachment"
        }
        gl.bindFramebuffer(FRAMEBUFFER, null)
    }

    private data class ThreeJsObjects(
        val renderer: ThreeRenderer,
        val knotScene: ThreeKnotScene,
    )
}
