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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.webgl.WebGLRenderTarget
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.webgl.rememberWebGLRenderTarget
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.BLEND
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.SCISSOR_TEST
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLES
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLUniformLocation

/** Two independent [WebGLRenderTarget]s rendered with plain WebGL2 — no third-party library. */
val PlainWebGlScreen = Screen.Example("Plain WebGL") { PlainWebGlDemo() }

@Composable
private fun PlainWebGlDemo() {
    var isAnimating by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RotatingTriangle(isAnimating)
            ColorPulse(isAnimating)
        }
        Toggle("animate", isAnimating) { isAnimating = it }
    }
}

@Composable
private fun RotatingTriangle(isAnimating: Boolean) {
    val renderTarget = rememberWebGLRenderTarget(IntSize(512, 320))!!
    val triangle = remember(renderTarget) { TriangleRenderer(renderTarget) }

    DisposableEffect(triangle, renderTarget) {
        onDispose { triangle.dispose(renderTarget) }
    }

    LaunchedEffect(renderTarget, isAnimating) {
        while (isAnimating) {
            withFrameNanos { frameTimeNanos ->
                triangle.render(frameTimeNanos)
            }
        }
    }

    LabelledContent("512×320 texture\na shader-drawn triangle") {
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ColorPulse(isAnimating: Boolean) {
    val webGLRenderTarget = rememberWebGLRenderTarget(IntSize(64, 64))

    if (webGLRenderTarget == null) {
        Text("webGLRenderTarget is null")
        return
    }

    val pulse = remember(webGLRenderTarget) { PulseRenderer(webGLRenderTarget) }

    LaunchedEffect(webGLRenderTarget, isAnimating) {
        while (isAnimating) {
            withFrameNanos { frameTimeNanos ->
                pulse.render(frameTimeNanos)
            }
        }
    }

    LabelledContent("64×64 texture\nnothing but a pulsing clear color") {
        Image(
            painter = webGLRenderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LabelledContent(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E1B33), Color(0xFF3A1250)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Compose",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.h5,
            )
            content()
        }
        Text(
            caption,
            modifier = Modifier.padding(top = 8.dp).width(200.dp),
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Draws a spinning, vertex-colored triangle. The geometry lives in the vertex shader, so there is
 * no buffer and no attribute to set up: the whole renderer is one program and two uniforms.
 */
private class TriangleRenderer(private val target: WebGLRenderTarget) {
    private val gl = target.webGLContext
    private val program: WebGLProgram =
        gl.createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
    private val angleUniform: WebGLUniformLocation? = gl.getUniformLocation(program, "angle")
    private val aspectUniform: WebGLUniformLocation? = gl.getUniformLocation(program, "aspect")
    private var angle = 0f
    private var previousFrameTimeNanos = 0L

    fun render(frameTimeNanos: Long) {
        target.render { renderFrame(frameTimeNanos) }
    }

    private fun renderFrame(frameTimeNanos: Long): Unit =
        with(target) {
            val deltaNanos = if (previousFrameTimeNanos == 0L) {
                0L
            } else {
                frameTimeNanos - previousFrameTimeNanos
            }
            previousFrameTimeNanos = frameTimeNanos
            angle += deltaNanos / 1_000_000_000f * 0.9f

            // Skia rendered the previous frame through this very context and left its own state
            // behind, so everything this frame depends on is set here, every frame. Leaving the
            // scissor test alone in particular would let Skia's last scissor rect clip both the
            // clear and the draw below.
            webGLContext.viewport(0, 0, size.width, size.height)
            webGLContext.disable(SCISSOR_TEST)
            webGLContext.disable(DEPTH_TEST)
            webGLContext.disable(BLEND)

            // Transparent premultiplied clear: the Compose content under the texture shows through.
            webGLContext.clearColor(0f, 0f, 0f, 0f)
            webGLContext.clear(COLOR_BUFFER_BIT)

            webGLContext.useProgram(program)
            webGLContext.uniform1f(angleUniform, angle)
            webGLContext.uniform1f(aspectUniform, size.width.toFloat() / size.height.toFloat())
            webGLContext.drawArrays(TRIANGLES, 0, 3)
        }

    /**
     * Releases the program
     */
    fun dispose(renderTarget: WebGLRenderTarget) {
        gl.deleteProgram(program)
        renderTarget.markGLStateStale()
    }

    companion object {
        private val VERTEX_SHADER_SOURCE = """
            #version 300 es
            const vec2 positions[3] = vec2[3](vec2(0.0, 0.85), vec2(-0.75, -0.6), vec2(0.75, -0.6));
            const vec3 colors[3] = vec3[3](
                vec3(1.0, 0.35, 0.45),
                vec3(0.3, 0.95, 0.6),
                vec3(0.45, 0.55, 1.0)
            );
            uniform float angle;
            uniform float aspect;
            out vec3 vertexColor;
            void main() {
                vec2 position = positions[gl_VertexID];
                float s = sin(angle);
                float c = cos(angle);
                vec2 rotated = vec2(position.x * c - position.y * s, position.x * s + position.y * c);
                gl_Position = vec4(rotated.x / aspect, rotated.y, 0.0, 1.0);
                vertexColor = colors[gl_VertexID];
            }
        """.trimIndent()

        private val FRAGMENT_SHADER_SOURCE = """
            #version 300 es
            precision mediump float;
            in vec3 vertexColor;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(vertexColor, 1.0);
            }
        """.trimIndent()

    }
}

private fun WebGLRenderingContext.createProgram(
    vertexSource: String,
    fragmentSource: String,
): WebGLProgram {
    val program = createProgram() ?: error("gl.createProgram() returned null")
    for ((type, source) in listOf(VERTEX_SHADER to vertexSource, FRAGMENT_SHADER to fragmentSource)) {
        val shader = createShader(type) ?: error("gl.createShader() returned null")
        shaderSource(shader, source)
        compileShader(shader)
        val log = getShaderInfoLog(shader)
        check(log.isNullOrBlank()) { "shader compilation reported: $log" }
        attachShader(program, shader)
        // The program keeps the shader alive until the program itself is deleted.
        deleteShader(shader)
    }
    linkProgram(program)
    val log = getProgramInfoLog(program)
    check(log.isNullOrBlank()) { "program linking reported: $log" }
    return program
}

/** Clears the texture to a pulsing color. No shaders, no resources, nothing to dispose. */
private class PulseRenderer(private val target: WebGLRenderTarget) {
    private var phase = 0f
    private var previousFrameTimeNanos = 0L

    fun render(frameTimeNanos: Long) {
        target.render { renderFrame(frameTimeNanos) }
    }

    private fun renderFrame(frameTimeNanos: Long): Unit =
        with(target) {
            val deltaNanos = if (previousFrameTimeNanos == 0L) {
                0L
            } else {
                frameTimeNanos - previousFrameTimeNanos
            }
            previousFrameTimeNanos = frameTimeNanos
            phase += deltaNanos / 1_000_000_000f * 1.5f
            val level = sin(phase) * 0.5f + 0.5f
            webGLContext.disable(SCISSOR_TEST)
            webGLContext.clearColor(0.15f * level, 0.55f * level, level, level)
            webGLContext.clear(COLOR_BUFFER_BIT)
        }
}
