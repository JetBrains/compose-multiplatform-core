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

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogInteractionTest {
    @Test
    fun testDialogDismissOnClickOutsideEnabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Dialog(
                onDismissRequest = {
                    dismissTriggered = true
                },
                properties = DialogProperties(dismissOnClickOutside = true)
            ) {
                Button(onClick = {}) {
                    Text("Dialog")
                }
            }
        }

        tap(DpOffset(x = screenSize.width / 2, 100.dp))

        waitForIdle()

        assertTrue(dismissTriggered)
        delay(1000)
    }

    @Test
    fun testDialogDismissOnClickOutsideDisabled() = runUIKitInstrumentedTest {
        var dismissTriggered = false
        setContent {
            Dialog(
                onDismissRequest = { dismissTriggered = true },
                properties = DialogProperties(dismissOnClickOutside = false)
            ) {
                Button(onClick = {}) {
                    Text("Dialog")
                }
            }
        }

        tap(DpOffset(x = screenSize.width / 2, 100.dp))
        waitForIdle()

        assertFalse(dismissTriggered)
        delay(1000)
    }

    @Test
    fun testManyDialogsDismissOnClickOutside() = runUIKitInstrumentedTest {
        val showDialog1 = mutableStateOf(true)
        val showDialog2 = mutableStateOf(true)
        setContent {
            if (showDialog1.value) {
                Dialog(
                    onDismissRequest = { showDialog1.value = false },
                    properties = DialogProperties(dismissOnClickOutside = true)
                ) {
                    Button(onClick = {}) {
                        Text("Dialog")
                    }
                }
            }
            if (showDialog2.value) {
                Dialog(
                    onDismissRequest = { showDialog2.value = false },
                    properties = DialogProperties(dismissOnClickOutside = true)
                ) {
                    Button(onClick = {}) {
                        Text("Dialog")
                    }
                }
            }
        }

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertTrue(showDialog1.value)
        assertFalse(showDialog2.value)

        tap(DpOffset(x = screenSize.width / 2, y = 100.dp))
        waitForIdle()

        assertFalse(showDialog1.value)
        assertFalse(showDialog2.value)
        delay(1000)
    }
}