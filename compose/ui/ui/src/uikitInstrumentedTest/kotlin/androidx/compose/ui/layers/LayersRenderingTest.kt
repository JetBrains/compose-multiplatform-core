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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.captureScreenshot
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.forEachPixel
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LayersRenderingTest {
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
