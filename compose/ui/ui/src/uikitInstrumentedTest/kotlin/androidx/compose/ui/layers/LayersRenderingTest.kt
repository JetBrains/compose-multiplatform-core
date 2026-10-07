/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.layers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.captureScreenshot
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.forEachPixel
import androidx.compose.ui.test.utils.forEachPixelInRect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import platform.UIKit.UIImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class LayersRenderingTest {
    @Test
    fun testPopupDoesNotDrawAtOriginBeforeParentAnchor() = runUIKitInstrumentedTest {
        val popupSize = 20.dp
        var showPopup by mutableStateOf(false)
        var captureNextParentDraw by mutableStateOf(false)
        var firstPopupFrame: UIImage? = null
        var popupContentPlaced by mutableStateOf(false)

        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Blue)
                    .drawBehind {
                        if (captureNextParentDraw) {
                            dispatch_async(dispatch_get_main_queue()) {
                                firstPopupFrame = captureScreenshot()
                            }
                            captureNextParentDraw = false
                        }
                    }
            )
            if (showPopup) {
                Popup(
                    alignment = Alignment.Center,
                    onDismissRequest = {},
                    properties = PopupProperties(usePlatformInsets = false),
                ) {
                    Box(
                        Modifier
                            .size(popupSize)
                            .background(Color.Red)
                            .onPlaced { popupContentPlaced = true }
                    )
                }
            }
        }

        captureNextParentDraw = true
        showPopup = true
        waitUntil("First popup frame should be captured") { firstPopupFrame != null }

        firstPopupFrame!!.forEachPixel(step = 4) { _, _, color ->
            assertEquals(Color.Blue, color, "Popup content appeared before its anchor was available")
        }

        waitUntil("Popup content should be placed") { popupContentPlaced }
        waitForIdle()
        val settledPopupFrame = assertNotNull(captureScreenshot())
        val expectedPopupBounds = with(density) {
            DpRect(
                origin = DpOffset(
                    x = (screenSize.width - popupSize) / 2,
                    y = (screenSize.height - popupSize) / 2,
                ),
                size = DpSize(popupSize, popupSize),
            ).toRect().roundToIntRect()
        }
        settledPopupFrame.forEachPixelInRect(expectedPopupBounds, step = 4) { _, _, color ->
            assertEquals(Color.Red, color, "Popup content was not drawn at its expected position")
        }
    }

    @Test
    fun testLayerContentAfterParentAnchorIsAvailable() = runUIKitInstrumentedTest {
        var showRed by mutableStateOf(false)
        var showGreen by mutableStateOf(false)
        var popupContentPlaced by mutableStateOf(false)

        setContent {
            Box(Modifier.fillMaxSize().background(Color.Blue))
            if (showRed) {
                Popup(
                    onDismissRequest = {},
                    properties = PopupProperties(usePlatformInsets = false)
                ) {
                    DisposableEffect(Unit) {
                        onDispose { popupContentPlaced = false }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Red)
                            .onPlaced { popupContentPlaced = true }
                    )
                }
            }
            if (showGreen) {
                Popup(
                    onDismissRequest = {},
                    properties = PopupProperties(usePlatformInsets = false)
                ) {
                    DisposableEffect(Unit) {
                        onDispose { popupContentPlaced = false }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Green)
                            .onPlaced { popupContentPlaced = true }
                    )
                }
            }
        }

        fun assertFrameColor(expectedColor: Color) {
            waitForIdle()
            assertNotNull(captureScreenshot()).forEachPixel(step = 4) { _, _, actualColor ->
                assertEquals(
                    expectedColor,
                    actualColor,
                    "Expected to draw $expectedColor background"
                )
            }
        }

        fun awaitPopupContentPlacement() {
            waitUntil("Popup content should be placed") { popupContentPlaced }
            waitForIdle()
        }

        fun awaitPopupContentDisposal() {
            waitUntil("Popup content should be disposed") { !popupContentPlaced }
            waitForIdle()
        }

        // IosComposeSceneLayer owns a separate ComposeScene. Its first layout is not ordered
        // after the parent scene's onPlaced callback, so this test only asserts the result once
        // UIKit has processed both scenes, rather than requiring the popup in a particular frame.
        showRed = true
        awaitPopupContentPlacement()
        assertFrameColor(Color.Red)

        showRed = false
        awaitPopupContentDisposal()
        assertFrameColor(Color.Blue)

        showGreen = true
        awaitPopupContentPlacement()
        assertFrameColor(Color.Green)

        showGreen = false
        awaitPopupContentDisposal()
        assertFrameColor(Color.Blue)
    }
}
