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
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.toBoolean
import kotlin.js.unsafeCast
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.BLEND
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.COMPILE_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.CULL_FACE
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.FLOAT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_COMPLETE
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.LINK_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE_MINUS_SRC_ALPHA
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.SCISSOR_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.STATIC_DRAW
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE0
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLTexture
import org.khronos.webgl.WebGLUniformLocation
import org.khronos.webgl.set
import org.w3c.dom.HTMLCanvasElement

/** Vertices of a viewport-filling triangle strip, in clip space. */
private val QUAD_VERTICES = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

/**
 * Renders a hand-written WebGL scene into a texture and lets Compose draw that texture without ever
 * copying pixels through the CPU.
 *
 * The whole thing rests on three facts:
 * 1. Asking the `<canvas>` Compose renders into for a `"webgl2"` context returns the very same
 *    `WebGL2RenderingContext` Skiko created — a canvas never hands out a second context. WebGL has
 *    no share groups, so this is the only context whose textures Skia is allowed to touch.
 * 2. [pushTexture] publishes our `WebGLTexture` in Emscripten's texture table, which is what turns
 *    it into the numeric id Skia's GL API speaks. Skiko only exposes that helper to Kotlin/Wasm, so
 *    this demo re-implements it for both web targets in `WebGlTextureRegistration.web.kt`.
 * 3. An adopted [Image] can only be drawn by the [DirectContext] that adopted it, so the context
 *    Compose renders with has to be the one passed to [renderFrame].
 *
 * [renderFrame] is meant to be called once per frame from a `withFrameNanos` callback, which runs
 * before Compose measures, lays out and draws. Drawing sites then only draw [image]; they never
 * touch GL. That split matters: the canvas a composable draws on is usually a graphics layer's
 * display-list recorder, so GL work cannot happen there.
 */
internal class AdoptedGlScene private constructor(private val gl: WebGLRenderingContext) {

    companion object {
        /**
         * Returns a scene rendering into [canvas]'s WebGL2 context — the context Skiko uses — or
         * `null` if that context cannot be obtained.
         */
        fun createOrNull(canvas: HTMLCanvasElement): AdoptedGlScene? =
            webGl2ContextOf(canvas)?.let(::AdoptedGlScene)
    }

    /** How much the plasma field is domain-warped. */
    var warp: Float = 0.45f

    /** Offset into the cosine palette. */
    var hue: Float = 0.1f

    /** Brightness of the spinning quad. */
    var glow: Float = 0.85f

    /**
     * When `true`, a fresh texture is allocated, registered and adopted on every frame and the
     * previous [Image] is closed — which is Skia's cue to delete the previous GL texture through
     * Emscripten. When `false` (the interesting mode) one texture is adopted once and then
     * re-rendered in place forever.
     */
    var recreateTextureEveryFrame: Boolean = false

    /** Resolution of the offscreen texture. Changing it adopts a new texture of that size. */
    var textureSize: IntSize = IntSize(1024, 640)

    /** Human readable state, surfaced by the demo UI. */
    var status: String = "waiting for the first frame"
        private set

    /** Emscripten id of the texture Skia currently owns, or `-1`. */
    var adoptedTextureId: Int = -1
        private set

    /** How many textures have been handed over to Skia so far. */
    var adoptedTextureCount: Int = 0
        private set

    private var plasmaProgram: GlProgram? = null
    private var quadProgram: GlProgram? = null
    private var vertexBuffer: WebGLBuffer? = null
    private var framebuffer: WebGLFramebuffer? = null

    /** A texture the demo keeps ownership of; only our own shader ever samples it. */
    private var patternTexture: WebGLTexture? = null

    private var target: AdoptedGlTexture? = null

    /**
     * The previous frame's image in "new texture every frame" mode. It is closed one frame late,
     * because a display list recorded during the previous frame may still reference it.
     */
    private var retiredImage: Image? = null
    private var adoptedSize = IntSize.Zero
    private var failed = false

    /** The adopted texture to draw, or `null` until the first frame has been rendered. */
    val image: Image? get() = target?.image

    /**
     * Renders one frame of the WebGL scene into the adopted texture, adopting a new texture first if
     * needed. Call once per frame from `withFrameNanos`, passing the context Compose renders with.
     */
    fun renderFrame(context: DirectContext, timeSeconds: Float) {
        if (failed) return

        val size = IntSize(
            width = textureSize.width.coerceIn(16, 4096),
            height = textureSize.height.coerceIn(16, 4096),
        )

        try {
            createGlObjectsIfNeeded()

            retiredImage?.close()
            retiredImage = null

            val previous = target
            val current = when {
                previous == null || adoptedSize != size || recreateTextureEveryFrame -> {
                    gl.adoptNewTexture(context, size).also {
                        retiredImage = previous?.image
                        adoptedSize = size
                        adoptedTextureId = it.textureId
                        adoptedTextureCount++
                    }
                }
                else -> previous
            }
            target = current

            renderSceneInto(current, timeSeconds, size)

            // Everything above went behind Skia's back: the framebuffer, program, buffer and
            // texture bindings it had cached are stale now. Without this, Compose renders garbage.
            context.resetAll()

            status = if (recreateTextureEveryFrame) {
                "adopting a new ${size.width}×${size.height} texture every frame"
            } else {
                "one adopted ${size.width}×${size.height} texture, re-rendered in place"
            }
        } catch (throwable: Throwable) {
            failed = true
            status = "failed: ${throwable.message}"
        }
    }

    /**
     * Demonstrates the non-owning half of the API. [patternTexture] stays ours, so after publishing
     * it in Emscripten's table the id has to be taken back out by hand. [unregisterTexture] only
     * drops that id: the texture keeps living, and the spinning quad keeps sampling it.
     */
    fun registrationRoundTrip(): String {
        val texture = patternTexture ?: return "GL objects are not created yet"
        val id = pushTexture(texture)
        unregisterTexture(id)
        return "pushTexture(pattern) returned id $id, released again with unregisterTexture($id) — " +
            "the texture itself is untouched and still being sampled"
    }

    fun dispose() {
        retiredImage?.close()
        retiredImage = null
        target?.image?.close()
        target = null
        adoptedSize = IntSize.Zero
        adoptedTextureId = -1
        releaseGlObjects()
    }

    private fun renderSceneInto(target: AdoptedGlTexture, timeSeconds: Float, size: IntSize) {
        val plasma = plasmaProgram ?: error("shader programs are not compiled")
        val quad = quadProgram ?: error("shader programs are not compiled")
        val aspect = size.width.toFloat() / size.height.toFloat()

        gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
        gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, target.texture, 0)
        check(gl.checkFramebufferStatus(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE) {
            "the adopted texture is not a complete framebuffer attachment"
        }

        gl.viewport(0, 0, size.width, size.height)
        gl.disable(DEPTH_TEST)
        gl.disable(SCISSOR_TEST)
        gl.disable(CULL_FACE)
        gl.clearColor(0f, 0f, 0f, 0f)
        gl.clear(COLOR_BUFFER_BIT)
        gl.enable(BLEND)
        gl.blendFunc(ONE, ONE_MINUS_SRC_ALPHA) // premultiplied source
        gl.bindBuffer(ARRAY_BUFFER, vertexBuffer)

        gl.useProgram(plasma.program)
        bindQuadVertices(plasma.positionAttribute)
        gl.uniform1f(plasma.uniform("uTime"), timeSeconds)
        gl.uniform1f(plasma.uniform("uWarp"), warp)
        gl.uniform1f(plasma.uniform("uHue"), hue)
        gl.uniform1f(plasma.uniform("uAspect"), aspect)
        gl.drawArrays(TRIANGLE_STRIP, 0, 4)

        val angle = timeSeconds * 0.8f
        gl.useProgram(quad.program)
        bindQuadVertices(quad.positionAttribute)
        gl.uniform2f(quad.uniform("uRotation"), cos(angle), sin(angle))
        gl.uniform1f(quad.uniform("uScale"), 0.46f + 0.04f * sin(timeSeconds * 1.7f))
        gl.uniform1f(quad.uniform("uAspect"), aspect)
        gl.uniform1f(quad.uniform("uTime"), timeSeconds)
        gl.uniform1f(quad.uniform("uGlow"), glow)
        gl.activeTexture(TEXTURE0)
        gl.bindTexture(TEXTURE_2D, patternTexture)
        gl.uniform1i(quad.uniform("uPattern"), 0)
        gl.drawArrays(TRIANGLE_STRIP, 0, 4)

        // Hand the default framebuffer — the one Skia renders Compose into — back.
        gl.bindFramebuffer(FRAMEBUFFER, null)
    }

    private fun bindQuadVertices(attribute: Int) {
        gl.enableVertexAttribArray(attribute)
        gl.vertexAttribPointer(attribute, 2, FLOAT, false, 0, 0)
    }

    private fun createGlObjectsIfNeeded() {
        if (framebuffer != null) return

        framebuffer = gl.createFramebuffer() ?: error("gl.createFramebuffer() returned null")
        plasmaProgram = GlProgram(gl, SCENE_VERTEX_SHADER, PLASMA_FRAGMENT_SHADER)
        quadProgram = GlProgram(gl, SPINNING_QUAD_VERTEX_SHADER, SPINNING_QUAD_FRAGMENT_SHADER)

        val vertices = Float32Array(QUAD_VERTICES.size)
        QUAD_VERTICES.forEachIndexed { index, value -> vertices[index] = value }
        vertexBuffer = (gl.createBuffer() ?: error("gl.createBuffer() returned null")).also {
            gl.bindBuffer(ARRAY_BUFFER, it)
            gl.bufferData(ARRAY_BUFFER, vertices, STATIC_DRAW)
        }

        patternTexture = createPatternTexture()
    }

    private fun releaseGlObjects() {
        framebuffer?.let { gl.deleteFramebuffer(it) }
        framebuffer = null
        vertexBuffer?.let { gl.deleteBuffer(it) }
        vertexBuffer = null
        patternTexture?.let { gl.deleteTexture(it) }
        patternTexture = null
        plasmaProgram?.dispose()
        plasmaProgram = null
        quadProgram?.dispose()
        quadProgram = null
    }

    /** A small procedural texture the demo keeps for itself, sampled by the spinning quad. */
    private fun createPatternTexture(): WebGLTexture {
        val side = 64
        val pixels = Uint8Array(side * side * 4)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val checker = if (((x / 8) + (y / 8)) % 2 == 0) 1f else 0.55f
                val gradient = y.toFloat() / (side - 1)
                val offset = (y * side + x) * 4
                pixels[offset] = (255 * checker * (0.35f + 0.65f * gradient)).toInt().toByte()
                pixels[offset + 1] = (255 * checker * (0.75f - 0.35f * gradient)).toInt().toByte()
                pixels[offset + 2] = (255 * checker).toInt().toByte()
                pixels[offset + 3] = 0xFF.toByte()
            }
        }

        val texture = gl.createTexture() ?: error("gl.createTexture() returned null")
        gl.bindTexture(TEXTURE_2D, texture)
        gl.texImage2D(TEXTURE_2D, 0, RGBA, side, side, 0, RGBA, UNSIGNED_BYTE, pixels)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
        gl.bindTexture(TEXTURE_2D, null)
        return texture
    }
}

private class GlProgram(
    private val gl: WebGLRenderingContext,
    vertexShaderSource: String,
    fragmentShaderSource: String,
) {
    private val vertexShader = gl.createCompiledShader(VERTEX_SHADER, vertexShaderSource)
    private val fragmentShader = gl.createCompiledShader(FRAGMENT_SHADER, fragmentShaderSource)
    private val uniforms = mutableMapOf<String, WebGLUniformLocation?>()

    val program: WebGLProgram = (gl.createProgram() ?: error("gl.createProgram() returned null"))
        .also { program ->
            gl.attachShader(program, vertexShader)
            gl.attachShader(program, fragmentShader)
            gl.linkProgram(program)
            check(gl.getProgramParameter(program, LINK_STATUS).isTrue()) {
                "program linking failed: ${gl.getProgramInfoLog(program)}"
            }
        }

    val positionAttribute: Int = gl.getAttribLocation(program, "aPosition")

    fun uniform(name: String): WebGLUniformLocation? =
        uniforms.getOrPut(name) { gl.getUniformLocation(program, name) }

    fun dispose() {
        gl.deleteProgram(program)
        gl.deleteShader(vertexShader)
        gl.deleteShader(fragmentShader)
    }
}

private fun WebGLRenderingContext.createCompiledShader(type: Int, source: String): WebGLShader {
    val shader = createShader(type) ?: error("gl.createShader() returned null")
    shaderSource(shader, source)
    compileShader(shader)
    check(getShaderParameter(shader, COMPILE_STATUS).isTrue()) {
        "shader compilation failed: ${getShaderInfoLog(shader)}"
    }
    return shader
}

private fun JsAny?.isTrue(): Boolean = this?.unsafeCast<JsBoolean>()?.toBoolean() == true

private const val SCENE_VERTEX_SHADER = """
    attribute vec2 aPosition;
    varying vec2 vUv;
    void main() {
        vUv = aPosition * 0.5 + 0.5;
        gl_Position = vec4(aPosition, 0.0, 1.0);
    }
"""

private const val PLASMA_FRAGMENT_SHADER = """
    precision mediump float;
    varying vec2 vUv;
    uniform float uTime;
    uniform float uWarp;
    uniform float uHue;
    uniform float uAspect;

    vec3 palette(float t) {
        return 0.5 + 0.5 * cos(6.28318 * (vec3(0.0, 0.33, 0.67) + t));
    }

    void main() {
        vec2 p = (vUv * 2.0 - 1.0) * vec2(uAspect, 1.0);
        for (int i = 0; i < 3; i++) {
            p += uWarp * 0.35 * vec2(sin(p.y * 3.0 + uTime), cos(p.x * 3.0 - uTime * 0.7));
        }
        float field = sin(p.x * 3.0 + uTime)
            + sin(p.y * 3.5 - uTime * 0.8)
            + sin(length(p) * 5.0 - uTime * 1.3);
        vec3 color = palette(field * 0.15 + uHue);
        float alpha = smoothstep(1.35, 0.2, length(vUv * 2.0 - 1.0));
        gl_FragColor = vec4(color * alpha, alpha);
    }
"""

private const val SPINNING_QUAD_VERTEX_SHADER = """
    attribute vec2 aPosition;
    uniform vec2 uRotation;
    uniform float uScale;
    uniform float uAspect;
    varying vec2 vUv;
    void main() {
        vUv = aPosition * 0.5 + 0.5;
        vec2 rotated = vec2(
            aPosition.x * uRotation.x - aPosition.y * uRotation.y,
            aPosition.x * uRotation.y + aPosition.y * uRotation.x
        ) * uScale;
        gl_Position = vec4(rotated.x / uAspect, rotated.y, 0.0, 1.0);
    }
"""

private const val SPINNING_QUAD_FRAGMENT_SHADER = """
    precision mediump float;
    varying vec2 vUv;
    uniform sampler2D uPattern;
    uniform float uTime;
    uniform float uGlow;

    void main() {
        vec4 pattern = texture2D(uPattern, vUv + vec2(uTime * 0.04, uTime * 0.02));
        float mask = smoothstep(0.5, 0.36, length(vUv - 0.5));
        float alpha = mask * 0.9;
        gl_FragColor = vec4(pattern.rgb * uGlow * alpha, alpha);
    }
"""
