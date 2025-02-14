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

import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import org.jetbrains.compose.storytale.story

val `PrimaryButton default state` by story {
    val enabled by parameter(true)
    val text by parameter("Primary button")
    Button(enabled = enabled, onClick = {}) {
        Text(text)
    }
}

val `Checkbox` by story {
    val checked by parameter(false)
    Checkbox(checked = checked, onCheckedChange = {})
}