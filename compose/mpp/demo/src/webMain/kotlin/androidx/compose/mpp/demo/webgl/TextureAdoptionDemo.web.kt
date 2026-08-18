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

// The demo reaches for ComposeWindow to get the DirectContext Compose renders with; it is internal
// API, hence the suppression (the same trick the rest of this demo module uses).
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
import androidx.compose.material.Button
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
 * Draws a WebGL scene inside Compose with no pixel copies: the scene is rendered into a
 * `WebGLTexture` that Skia has adopted, and Compose draws that texture like any other GPU image —
 * clipped, rotated, blurred and composited with regular Compose content on top of it.
 */
val TextureAdoptionScreen = Screen.Example("WebGL texture adoption") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TextureAdoptionDemo()
    }
}

/** One tick of the demo clock. */
private data class Frame(val index: Long, val timeSeconds: Float, val fps: Float)

@Composable
private fun TextureAdoptionDemo() {
    val composeWindow = LocalComposeWindow.current
    val scene = remember(composeWindow) {
        composeWindow?.let { AdoptedGlScene.createOrNull(it.htmlCanvas) }
    }
    DisposableEffect(scene) { onDispose { scene?.dispose() } }

    if (scene == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Could not obtain the WebGL context Compose renders with, so there is nothing to " +
                    "adopt a texture from.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    var running by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var warp by remember { mutableStateOf(scene.warp) }
    var hue by remember { mutableStateOf(scene.hue) }
    var glow by remember { mutableStateOf(scene.glow) }
    var recreateEveryFrame by remember { mutableStateOf(false) }
    var textureSide by remember { mutableStateOf(1024f) }
    var roundTripLog by remember { mutableStateOf<String?>(null) }

    // Read only from draw scopes, so that a new frame invalidates the drawing and not the whole UI.
    val frame = remember { mutableStateOf(Frame(0, 0f, 0f)) }
    // Read from composition, and therefore refreshed a few times per second instead of every frame.
    var stats by remember { mutableStateOf(Frame(0, 0f, 0f)) }

    scene.warp = warp
    scene.hue = hue
    scene.glow = glow
    scene.recreateTextureEveryFrame = recreateEveryFrame
    scene.textureSize = IntSize(textureSide.roundToInt(), (textureSide * 0.625f).roundToInt())

    // withFrameNanos callbacks run inside the frame, before Compose measures, lays out and draws —
    // so this is where the WebGL pass belongs: the texture holds this frame's content by the time
    // Skia submits the frame that samples it.
    LaunchedEffect(running, scene) {
        if (!running) return@LaunchedEffect
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val deltaSeconds =
                    if (previousNanos == 0L) 0f else (nanos - previousNanos) / 1_000_000_000f
                previousNanos = nanos
                val current = frame.value
                val next = Frame(
                    index = current.index + 1,
                    timeSeconds = current.timeSeconds + deltaSeconds * speed,
                    fps = if (deltaSeconds > 0f) {
                        current.fps * 0.9f + (1f / deltaSeconds) * 0.1f
                    } else {
                        current.fps
                    },
                )
                // Null until Compose has rendered its first frame and captured the context.
                val directContext = composeWindow?.skiaDirectContext
                if (directContext != null) {
                    scene.renderFrame(directContext, next.timeSeconds)
                }

                frame.value = next
                if (next.index % 20 == 0L) stats = next
            }
        }
    }

    Column(
        modifier = Modifier.width(600.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Hero(scene, frame)
        Variants(scene, frame)
        Controls(
            running = running,
            onRunningChange = { running = it },
            speed = speed,
            onSpeedChange = { speed = it },
            warp = warp,
            onWarpChange = { warp = it },
            hue = hue,
            onHueChange = { hue = it },
            glow = glow,
            onGlowChange = { glow = it },
            textureSide = textureSide,
            onTextureSideChange = { textureSide = it },
            recreateEveryFrame = recreateEveryFrame,
            onRecreateEveryFrameChange = { recreateEveryFrame = it },
            onRoundTrip = { roundTripLog = scene.registrationRoundTrip() },
        )
        Status(scene, stats, composeWindow?.skiaDirectContext, roundTripLog)
    }
}

/**
 * The adopted texture as the hero: tilted in 3D by dragging, clipped to a rounded rectangle, and
 * with Compose content composited on top of it.
 */
@Composable
private fun Hero(scene: AdoptedGlScene, frame: State<Frame>) {
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
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
            // A gradient underneath proves the texture arrives with a real alpha channel.
            .background(Brush.linearGradient(listOf(Color(0xFF12123A), Color(0xFF3A1250)))),
        contentAlignment = Alignment.BottomStart,
    ) {
        AdoptedTextureSurface(Modifier.fillMaxSize(), frame) { scene.image }
        Column(Modifier.padding(20.dp)) {
            Text(
                "Compose draws on top",
                color = Color.White,
                style = MaterialTheme.typography.h6,
            )
            Text(
                "drag to tilt · WebGL below, Compose above, one GPU texture",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

/** The same adopted texture, reused several times in one frame with different Compose treatments. */
@Composable
private fun Variants(scene: AdoptedGlScene, frame: State<Frame>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdoptedTextureSurface(Modifier.size(96.dp).clip(CircleShape), frame) { scene.image }
        AdoptedTextureSurface(
            Modifier.size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer {
                    rotationZ = 12f
                    alpha = 0.75f
                },
            frame,
        ) { scene.image }
        AdoptedTextureSurface(
            Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).blur(6.dp),
            frame,
        ) { scene.image }
    }
}

@Composable
private fun Controls(
    running: Boolean,
    onRunningChange: (Boolean) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    warp: Float,
    onWarpChange: (Float) -> Unit,
    hue: Float,
    onHueChange: (Float) -> Unit,
    glow: Float,
    onGlowChange: (Float) -> Unit,
    textureSide: Float,
    onTextureSideChange: (Float) -> Unit,
    recreateEveryFrame: Boolean,
    onRecreateEveryFrameChange: (Boolean) -> Unit,
    onRoundTrip: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LabelledSlider("warp", warp, 0f..1.2f, onValueChange = onWarpChange)
            LabelledSlider("palette", hue, 0f..1f, onValueChange = onHueChange)
            LabelledSlider("glow", glow, 0f..1.5f, onValueChange = onGlowChange)
            LabelledSlider("speed", speed, 0f..3f, onValueChange = onSpeedChange)
            LabelledSlider(
                label = "texture width",
                value = textureSide,
                valueRange = 256f..2048f,
                onValueChange = onTextureSideChange,
                valueText = "${textureSide.roundToInt()} px",
            )
            Toggle("animate", running, onRunningChange)
            Toggle(
                label = "adopt a new texture every frame",
                checked = recreateEveryFrame,
                onCheckedChange = onRecreateEveryFrameChange,
            )
            Button(onClick = onRoundTrip, modifier = Modifier.padding(top = 8.dp)) {
                Text("pushTexture + unregisterTexture round trip")
            }
        }
    }
}

@Composable
private fun Status(
    scene: AdoptedGlScene,
    frame: Frame,
    directContext: DirectContext?,
    roundTripLog: String?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatusLine("skia context", directContext?.toString() ?: "not captured yet")
            StatusLine("state", scene.status)
            StatusLine("adopted texture id", scene.adoptedTextureId.toString())
            StatusLine("textures handed to Skia", scene.adoptedTextureCount.toString())
            StatusLine("frame", "${frame.index} · ${frame.fps.roundToInt()} fps")
            if (roundTripLog != null) {
                StatusLine("round trip", roundTripLog)
            }
        }
    }
}
