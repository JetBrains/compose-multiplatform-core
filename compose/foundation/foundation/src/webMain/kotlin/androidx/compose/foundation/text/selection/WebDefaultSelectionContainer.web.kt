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

package androidx.compose.foundation.text.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Root selection container for web.
 *
 * Wraps application content so that plain text is selectable by default, while text inside
 * interactive components (buttons, checkboxes, tabs, etc.) is automatically excluded.
 *
 * Unlike the public [SelectionContainer], this does NOT clear [LocalInteractiveAreaRegistry] —
 * it is the one that provides it. Interactive components register their bounds through
 * [AbstractClickableNode], and [SelectionManager] consults the registry before starting
 * selection or performing Select All.
 *
 * Applied automatically at the web platform entry point. Not part of the public API.
 */
@Composable
fun WebDefaultSelectionContainer(content: @Composable () -> Unit) {
    var selection by remember { mutableStateOf<Selection?>(null) }
    val registry = remember { InteractiveAreaRegistryImpl() }
    CompositionLocalProvider(LocalInteractiveAreaRegistry provides registry) {
        // Use the internal SelectionContainer overload directly so that
        // LocalInteractiveAreaRegistry is NOT cleared (unlike the public overload).
        SelectionContainer(
            selection = selection,
            onSelectionChange = { selection = it },
            children = content,
        )
    }
}
