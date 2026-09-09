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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// https://youtrack.jetbrains.com/issue/CMP-10745/Web-A11y.-Entered-text-value-is-not-updated-inside-the-TextFields-div
@Composable
fun A11YTextValueNotUpdated() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var textState by remember { mutableStateOf("") }
        TextField(
            value = textState,
            onValueChange = { textState = it },
            label = { Text("Paste here") },
            modifier = Modifier.fillMaxWidth().testTag("pasteField")
        )

        var text by remember { mutableStateOf("") }
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Paste here") },
            modifier = Modifier.testTag("textFieldTag"),
        )

        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Paste here") },
            trailingIcon = {
                IconButton(
                    onClick = { text = "" },
                    modifier = Modifier.testTag("clearIconTag"),
                ) {
                    Text("Clear")
                }
            },
            modifier = Modifier.testTag("textFieldTag2"),
        )

        var password by remember { mutableStateOf("12345678") }
        var showPassword by remember { mutableStateOf(false) }

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.testTag("passwordIconTag"),
                ) {
                    Text(if (showPassword) "Hide" else "Show")
                }
            },
            modifier = Modifier.testTag("passwordFieldTag")
                .semantics { password(isPasswordObfuscated = !showPassword) },
        )

    }
}
