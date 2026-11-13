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

package androidx.compose.mpp.demo

import androidx.compose.mpp.demo.components.text.loadResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.SingleComposeSceneRenderingScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkiaLayerRenderDelegate
import org.jetbrains.skiko.SkikoRenderDelegate

private fun getCanvasWidth(canvasId: String): Int =
    js("(() => { const c = document.getElementById(canvasId); return (c && c.width) ? Number(c.width) : 2048; })()")

private fun getCanvasHeight(canvasId: String): Int =
    js("(() => { const c = document.getElementById(canvasId); return (c && c.height) ? Number(c.height) : 1536; })()")

private fun registerPointerCallback(callback: (Int, Double, Double, Int, Double, Double) -> Unit) {
    js("""
        globalThis.__dispatchPointerEvent = function(eventTypeId, x, y, buttonId, scrollDeltaX, scrollDeltaY) {
            try {
                callback(
                    eventTypeId | 0,
                    Number(x) || 0.0,
                    Number(y) || 0.0,
                    (buttonId !== undefined) ? (buttonId | 0) : 1,
                    (scrollDeltaX !== undefined) ? Number(scrollDeltaX) : 0.0,
                    (scrollDeltaY !== undefined) ? Number(scrollDeltaY) : 0.0
                );
            } catch (e) {
                // Ignore transient cancellation or dispatch errors
            }
        };
    """)
}

/**
 * A standalone, DOM-less host that wires Compose directly to Skiko and native SDL / OpenGL.
 * Avoids any browser DOM emulation or ComposeViewport layers.
 */
@OptIn(InternalComposeUiApi::class)
class SdlComposeHost(
    private var width: Int = 2048,
    private var height: Int = 1536,
    private val densityValue: Float = 2f,
) {
    private var skiaLayer: SkiaLayer? = null
    private var frameRecomposer: FrameRecomposer? = null
    private var scene: ComposeScene? = null
    private var renderingScope: SingleComposeSceneRenderingScope? = null

    fun start(canvasId: String = "ComposeTarget") {
        registerSkikoComposeImplementation()

        val recomposer = FrameRecomposer(Dispatchers.Main) {
            skiaLayer?.needRender()
        }
        this.frameRecomposer = recomposer

        val scope = SingleComposeSceneRenderingScope {
            skiaLayer?.needRender()
        }
        this.renderingScope = scope

        this.width = getCanvasWidth(canvasId)
        this.height = getCanvasHeight(canvasId)

        val composeScene = CanvasLayersComposeScene(
            frameRecomposer = recomposer,
            density = Density(densityValue),
            size = IntSize(width, height),
            invalidateLayout = { scope.onSceneInvalidation() },
            invalidateDraw = { scope.onSceneInvalidation() },
        )
        this.scene = composeScene

        registerPointerCallback { eventTypeId, x, y, buttonId, scrollDeltaX, scrollDeltaY ->
            this.onPointerEvent(
                eventTypeId = eventTypeId,
                x = x.toFloat(),
                y = y.toFloat(),
                buttonId = buttonId,
                scrollDeltaX = scrollDeltaX.toFloat(),
                scrollDeltaY = scrollDeltaY.toFloat()
            )
        }

        composeScene.setContent {
            val app = remember { App() }
            app.Content()
        }

        val renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: SkCanvas, width: Int, height: Int, nanoTime: Long) {
                with(scope) {
                    composeScene.size = IntSize(width, height)
                    composeScene.render(recomposer, canvas.asComposeCanvas(), nanoTime)
                }
            }
        }

        val layer = SkiaLayer()
        layer.renderDelegate = SkiaLayerRenderDelegate(layer, renderDelegate)
        val canvasObj = document.getElementById(canvasId)
        if (canvasObj != null) {
            layer.attachTo(canvasObj)
            layer.needRender()
        }
        this.skiaLayer = layer
        activeSdlHost = this
    }

    fun onPointerEvent(
        eventTypeId: Int, // 1: Press, 2: Release, 3: Move, 4: Enter, 5: Exit, 6: Scroll
        x: Float,
        y: Float,
        buttonId: Int = 1,
        scrollDeltaX: Float = 0f,
        scrollDeltaY: Float = 0f
    ) {
        val eventType = when (eventTypeId) {
            1 -> PointerEventType.Press
            2 -> PointerEventType.Release
            3 -> PointerEventType.Move
            4 -> PointerEventType.Enter
            5 -> PointerEventType.Exit
            6 -> PointerEventType.Scroll
            else -> PointerEventType.Unknown
        }

        val pointerButton = when (buttonId) {
            1 -> PointerButton.Primary
            2 -> PointerButton.Tertiary
            3 -> PointerButton.Secondary
            else -> PointerButton.Primary
        }

        val isPrimary = (buttonId == 1) || (eventTypeId == 3 && (buttonId and 1) != 0)
        val isSecondary = (buttonId == 3) || (eventTypeId == 3 && (buttonId and 4) != 0)
        val isTertiary = (buttonId == 2) || (eventTypeId == 3 && (buttonId and 2) != 0)

        val buttons = if (eventTypeId == 2) {
            PointerButtons()
        } else {
            PointerButtons(
                isPrimaryPressed = isPrimary,
                isSecondaryPressed = isSecondary,
                isTertiaryPressed = isTertiary
            )
        }

        try {
            scene?.sendPointerEvent(
                eventType = eventType,
                position = Offset(x, y),
                scrollDelta = Offset(scrollDeltaX, scrollDeltaY),
                type = PointerType.Mouse,
                buttons = buttons,
                button = if (eventTypeId == 1 || eventTypeId == 2) pointerButton else null
            )
        } catch (_: Throwable) {
            // CancellationException or detached node lifecycle exceptions are normal during UI state transitions
        }
        skiaLayer?.needRender()
    }

    fun resize(newWidth: Int, newHeight: Int) {
        this.width = newWidth
        this.height = newHeight
        scene?.size = IntSize(newWidth, newHeight)
        skiaLayer?.needRender()
    }

    fun close() {
        scene?.close()
        frameRecomposer?.close()
        skiaLayer = null
        if (activeSdlHost === this) {
            activeSdlHost = null
        }
    }
}

internal var activeSdlHost: SdlComposeHost? = null

fun startSdlComposeDemo(canvasId: String = "ComposeTarget") {
    @Suppress("INVISIBLE_REFERENCE")
    org.jetbrains.skiko.wasm.onWasmReady {
        val host = SdlComposeHost()
        host.start(canvasId)
    }
}
