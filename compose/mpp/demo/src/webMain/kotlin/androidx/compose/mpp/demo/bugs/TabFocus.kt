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

package androidx.compose.mpp.demo.bugs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document

@Composable
fun TabFocus() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicTextField(
            state = rememberTextFieldState("TextField 1"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        BasicTextField(
            state = rememberTextFieldState("TextField 2"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Checkbox(checked = false, onCheckedChange = {})
        BasicTextField(
            state = rememberTextFieldState("TextField 3"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Checkbox(checked = false, onCheckedChange = {})
        TextButton(onClick = {}) { Text("Button") }
        Checkbox(checked = false, onCheckedChange = {})
        BasicTextField(
            state = rememberTextFieldState("TextField 4"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        TextButton(onClick = {}) { Text("Button") }
        BasicTextField(
            state = rememberTextFieldState("TextField 5"),
            lineLimits = TextFieldLineLimits.SingleLine
        )
        BasicTextField(
            state = rememberTextFieldState("TextField 6"),
            lineLimits = TextFieldLineLimits.SingleLine
        )
        Box(
            modifier = Modifier.clickable(onClick = {}),
        ) {
            Text("clickable Box")
        }
        BasicTextField(
            state = rememberTextFieldState("TextField 7"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        BasicTextField(
            state = rememberTextFieldState("TextField 8"),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabFocusWithInterop() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = {}) { Text("Compose Button 1") }

        HtmlElementView(
            factory = {
                (document.createElement("button") as HTMLButtonElement).apply {
                    textContent = "HTML Button 1"
                }
            },
            modifier = Modifier.width(200.dp).height(40.dp)
        )

        TextButton(onClick = {}) { Text("Compose Button 2") }

        HtmlElementView(
            factory = {
                (document.createElement("button") as HTMLButtonElement).apply {
                    textContent = "HTML Button 2"
                }
            },
            modifier = Modifier.width(200.dp).height(40.dp)
        )

        TextButton(onClick = {}) { Text("Compose Button 3") }

        HtmlElementView(
            factory = {
                (document.createElement("button") as HTMLButtonElement).apply {
                    textContent = "HTML Button 3"
                }
            },
            modifier = Modifier.width(200.dp).height(40.dp)
        )
    }
}