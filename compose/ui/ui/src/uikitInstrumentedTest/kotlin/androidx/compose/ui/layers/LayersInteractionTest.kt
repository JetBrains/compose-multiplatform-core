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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.findNodeWithLabel
import androidx.compose.ui.test.findNodeWithLabelOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayersInteractionTest {
    @Test
    fun testPopupDismissOnClickOutsideEnabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Popup(
                onDismissRequest = {
                    println(">> On dismiss!")
                    dismissTriggered = true
                },
                properties = PopupProperties(dismissOnClickOutside = true)
            ) {
                Text("Popup")
            }
        }

        tap(screenSize.center)

        waitForIdle()

        assertTrue(dismissTriggered)
    }

    @Test
    fun testPopupDismissOnClickOutsideDisabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Popup(
                onDismissRequest = { dismissTriggered = true },
                properties = PopupProperties(dismissOnClickOutside = false)
            ) {
                Text("Popup")
            }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertFalse(dismissTriggered)
    }

    @Test
    fun testDialogDismissOnClickOutsideEnabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Dialog(
                onDismissRequest = {
                    println(">> On dismiss!")
                    dismissTriggered = true
                },
                properties = DialogProperties(dismissOnClickOutside = true)
            ) {
                Text("Dialog")
            }
        }

        tap(screenSize.center)

        waitForIdle()

        assertTrue(dismissTriggered)
    }

    @Test
    fun testDialogDismissOnClickOutsideDisabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Dialog(
                onDismissRequest = { dismissTriggered = true },
                properties = DialogProperties(dismissOnClickOutside = false)
            ) {
                Text("Dialog")
            }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertFalse(dismissTriggered)
    }

    @Test
    fun testPopupDismissOnClickOutsideEnabledAndFocusable() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Popup(
                onDismissRequest = { dismissTriggered = true },
                properties = PopupProperties(
                    dismissOnClickOutside = true,
                    focusable = true
                )
            ) {
                Text("Popup")
            }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertTrue(dismissTriggered)
    }

    @Test
    fun testPopupDismissOnClickOutsideDisabledAndFocusable() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Popup(
                onDismissRequest = { dismissTriggered = true },
                properties = PopupProperties(
                    dismissOnClickOutside = false,
                    focusable = true
                )
            ) {
                Text("Popup")
            }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertFalse(dismissTriggered)
    }

    @Test
    fun testManyPopupsDismissOnClickOutside() = runUIKitInstrumentedTest {
        var dismiss1Triggered = false
        var dismiss2Triggered = false
        var dismiss3Triggered = false
        setContent {
            Popup(
                onDismissRequest = { dismiss1Triggered = true },
                properties = PopupProperties(dismissOnClickOutside = true)
            ) { Text("Popup 1") }
            Popup(
                onDismissRequest = { dismiss2Triggered = true },
                properties = PopupProperties(dismissOnClickOutside = true)
            ) { Text("Popup 2") }
            Popup(
                onDismissRequest = { dismiss3Triggered = true },
                properties = PopupProperties(dismissOnClickOutside = true)
            ) { Text("Popup 3") }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertTrue(dismiss1Triggered)
        assertTrue(dismiss2Triggered)
        assertTrue(dismiss3Triggered)
    }

    @Test
    fun testFocusablePopupInteraction() = runUIKitInstrumentedTest {
        var contentButtonClicked = false
        var popupButtonClicked = false
        setContent {
            Button(
                onClick = { contentButtonClicked = true },
                modifier = Modifier.Companion.fillMaxSize()
            ) {
                Text("Content Button")
            }
            Popup(
                properties = PopupProperties(dismissOnClickOutside = false, focusable = true)
            ) {
                Button({ popupButtonClicked = true }) {
                    Text("Popup Button")
                }
            }
        }

        findNodeWithLabel("Popup Button").tap()
        waitForIdle()
        assertTrue(popupButtonClicked)

        val contentButtonAccessible = findNodeWithLabelOrNull("Content Button") != null
        assertFalse(contentButtonAccessible)

        tap(screenSize.center)
        waitForIdle()
        assertFalse(contentButtonClicked)
    }

    @Test
    fun testNonFocusablePopupInteraction() = runUIKitInstrumentedTest {
        var contentButtonClicked = false
        var popupButtonClicked = false
        setContent {
            Button(
                onClick = { contentButtonClicked = true },
                modifier = Modifier.Companion.fillMaxSize()
            ) {
                Text("Content Button")
            }
            Popup(
                properties = PopupProperties(dismissOnClickOutside = false, focusable = false)
            ) {
                Button({ popupButtonClicked = true }) {
                    Text("Popup Button")
                }
            }
        }

        findNodeWithLabel("Popup Button").tap()
        waitForIdle()
        assertTrue(popupButtonClicked)

        findNodeWithLabel("Content Button").tap()
        waitForIdle()
        assertTrue(contentButtonClicked)
    }
}