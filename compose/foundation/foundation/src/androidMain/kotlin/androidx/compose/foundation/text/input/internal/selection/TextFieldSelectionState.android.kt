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
import androidx.compose.foundation.text.TextContextMenuItems.Autofill
import androidx.compose.foundation.text.TextContextMenuItems.Copy
import androidx.compose.foundation.text.TextContextMenuItems.Cut
import androidx.compose.foundation.text.TextContextMenuItems.Paste
import androidx.compose.foundation.text.TextContextMenuItems.SelectAll
import androidx.compose.foundation.text.TextItem
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.modifier.addTextContextMenuComponentsWithResources
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
    TextItem(state, Cut, enabled = availability.canCut) { onMenuItemClicked(Cut) }
    TextItem(state, Copy, enabled = availability.canCopy) { onMenuItemClicked(Copy) }
    TextItem(state, Paste, enabled = availability.canPaste) { onMenuItemClicked(Paste) }
    TextItem(state, SelectAll, enabled = availability.canSelectAll) { onMenuItemClicked(SelectAll) }
    if (Build.VERSION.SDK_INT >= 26) {
        TextItem(state, Autofill, enabled = availability.canAutofill) {
            onMenuItemClicked(Autofill)
        }
    }
}

// TODO(halilibo): Add a new TextToolbar option "paste as plain text".
internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
    state: TextFieldSelectionState,
    coroutineScope: CoroutineScope,
): Modifier = addTextContextMenuComponentsWithResources { resources ->
    fun TextContextMenuBuilderScope.textFieldItem(
        item: TextContextMenuItems,
        desiredState: TextToolbarState = TextToolbarState.None,
        closePredicate: (() -> Boolean)? = null,
        onClick: () -> Unit
    ) {
        item(
            key = item.key,
            label = item.resolveString(resources),
            leadingIcon = item.drawableId,
            onClick = {
                onClick()
                if (closePredicate?.invoke() ?: true) close()
                state.updateTextToolbarState(desiredState)
            }
        )
    }

    fun TextContextMenuBuilderScope.textFieldSuspendItem(
        item: TextContextMenuItems,
        onClick: suspend () -> Unit
    ) {
        textFieldItem(item) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { onClick() }
        }
    }

    separator()
    if (state.canCut()) {
        textFieldSuspendItem(Cut) { state.cut() }
    }
    if (state.canCopy()) {
        textFieldSuspendItem(Copy) { state.copy(cancelSelection = state.textToolbarShown) }
    }
    if (state.canPaste()) {
        textFieldSuspendItem(Paste) { state.paste() }
    }
    if (state.canSelectAll()) {
        textFieldItem(
            item = SelectAll,
            desiredState = TextToolbarState.Selection,
            closePredicate = { !state.textToolbarShown },
        ) {
            state.selectAll()
            state.updateTextToolbarState(TextToolbarState.Selection)
        }
    }
    if (Build.VERSION.SDK_INT >= 26 && state.canAutofill()) {
        textFieldItem(Autofill) { state.autofill() }
    }
    separator()
}
