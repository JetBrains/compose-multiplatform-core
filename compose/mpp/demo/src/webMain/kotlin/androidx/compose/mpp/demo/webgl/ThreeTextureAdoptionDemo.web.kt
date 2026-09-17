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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.webgl.WebGLRenderTarget
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.webgl.rememberWebGLRenderTarget
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A demo of [WebGLRenderTarget]: three.js renders a lit torus knot into a texture Compose owns
 * and Skia adopted, and Compose then draws that texture like any other image.
 */
val ThreeJsTextureAdoptionScreen =
    Screen.Example("Three.js integration") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ThreeTextureAdoptionDemo()
        }
    }

@Composable
private fun ThreeTextureAdoptionDemo() {
    var textureWidth by remember { mutableStateOf(512) }
    val textureSize = IntSize(textureWidth, (textureWidth * 0.625f).roundToInt())

    val renderTarget = rememberWebGLRenderTarget(textureSize)
    if (renderTarget == null) {
        Centered(
            "Compose does not render through a WebGL2 canvas here, so there is no texture to adopt."
        )
        return
    }

    // three.js arrives through a dynamic import, so the renderer can only be built asynchronously.
    val loadState by produceState<LoadState>(LoadState.Loading, renderTarget) {
        value = try {
            ThreeJsKnotRenderer.createOrNull(renderTarget)?.let(LoadState::Ready)
                ?: LoadState.Failed("three.js is unavailable.")
        } catch (throwable: Throwable) {
            LoadState.Failed("Loading three.js failed: ${throwable.message}")
        }
    }

    when (val state = loadState) {
        LoadState.Loading -> Centered("loading three.js…")
        is LoadState.Failed -> Centered(state.message)
        is LoadState.Ready ->
            ThreeSceneContent(
                renderTarget = renderTarget,
                threeJs = state.renderer,
                textureWidth = textureWidth,
                onTextureWidthChange = { textureWidth = it },
            )
    }
}

private sealed interface LoadState {
    object Loading : LoadState

    class Ready(val renderer: ThreeJsKnotRenderer) : LoadState

    class Failed(val message: String) : LoadState
}

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ThreeSceneContent(
    renderTarget: WebGLRenderTarget,
    threeJs: ThreeJsKnotRenderer,
    textureWidth: Int,
    onTextureWidthChange: (Int) -> Unit,
) {
    var running by remember { mutableStateOf(true) }
    var spin by remember { mutableStateOf(threeJs.spin) }
    var hue by remember { mutableStateOf(threeJs.hue) }
    var roughness by remember { mutableStateOf(threeJs.roughness) }
    var metalness by remember { mutableStateOf(threeJs.metalness) }
    var opacity by remember { mutableStateOf(threeJs.opacity) }
    var lightIntensity by remember { mutableStateOf(threeJs.lightIntensity) }

    threeJs.spin = spin
    threeJs.hue = hue
    threeJs.roughness = roughness
    threeJs.metalness = metalness
    threeJs.opacity = opacity
    threeJs.lightIntensity = lightIntensity

    DisposableEffect(threeJs, renderTarget) { onDispose { threeJs.dispose(renderTarget) } }

    // Everything three.js does happens inside the frame, before Compose measures, lays out and
    // draws, so the texture holds this frame's content by the time Skia submits the frame that
    // samples it. The drawing sites below only draw the result.
    LaunchedEffect(renderTarget, threeJs, running) {
        while (running) {
            withFrameNanos { frameTimeNanos ->
                threeJs.renderFrame(frameTimeNanos)
            }
        }
    }

    // Read from composition, and therefore refreshed a few times per second instead of every frame.
    var stats by remember { mutableStateOf(FrameStats(0, 0f)) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val deltaSeconds = if (previousNanos == 0L) {
                    0f
                } else {
                    (nanos - previousNanos) / 1_000_000_000f
                }
                previousNanos = nanos
                val next = FrameStats(
                    index = stats.index + 1,
                    fps =
                        if (deltaSeconds > 0f) stats.fps * 0.9f + (1f / deltaSeconds) * 0.1f
                        else stats.fps,
                )
                if (next.index % 20 == 0L) stats = next
            }
        }
    }

    Column(
        modifier = Modifier.width(600.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hero(renderTarget)
        Variants(renderTarget)
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LabelledSlider("spin", spin, 0f..3f) { spin = it }
                LabelledSlider("color", hue, 0f..1f) { hue = it }
                LabelledSlider("roughness", roughness, 0f..1f) { roughness = it }
                LabelledSlider("metalness", metalness, 0f..1f) { metalness = it }
                LabelledSlider("opacity", opacity, 0.05f..1f) { opacity = it }
                LabelledSlider("light", lightIntensity, 0f..8f) { lightIntensity = it }
                LabelledSlider(
                    label = "texture width",
                    value = textureWidth.toDouble(),
                    valueRange = 16f..2048f,
                    onValueChange = { onTextureWidthChange(it.roundToInt()) },
                    valueText = "$textureWidth px",
                )
                Toggle("animate", running) { running = it }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusLine("texture size", "${renderTarget.size.width}×${renderTarget.size.height}")
                StatusLine("state", threeJs.status)
                StatusLine("frame", "${stats.index} · ${stats.fps.roundToInt()} fps")
            }
        }
    }
}

/** One tick of the demo clock, sampled a few times per second. */
private data class FrameStats(val index: Long, val fps: Float)

/**
 * The three.js output as the hero: tilted in 3D by dragging, clipped, with Compose content on top.
 */
@Composable
private fun Hero(renderTarget: WebGLRenderTarget) {
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(1.3f)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        tiltY = (tiltY + dragAmount.x * 0.15f).coerceIn(-35f, 35f)
                        tiltX = (tiltX - dragAmount.y * 0.15f).coerceIn(-35f, 35f)
                    }
                }
                .graphicsLayer {
                    rotationX = tiltX
                    rotationY = tiltY
                    cameraDistance = 16f * density
                }
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0E1B33), Color(0xFF3A1250)))),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "This is Compose Text",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.h3,
            )
        }
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.padding(20.dp)) {
            Text(
                "three.js below, Compose above",
                color = Color.White,
                style = MaterialTheme.typography.h6,
            )
            Text(
                "drag to tilt · one WebGL context, one GPU texture, no copies",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

/** The same adopted texture, drawn several times in one frame with different transformations. */
@Composable
private fun Variants(renderTarget: WebGLRenderTarget) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.LightGray),
        )
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.size(96.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.DarkGray)
                    .graphicsLayer {
                        alpha = 0.75f
                        scaleX = -0.75f
                        scaleY = 0.75f
                    },
        )
        Image(
            painter = renderTarget.painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray)
                    .blur(2.dp)
                    .scale(1f, -1f),
        )
    }
}
