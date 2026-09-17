/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.accessibility

import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertAccessibilityTree
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test

class LayersAccessibilityTest {

    @Test
    fun testNodesCoveredByPopup() = runUIKitInstrumentedTest(useHostingView = false) {
        val topPopup = mutableStateOf(false)
        val bottomPopup = mutableStateOf(false)
        val topPopupFocusable = mutableStateOf(false)
        setContent {
            Text("Root")
            if (bottomPopup.value) {
                Popup {
                    Text("Popup 1")
                }
            }
            if (topPopup.value) {
                Popup(properties = PopupProperties(focusable = topPopupFocusable.value)) {
                    Text("Popup 2")
                }
            }
        }

        assertAccessibilityTree {
            label = "Root"
        }

        bottomPopup.value = true
        // Non-focusable popup should not hide content under it for accessibility reader
        assertAccessibilityTree {
            node {
                label = "Root"
            }
            node {
                label = "Popup 1"
            }
        }

        topPopup.value = true
        // Non-focusable popup should not hide content under it for accessibility reader
        assertAccessibilityTree {
            node {
                label = "Root"
            }
            node {
                node {
                    label = "Popup 1"
                }
                node {
                    label = "Popup 2"
                }
            }
        }

        topPopupFocusable.value = true
        // Popup should react on focusable flag change
        assertAccessibilityTree {
            label = "Popup 2"
        }

        topPopup.value = false
        bottomPopup.value = false
        assertAccessibilityTree {
            label = "Root"
        }
    }

    @Test
    fun testNodesCoveredByDialog() = runUIKitInstrumentedTest {
        val showDialog = mutableStateOf(false)
        setContent {
            Text("Root")
            Popup {
                Text("Popup")
            }
            if (showDialog.value) {
                Dialog(onDismissRequest = {}) {
                    Text("Dialog")
                }
            }
        }

        assertAccessibilityTree {
            node {
                label = "Root"
            }
            node {
                label = "Popup"
            }
        }

        showDialog.value = true
        // Dialog popup should hide content under it for accessibility reader
        assertAccessibilityTree {
            label = "Dialog"
        }

        showDialog.value = false

        assertAccessibilityTree {
            node {
                label = "Root"
            }
            node {
                label = "Popup"
            }
        }
    }

    @Test
    fun testLayersAppearanceOrder() = runUIKitInstrumentedTest(useHostingView = false) {
        val bottomLayer = mutableStateOf(false)
        val middleLayers = mutableStateOf(false)
        setContent {
            Text("Root")
            if (bottomLayer.value) {
                Popup(properties = PopupProperties(focusable = true)) {
                    Text("Bottom")
                }
            }
            if (middleLayers.value) {
                Popup(properties = PopupProperties(focusable = true)) {
                    Text("Middle 1")
                }
                // Non-focusable layer
                Popup {
                    Text("Middle 2")
                }
            }
            Popup(properties = PopupProperties(focusable = true)) {
                Text("Top")
            }
        }

        assertAccessibilityTree {
            label = "Top"
        }

        bottomLayer.value = true
        // The last added layer should be on top
        assertAccessibilityTree {
            label = "Bottom"
        }

        middleLayers.value = true
        // The last added layers should be on top
        assertAccessibilityTree {
            node {
                label = "Middle 1"
            }
            node {
                label = "Middle 2"
            }
        }
    }
}