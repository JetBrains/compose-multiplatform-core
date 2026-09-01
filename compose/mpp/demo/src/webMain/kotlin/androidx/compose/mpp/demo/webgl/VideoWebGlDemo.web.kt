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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.webgl.WebGLRenderTarget
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.webgl.rememberWebGLRenderTarget
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.khronos.webgl.WebGLTexture
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLUniformLocation
import kotlin.js.JsAny
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val VIDEO_URL =
    "http://docs.evostream.com/sample_content/assets/bunny.mp4"

val VideoWebGlScreen = Screen.Example("WebGL video") { VideoWebGlDemo() }

@Composable
private fun VideoWebGlDemo() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HTML video uploaded to a WebGL texture and drawn by Compose")
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VideoPlayer(modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(16f / 9f))
        }
    }
}

@Composable
private fun VideoPlayer(
    modifier: Modifier,
) {
    val renderTarget = rememberWebGLRenderTarget(IntSize(1280, 720)) ?: return
    val videoRenderer = remember(renderTarget) {
        VideoTextureRenderer(renderTarget).also { it.init(VIDEO_URL) }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var pointerActivity by remember { mutableStateOf(0) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Move) pointerActivity++
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!isPlaying || controlsVisible) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
            )
            Button(
                onClick = {
                    if (isPlaying) {
                        videoRenderer.pause()
                        isPlaying = false
                    } else {
                        videoRenderer.play()
                        isPlaying = true
                    }
                },
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }
    }

    LaunchedEffect(pointerActivity, isPlaying) {
        controlsVisible = true
        if (isPlaying) {
            delay(1.seconds)
            controlsVisible = false
        }
    }

    DisposableEffect(videoRenderer, renderTarget) {
        onDispose {
            videoRenderer.stop()
            videoRenderer.dispose()
            renderTarget.markGLStateStale()
        }
    }

    LaunchedEffect(videoRenderer, renderTarget) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                videoRenderer.renderFrame(frameTimeNanos)
            }
        }
    }
}

private class VideoTextureRenderer(private val target: WebGLRenderTarget) {
    private val gl = target.webGLContext
    private val program: WebGLProgram =
        gl.createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
    private val videoUniform: WebGLUniformLocation? = gl.getUniformLocation(program, "videoTexture")
    private var video: JsAny? = null
    private var texture: WebGLTexture? = null
    private var textureAllocated = false

    fun init(url: String) {
        video = createVideo(url)
        texture = gl.createTexture() ?: error("gl.createTexture() returned null")
        gl.bindTexture(TEXTURE_2D, texture)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
        gl.bindTexture(TEXTURE_2D, null)
    }

    fun renderFrame(frameTimeNanos: Long) {
        target.render { renderFrameInTarget(frameTimeNanos) }
    }

    private fun renderFrameInTarget(frameTimeNanos: Long) {
        val video = video ?: return
        val texture = texture ?: return
        if (!textureAllocated) {
            if (!allocateVideoTexture(gl, texture, video)) return
            textureAllocated = true
        }
        if (!uploadVideoFrame(gl, texture, video)) return

        with(target) {
            webGLContext.viewport(0, 0, size.width, size.height)
            webGLContext.disable(org.khronos.webgl.WebGLRenderingContext.Companion.SCISSOR_TEST)
            webGLContext.clearColor(0f, 0f, 0f, 0f)
            webGLContext.clear(COLOR_BUFFER_BIT)
            webGLContext.useProgram(program)
            webGLContext.activeTexture(org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE0)
            webGLContext.bindTexture(TEXTURE_2D, texture)
            webGLContext.uniform1i(videoUniform, 0)
            webGLContext.drawArrays(TRIANGLE_STRIP, 0, 4)
            webGLContext.bindTexture(TEXTURE_2D, null)
        }
    }

    fun play() {
        video?.let(::playVideo)
    }

    fun pause() {
        video?.let(::pauseVideo)
    }

    fun stop() {
        video?.let(::stopVideo)
        video = null
    }

    fun dispose() {
        stop()
        texture?.let(gl::deleteTexture)
        texture = null
        textureAllocated = false
        gl.deleteProgram(program)
    }

    companion object {
        private val VERTEX_SHADER_SOURCE = """
            #version 300 es
            out vec2 uv;
            const vec2 positions[4] = vec2[4](
                vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0), vec2(1.0, 1.0)
            );
            void main() {
                vec2 position = positions[gl_VertexID];
                uv = vec2((position.x + 1.0) * 0.5, 1.0 - (position.y + 1.0) * 0.5);
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SHADER_SOURCE = """
            #version 300 es
            precision mediump float;
            uniform sampler2D videoTexture;
            in vec2 uv;
            out vec4 color;
            void main() { color = texture(videoTexture, uv); }
        """.trimIndent()
    }
}

private fun createVideo(url: String): JsAny = js(
    """(function() {
        const video = document.createElement('video');
        video.crossOrigin = 'anonymous';
        video.muted = false;
        video.loop = true;
        video.playsInline = true;
        video.src = url;
        video.load();
        return video;
    })()"""
)

private fun playVideo(video: JsAny): Unit = js("video.play().catch(function() {})")
private fun pauseVideo(video: JsAny): Unit = js("video.pause()")
private fun stopVideo(video: JsAny): Unit = js("video.pause()")

private fun allocateVideoTexture(
    gl: WebGLRenderingContext,
    texture: WebGLTexture,
    video: JsAny,
): Boolean = js(
    """(function() {
        if (video.readyState < 2 || video.videoWidth === 0) return false;
        gl.bindTexture(gl.TEXTURE_2D, texture);
        gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, video.videoWidth, video.videoHeight);
        return true;
    })()"""
)

private fun uploadVideoFrame(
    gl: WebGLRenderingContext,
    texture: WebGLTexture,
    video: JsAny,
): Boolean = js(
    """(function() {
        if (video.readyState < 2 || video.videoWidth === 0) return false;
        gl.bindTexture(gl.TEXTURE_2D, texture);
        gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, gl.RGBA, gl.UNSIGNED_BYTE, video);
        return true;
    })()"""
)

private fun WebGLRenderingContext.createProgram(
    vertexSource: String,
    fragmentSource: String,
): WebGLProgram {
    val program = createProgram() ?: error("gl.createProgram() returned null")
    for ((type, source) in listOf(
        VERTEX_SHADER to vertexSource,
        FRAGMENT_SHADER to fragmentSource
    )) {
        val shader = createShader(type) ?: error("gl.createShader() returned null")
        shaderSource(shader, source)
        compileShader(shader)
        check(getShaderInfoLog(shader).isNullOrBlank())
        attachShader(program, shader)
        deleteShader(shader)
    }
    linkProgram(program)
    check(getProgramInfoLog(program).isNullOrBlank())
    return program
}
