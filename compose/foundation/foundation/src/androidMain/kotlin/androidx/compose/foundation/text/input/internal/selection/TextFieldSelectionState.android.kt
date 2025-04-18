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

package androidx.compose.foundation.text.input.internal.selection

import android.os.Build
import androidx.compose.foundation.contextmenu.ContextMenuScope
import androidx.compose.foundation.contextmenu.ContextMenuState
import androidx.compose.foundation.text.MenuItemsAvailability
import androidx.compose.foundation.text.TextContextMenuItems
import androidx.compose.foundation.text.TextItem
import androidx.compose.foundation.text.contextmenu.modifier.addTextContextMenuComponentsWithResources
import androidx.compose.foundation.text.textItem
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun TextFieldSelectionState.contextMenuBuilder(
    state: ContextMenuState,
    itemsAvailability: State<MenuItemsAvailability>,
    onMenuItemClicked: TextFieldSelectionState.(TextContextMenuItems) -> Unit
): ContextMenuScope.() -> Unit = {
    val availability: MenuItemsAvailability = itemsAvailability.value
    TextItem(state, TextContextMenuItems.Cut, enabled = availability.canCut) {
        onMenuItemClicked(TextContextMenuItems.Cut)
    }
    TextItem(state, TextContextMenuItems.Copy, enabled = availability.canCopy) {
        onMenuItemClicked(TextContextMenuItems.Copy)
    }
    TextItem(state, TextContextMenuItems.Paste, enabled = availability.canPaste) {
        onMenuItemClicked(TextContextMenuItems.Paste)
    }
    TextItem(state, TextContextMenuItems.SelectAll, enabled = availability.canSelectAll) {
        onMenuItemClicked(TextContextMenuItems.SelectAll)
    }
    if (Build.VERSION.SDK_INT >= 26) {
        TextItem(state, TextContextMenuItems.Autofill, enabled = availability.canAutofill) {
            onMenuItemClicked(TextContextMenuItems.Autofill)
        }
    }
}

// TODO(halilibo): Add a new TextToolbar option "paste as plain text".
internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
    state: TextFieldSelectionState,
    coroutineScope: CoroutineScope,
): Modifier = addTextContextMenuComponentsWithResources { resources ->
    separator()
    if (state.canCut())
        textItem(resources, TextContextMenuItems.Cut) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { state.cut() }
            close()
        }
    if (state.canCopy())
        textItem(resources, TextContextMenuItems.Copy) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                state.copy(cancelSelection = state.textToolbarShown)
            }
            close()
        }
    if (state.canPaste())
        textItem(resources, TextContextMenuItems.Paste) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { state.paste() }
            close()
        }
    if (state.canSelectAll())
        textItem(resources, TextContextMenuItems.SelectAll) {
            state.selectAll()
            if (!state.textToolbarShown) close()
        }
    if (Build.VERSION.SDK_INT >= 26 && state.canAutofill())
        textItem(resources, TextContextMenuItems.Autofill) {
            state.autofill()
            close()
        }
    separator()
}
