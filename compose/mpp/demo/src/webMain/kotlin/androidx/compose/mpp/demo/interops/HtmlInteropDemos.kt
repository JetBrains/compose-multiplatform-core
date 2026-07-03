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

package androidx.compose.mpp.demo.interops

import Map
import Directions
import LazyDirections
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import androidx.compose.ui.window.Dialog
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

val HtmlInteropDemos = Screen.Selection(
    "HtmlInteropDemos",
    Screen.Example("Directions") { Directions() },
    Screen.Example("LazyDirections") { LazyDirections() },
    Screen.Example("Map") { Map() },
    Screen.Example("SyncTextState") { SyncTextState() },
    Screen.Example("SyncTextStateViaParameter") { SyncTextStateViaParameter() },
    Screen.Example("Nested Compose Viewport") {
        NestedComposeViewportDemo()
    },
    Screen.Example("Dialog with HTML interop") { DialogWithHtmlInterop() },
)


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SyncTextState() {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight()
    ) {
        val textState = rememberTextFieldState("text line 1\ntext line 2")

        TextField(textState)

        HtmlElementView(
            factory = {
                (document.createElement("textarea") as HTMLTextAreaElement).apply {
                    style.apply {
                        boxSizing = "border-box"
                    }
                }
            },
            update = { input ->
                input.value = textState.text.toString()
            },
            modifier = Modifier.size(300.dp).padding(50.dp)
        )
    }
}

@Composable
fun SyncTextStateViaParameter() {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight()
    ) {
        val textState = rememberTextFieldState("text line 1\ntext line 2")
        TextField(textState)

        TextInDiv(textState.text.toString())
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TextInDiv(text: String) {
    HtmlElementView(
        factory = {
            (document.createElement("div") as HTMLDivElement).apply {
                innerText = text
            }
        },
        modifier = Modifier.size(300.dp).padding(50.dp),
        update = { div -> div.innerText = text }
    )
}

/**
 * Shows a native `<input>` living inside a [Dialog] — exercises `WebComposeSceneLayer`
 * (see CMP-8359-plan.md) when `isPerCanvasSceneLayerEnabled` is on: the dialog gets its own
 * `<canvas>`, and this interop element must be anchored to *that* canvas's own interop container,
 * not the main window's.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DialogWithHtmlInterop() {
    var open by remember { mutableStateOf(false) }
    var lastTypedValue by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { open = true }) {
            Text("Open dialog with HTML interop")
        }
        Spacer(Modifier.height(8.dp))
        Text("Last value read back from the native <input>: \"$lastTypedValue\"")
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Text("This is a native <input> element, rendered inside the dialog's own canvas:")
                Spacer(Modifier.height(8.dp))
                HtmlElementView(
                    modifier = Modifier.size(250.dp, 40.dp),
                    factory = {
                        (document.createElement("input") as HTMLInputElement).apply {
                            type = "text"
                            placeholder = "Type here..."
                            oninput = { lastTypedValue = value }
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { open = false }) {
                    Text("Close")
                }
            }
        }
    }
}