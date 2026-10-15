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

package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect

@InternalComposeUiApi
class WebTextToolbar : TextToolbar {
    @Immutable
    data class TextToolbarMenu(
        val rect: Rect,
        val onCopyRequested: (() -> Unit)?,
        val onPasteRequested: (() -> Unit)?,
        val onCutRequested: (() -> Unit)?,
        val onSelectAllRequested: (() -> Unit)?
    )

    /** Installed by foundation and invoked from the window composition. */
    var renderer by mutableStateOf<(@Composable (TextToolbarMenu?) -> Unit)?>(null)

    private var menu by mutableStateOf<TextToolbarMenu?>(null)

    @Composable
    internal fun Render() {
        DisposableEffect(this) {
            onDispose { hide() }
        }
        renderer?.invoke(menu)
    }

    override val status: TextToolbarStatus
        get() = if (menu == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun hide() {
        menu = null
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menu = TextToolbarMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
        )
    }
}
