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

// Reaches for ComposeWindow to get the DirectContext Compose renders with; it is internal API, hence
// the suppression (the same trick the rest of this demo module uses).
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package androidx.compose.mpp.demo.webgl

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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalComposeWindow
import kotlin.math.roundToInt
import org.jetbrains.skia.DirectContext

/**
 * The texture adoption demo with the WebGL implementation delegated to three.js:
 * it renders a lit torus knot into a texture, which is then adopted by Skiko and rendered as Skiko Image.
 */
val ThreeJsTextureAdoptionScreen = Screen.Example("WebGL texture adoption / Three.js integration") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ThreeTextureAdoptionDemo()
    }
}

/** One tick of the demo clock. */
private data class ThreeFrame(val index: Long, val fps: Float)

private sealed interface SceneState {
    object Loading : SceneState

    class Ready(val scene: ThreeJsAdoptedScene) : SceneState

    class Failed(val message: String) : SceneState
}

@Composable
private fun ThreeTextureAdoptionDemo() {
    val composeWindow = LocalComposeWindow.current
    var sceneState by remember { mutableStateOf<SceneState>(SceneState.Loading) }

    // three.js arrives through a dynamic import, so the scene can only be built asynchronously.
    LaunchedEffect(composeWindow) {
        val canvas = composeWindow?.htmlCanvas
        sceneState = if (canvas == null) {
            SceneState.Failed(
                "Could not obtain the WebGL context Compose renders with, so there is nothing to " +
                    "adopt a texture from."
            )
        } else {
            try {
                val scene = ThreeJsAdoptedScene.createOrNull(canvas)
                if (scene != null) {
                    SceneState.Ready(scene)
                } else {
                    SceneState.Failed("three.js or the WebGL2 context Skiko uses is unavailable.")
                }
            } catch (throwable: Throwable) {
                SceneState.Failed("Loading three.js failed: ${throwable.message}")
            }
        }
    }

    val state = sceneState
    DisposableEffect(state) {
        onDispose {
            if (state is SceneState.Ready) {
                state.scene.dispose(composeWindow?.skiaDirectContext)
            }
        }
    }

    when (state) {
        is SceneState.Loading -> Centered("loading three.js…")
        is SceneState.Failed -> Centered(state.message)
        // skiaDirectContext is a plain field that stays null until Compose has rendered its first
        // frame, so it is read through a lambda instead of being captured during composition.
        is SceneState.Ready ->
            ThreeSceneContent(state.scene) { composeWindow?.skiaDirectContext }
    }
}

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ThreeSceneContent(threeJsScene: ThreeJsAdoptedScene, directContext: () -> DirectContext?) {
    var running by remember { mutableStateOf(true) }
    var spin by remember { mutableStateOf(threeJsScene.spin) }
    var hue by remember { mutableStateOf(threeJsScene.hue) }
    var roughness by remember { mutableStateOf(threeJsScene.roughness) }
    var metalness by remember { mutableStateOf(threeJsScene.metalness) }
    var opacity by remember { mutableStateOf(threeJsScene.opacity) }
    var lightIntensity by remember { mutableStateOf(threeJsScene.lightIntensity) }
    var textureSize by remember { mutableStateOf(512) }

    // Read only from draw scopes, so that a new frame invalidates the drawing and not the whole UI.
    val frame = remember { mutableStateOf(ThreeFrame(0, 0f)) }
    // Read from composition, and therefore refreshed a few times per second instead of every frame.
    var stats by remember { mutableStateOf(ThreeFrame(0, 0f)) }

    threeJsScene.spin = spin
    threeJsScene.hue = hue
    threeJsScene.roughness = roughness
    threeJsScene.metalness = metalness
    threeJsScene.opacity = opacity
    threeJsScene.lightIntensity = lightIntensity
    threeJsScene.textureSize = IntSize(textureSize, (textureSize * 0.625f).roundToInt())

    // withFrameNanos callbacks run inside the frame, before Compose measures, lays out and draws — so
    // this is where three.js belongs: the texture holds this frame's content by the time Skia submits
    // the frame that samples it. Drawing sites below only draw the resulting image.
    LaunchedEffect(running, threeJsScene) {
        if (!running) return@LaunchedEffect
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val deltaSeconds =
                    if (previousNanos == 0L) 0f else (nanos - previousNanos) / 1_000_000_000f
                previousNanos = nanos
                val current = frame.value
                val next = ThreeFrame(
                    index = current.index + 1,
                    fps = if (deltaSeconds > 0f) {
                        current.fps * 0.9f + (1f / deltaSeconds) * 0.1f
                    } else {
                        current.fps
                    },
                )
                val context = directContext()
                if (context != null) {
                    threeJsScene.renderFrame(context, deltaSeconds)
                }

                frame.value = next
                if (next.index % 20 == 0L) stats = next
            }
        }
    }

    Column(
        modifier = Modifier.width(600.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AdoptedImageRender({ threeJsScene.image }, frame)
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
                    value = textureSize.toDouble(),
                    valueRange = 16f..2048f,
                    onValueChange = { textureSize = it.roundToInt() },
                    valueText = "${textureSize} px",
                )
                Toggle("animate", running) { running = it }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusLine("skia context", directContext()?.toString() ?: "not captured yet")
                StatusLine("state", threeJsScene.status)
                StatusLine("adopted texture id", threeJsScene.adoptedTextureId.toString())
                StatusLine("textures handed to Skia", threeJsScene.adoptedTextureCount.toString())
                StatusLine("frame", "${stats.index} · ${stats.fps.roundToInt()} fps")
            }
        }
    }
}

/** The three.js output as the hero: tilted in 3D by dragging, clipped, with Compose content on top. */
@Composable
private fun AdoptedImageRender(imageProvider: () -> org.jetbrains.skia.Image?, frame: State<Any?>) {
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                style = MaterialTheme.typography.h3
            )
        }
        AdoptedTextureSurface(Modifier.fillMaxSize(), frame, imageProvider)
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

    Variants(imageProvider, frame)
}

/** The same adopted texture, reused several times in one frame with different transformations. */
@Composable
private fun Variants(imageProvider: () -> org.jetbrains.skia.Image?, frame: State<Any?>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AdoptedTextureSurface(Modifier.size(96.dp).clip(CircleShape).background(Color.LightGray), frame, imageProvider)
        AdoptedTextureSurface(
            Modifier.size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.DarkGray)
                .graphicsLayer {
                    alpha = 0.75f
                    scaleX = -0.75f
                    scaleY = 0.75f

                },
            frame,
            imageProvider,
        )
        AdoptedTextureSurface(
            Modifier.size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray)
                .blur(2.dp)
                .scale(1f, -1f),
            frame,
            imageProvider
        )
    }
}
