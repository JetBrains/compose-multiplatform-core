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

package androidx.compose.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import androidx.compose.ui.window.ComposeWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.LocalComposeWindow
import androidx.compose.ui.window.Popup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/**
 * Smoke tests for [WebComposeSceneLayer], gated behind
 * [androidx.compose.ui.window.ComposeViewportConfiguration.isPerCanvasSceneLayerEnabled]
 * (CMP-8359 slice 2). Full DOM-assertion coverage (z-order, outside-click dismiss, scrim, resize)
 * is tracked as a later sub-step once the outside-click registry lands.
 */
class WebComposeSceneLayerTest : OnCanvasTests {
    @Test
    fun flagEnabledNoPopup() = runApplicationTest {
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            Text("no popup here")
        }
        awaitIdle()
    }

    @Test
    fun popupSharesMainCanvasWhenFlagDisabled() = runApplicationTest {
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = false }) {
            Popup {
                Text("popup content")
            }
        }
        awaitIdle()

        assertEquals(0, getLayersRoot().querySelectorAll("canvas").length)
    }

    @Test
    fun popupGetsOwnCanvasWhenFlagEnabled() = runApplicationTest {
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            Popup {
                Box(Modifier.size(10.dp)) {
                    Text("popup content")
                }
            }
        }
        awaitIdle()

        assertEquals(1, getLayersRoot().querySelectorAll("canvas").length)
    }

    @Test
    fun popupInteropViewIsAnchoredToItsOwnLayerNotTheMainWindow() = runApplicationTest {
        val divId = "layerInteropDiv"
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            Popup {
                Box(Modifier.size(10.dp)) {
                    HtmlElementView(
                        modifier = Modifier.size(10.dp),
                        factory = {
                            (document.createElement("div") as HTMLDivElement).apply { id = divId }
                        }
                    )
                }
            }
        }
        awaitIdle()

        // If the layer fell back to the main window's shared interop container instead of its
        // own (the bug this test guards against), the div would land as a sibling of
        // layersRoot rather than inside it.
        assertNotNull(getLayersRoot().querySelector("#$divId"))
    }

    @Test
    fun clickOutsidePopupDismissesItWhenFlagEnabled() = runApplicationTest {
        var dismissed = false
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            Popup(onDismissRequest = { dismissed = true }) {
                Box(Modifier.size(10.dp)) {
                    Text("popup content")
                }
            }
        }
        awaitIdle()
        assertFalse(dismissed)

        // Dispatched on the main window's own canvas — a real Node outside the popup layer's
        // canvas. bubbles=true + composed=true so it reaches the window-level outside-click
        // listener despite the canvas living inside a shadow root — matching what a genuine
        // user-generated PointerEvent gets by spec (real UI events are always composed; only
        // synthetic test-constructed ones need it set explicitly).
        dispatchEvents(
            getCanvas(),
            PointerEvent(
                "pointerdown",
                PointerEventInit(clientX = 5, clientY = 5, button = 0, buttons = 1, bubbles = true, composed = true, pointerType = "mouse")
            )
        )
        awaitIdle()

        assertTrue(dismissed)
    }

    @Test
    fun twoPopupsStackInDomAppendOrder() = runApplicationTest {
        var window: ComposeWindow? = null
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            window = LocalComposeWindow.current
            Popup { Box(Modifier.size(10.dp)) { Text("first") } }
            Popup { Box(Modifier.size(10.dp)) { Text("second") } }
        }
        awaitIdle()

        val layers = window!!.layers
        assertEquals(2, layers.size)

        val domCanvasList = getLayersRoot().querySelectorAll("canvas").let { nodeList ->
            (0 until nodeList.length).map { nodeList[it] as HTMLCanvasElement }
        }
        // Z-order falls out of DOM append order: each layer's own canvas must appear later in
        // the DOM than the previously-attached layer's, matching `layers`'s attach order.
        val domIndices = layers.map { domCanvasList.indexOf(it.canvas) }
        assertEquals(listOf(0, 1), domIndices)
    }

    @Test
    fun dialogRendersScrimWithDefaultColor() = runApplicationTest {
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            Dialog(onDismissRequest = {}) {
                Box(Modifier.size(10.dp)) { Text("dialog content") }
            }
        }
        awaitIdle()

        val scrimDivs = getLayersRoot().querySelectorAll("div").let { nodeList ->
            (0 until nodeList.length).map { nodeList[it] as HTMLDivElement }
        }
        val scrim = scrimDivs.firstOrNull {
            it.style.backgroundColor.isNotBlank() && it.style.backgroundColor != "transparent"
        }
        assertNotNull(scrim, "expected a scrim <div> with a non-transparent background")
    }

    @Test
    fun popupLayerRepositionsWhenOffsetChanges() = runApplicationTest {
        var offset by mutableStateOf(IntOffset.Zero)
        var window: ComposeWindow? = null
        createComposeWindow(configure = { isPerCanvasSceneLayerEnabled = true }) {
            window = LocalComposeWindow.current
            Popup(offset = offset) {
                Box(Modifier.size(10.dp)) { Text("content") }
            }
        }
        awaitIdle()

        val layer = window!!.layers.single()
        val initialLeft = layer.boundsInWindow.left
        val initialWidth = layer.canvas.width
        assertTrue(initialWidth > 0)

        // A new offset value creates a new AlignmentOffsetPositionProvider instance (it's
        // remembered keyed on alignment+offset), which is itself a remember key for the popup's
        // measure policy — forcing a full remeasure/reposition, unlike a plain content-size change
        // which may or may not force the *parent* Layout to remeasure depending on the measure
        // policy's own constraints.
        offset = IntOffset(50, 50)
        awaitIdle()
        // The resize/reposition triggered by the new bounds is deferred to the next animation
        // frame (to avoid reentering the measure pass that computed them — see
        // WebComposeSceneLayer.boundsInWindow).
        awaitAnimationFrame()

        assertTrue(layer.boundsInWindow.left > initialLeft)
        // Canvas pixel dimensions are untouched by a pure reposition (content size didn't change).
        assertEquals(initialWidth, layer.canvas.width)
    }
}
