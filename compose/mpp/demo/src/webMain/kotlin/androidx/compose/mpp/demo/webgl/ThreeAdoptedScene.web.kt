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
    private val three: ThreeModule,
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

    var spin: Float = 1f
    var hue: Float = 0.55f
    var roughness: Float = 0.28f
    var metalness: Float = 0.62f
    var lightIntensity: Float = 3.4f
    var textureSize: IntSize = IntSize(1024, 640)
    var status: String = "waiting for the first frame"
        private set
    var adoptedTextureId: Int = -1
        private set
    var adoptedTextureCount: Int = 0
        private set

    private var threeObjects: ThreeObjects? = null
    private var framebuffer: WebGLFramebuffer? = null
    private var depthBuffer: WebGLRenderbuffer? = null
    private var renderTarget: ThreeRenderTarget? = null
    private var target: AdoptedGlTexture? = null

    /**
     * The previous frame's image, closed one frame late because a display list recorded during the
     * previous frame may still reference it.
     */
    private var retiredImage: Image? = null
    private var adoptedSize = IntSize.Zero
    private var angle = 0f
    private var failed = false

    /** The adopted texture to draw, or `null` until the first frame has been rendered. */
    val image: Image? get() = target?.image

    /**
     * Renders one frame with three.js into the adopted texture. Call once per frame from
     * `withFrameNanos`, passing the context Compose renders with.
     */
    fun renderFrame(context: DirectContext, deltaSeconds: Float) {
        if (failed) return

        val size = IntSize(
            width = textureSize.width.coerceIn(16, 4096),
            height = textureSize.height.coerceIn(16, 4096),
        )

        try {
            // Constructing the renderer queries capabilities and touches GL state, so it happens here
            // rather than at load time: this method always ends with DirectContext.resetAll(), which is
            // what lets Skia recover from any state three.js changed.
            val (renderer, knotScene) = threeObjects
                ?: ThreeObjects(
                    renderer = createThreeRenderer(three, canvas, gl),
                    knotScene = createKnotScene(three),
                ).also { threeObjects = it }

            val framebuffer = framebuffer
                ?: (gl.createFramebuffer() ?: error("gl.createFramebuffer() returned null"))
                    .also { this.framebuffer = it }

            retiredImage?.close()
            retiredImage = null

            var current = target
            if (current == null || adoptedSize != size) {
                val previous = current
                current = gl.adoptNewTexture(context, size)
                retiredImage = previous?.image
                adoptedSize = size
                adoptedTextureId = current.textureId
                adoptedTextureCount++
                attachToFramebuffer(framebuffer, current.texture, size)
                // The render target carries the viewport three.js renders with, and it is never
                // resized in place (that would make three dispose of our framebuffer), so a new
                // texture size means a new descriptor.
                renderTarget = createRenderTarget(three, size.width, size.height)
                knotScene.camera.aspect = size.width.toDouble() / size.height.toDouble()
                knotScene.camera.updateProjectionMatrix()
            }
            target = current
            val renderTarget = renderTarget ?: error("the render target was not created")

            angle += deltaSeconds * spin
            knotScene.knot.rotation.x = (angle * 0.6f).toDouble()
            knotScene.knot.rotation.y = angle.toDouble()
            knotScene.material.roughness = roughness.toDouble()
            knotScene.material.metalness = metalness.toDouble()
            knotScene.material.color.setHSL(hue.toDouble(), 0.72, 0.6)
            knotScene.keyLight.intensity = lightIntensity.toDouble()

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

            // And now the mirror image of resetState(): everything Skia cached is stale too.
            context.resetAll()

            status = "three.js renders into one adopted ${size.width}×${size.height} texture"
        } catch (throwable: Throwable) {
            failed = true
            status = "failed: ${throwable.message}"
        }
    }

    /**
     * [context] is only used to let Skia recover from the GL work done here, since three's own
     * disposal touches the shared context as well.
     */
    fun dispose(context: DirectContext?) {
        retiredImage?.close()
        retiredImage = null
        target?.image?.close()
        target = null
        adoptedSize = IntSize.Zero
        adoptedTextureId = -1
        // Only a descriptor pointing at our framebuffer: disposing it would make three.js delete a
        // framebuffer it never created, so it is simply dropped.
        renderTarget = null

        threeObjects?.let { (renderer, knotScene) ->
            disposeKnotScene(knotScene)
            renderer.dispose()
        }
        threeObjects = null

        framebuffer?.let { gl.deleteFramebuffer(it) }
        framebuffer = null
        depthBuffer?.let { gl.deleteRenderbuffer(it) }
        depthBuffer = null

        gl.bindFramebuffer(FRAMEBUFFER, null)
        context?.resetAll()
    }

    /**
     * Attaches the adopted texture as color attachment 0, plus a depth buffer sized to match, and
     * verifies that three.js will be able to render into the result.
     */
    private fun attachToFramebuffer(
        framebuffer: WebGLFramebuffer,
        texture: WebGLTexture,
        size: IntSize,
    ) {
        val depthBuffer = depthBuffer
            ?: (gl.createRenderbuffer() ?: error("gl.createRenderbuffer() returned null"))
                .also { this.depthBuffer = it }

        gl.bindRenderbuffer(RENDERBUFFER, depthBuffer)
        gl.renderbufferStorage(RENDERBUFFER, DEPTH_COMPONENT16, size.width, size.height)
        gl.bindRenderbuffer(RENDERBUFFER, null)

        gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
        gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, texture, 0)
        gl.framebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, depthBuffer)
        check(gl.checkFramebufferStatus(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE) {
            "the adopted texture is not a complete framebuffer attachment"
        }
        gl.bindFramebuffer(FRAMEBUFFER, null)
    }

    private data class ThreeObjects(
        val renderer: ThreeRenderer,
        val knotScene: ThreeKnotScene,
    )
}
