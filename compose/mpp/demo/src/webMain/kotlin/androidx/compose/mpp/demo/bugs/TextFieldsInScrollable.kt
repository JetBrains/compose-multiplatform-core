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

package androidx.compose.mpp.demo.bugs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

// https://youtrack.jetbrains.com/issue/CMP-7836/Compose-1.8.0-beta01-weird-scroll-behavior
@Composable
fun TextFieldsInTallScrollableContainer() {
    MaterialTheme {
        val verticalScrollState = rememberScrollState()
        Column(modifier = Modifier
            .verticalScroll(verticalScrollState)
            .fillMaxSize()
            .background(Color.LightGray)
            .padding(vertical = 50.dp, horizontal = 10.dp)
        ) {
            var firstTextFieldValue by remember { mutableStateOf(TextFieldValue("first")) }
            var secondTextFieldValue by remember { mutableStateOf(TextFieldValue("second")) }
            TextField(
                enabled = false,
                value = firstTextFieldValue,
                onValueChange = { firstTextFieldValue = it }
            )
            Spacer(modifier = Modifier.height(2000.dp))
            TextField(
                enabled = true,
                value = secondTextFieldValue,
                onValueChange = { secondTextFieldValue = it }
            )
            Spacer(modifier = Modifier.height(200.dp))
        }
    }
}
