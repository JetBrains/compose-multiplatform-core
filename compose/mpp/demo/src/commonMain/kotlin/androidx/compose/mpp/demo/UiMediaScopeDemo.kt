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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.LocalUiMediaScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMediaQueryApi::class)
@Composable
fun UiMediaScopeDemo() {
    val mediaScope = LocalUiMediaScope.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("keyboardKind: ${mediaScope.keyboardKind}")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tap to show keyboard") },
            singleLine = true,
        )
        Button(onClick = {
            keyboardController?.hide()
            focusManager.clearFocus()
        }) {
            Text("Hide keyboard")
        }

        Text("windowWidth: ${mediaScope.windowWidth}")
        Text("windowHeight: ${mediaScope.windowHeight}")
        Text("windowPosture: ${mediaScope.windowPosture}")
        Text("isFlat: ${mediaScope.windowPosture.isFlat}")
        Text("isTabletop: ${mediaScope.windowPosture.isTabletop}")
        Text("pointerPrecision: ${mediaScope.pointerPrecision}")
        Text("hasMicrophone: ${mediaScope.hasMicrophone}")
        Text("hasCamera: ${mediaScope.hasCamera}")
        Text("viewingDistance: ${mediaScope.viewingDistance}")
    }
}
