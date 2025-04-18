/*
 * Copyright 2021 The Android Open Source Project
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

import android.os.Build
import androidx.compose.foundation.PlatformMagnifierFactory
import androidx.compose.foundation.contextmenu.ContextMenuScope
import androidx.compose.foundation.contextmenu.ContextMenuState
import androidx.compose.foundation.isPlatformMagnifierSupported
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.text.MenuItemsAvailability
import androidx.compose.foundation.text.TextContextMenuItems
import androidx.compose.foundation.text.TextItem
import androidx.compose.foundation.text.contextmenu.modifier.addTextContextMenuComponentsWithResources
import androidx.compose.foundation.text.textItem
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal actual val PointerEvent.isShiftPressed: Boolean
    get() = false

// We use composed{} to read a local, but don't provide inspector info because the underlying
// magnifier modifier provides more meaningful inspector info.
internal actual fun Modifier.textFieldMagnifier(manager: TextFieldSelectionManager): Modifier {
    // Avoid tracking animation state on older Android versions that don't support magnifiers.
    if (!isPlatformMagnifierSupported()) {
        return this
    }

    return composed {
        val density = LocalDensity.current
        var magnifierSize by remember { mutableStateOf(IntSize.Zero) }
        animatedSelectionMagnifier(
            magnifierCenter = { calculateSelectionMagnifierCenterAndroid(manager, magnifierSize) },
            platformMagnifier = { center ->
                Modifier.magnifier(
                    sourceCenter = { center() },
                    onSizeChanged = { size ->
                        magnifierSize =
                            with(density) {
                                IntSize(size.width.roundToPx(), size.height.roundToPx())
                            }
                    },
                    useTextDefault = true,
                    platformMagnifierFactory = PlatformMagnifierFactory.getForCurrentPlatform()
                )
            }
        )
    }
}

internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
    manager: TextFieldSelectionManager,
    coroutineScope: CoroutineScope,
): Modifier = addTextContextMenuComponentsWithResources { resources ->
    separator()
    if (manager.canCut())
        textItem(resources, TextContextMenuItems.Cut) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { manager.cut() }
            close()
        }
    if (manager.canCopy())
        textItem(resources, TextContextMenuItems.Copy) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                manager.copy(cancelSelection = manager.textToolbarShown)
            }
            close()
        }
    if (manager.canPaste())
        textItem(resources, TextContextMenuItems.Paste) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { manager.paste() }
            close()
        }
    if (manager.canSelectAll())
        textItem(resources, TextContextMenuItems.SelectAll) {
            manager.selectAll()
            if (!manager.textToolbarShown) close()
        }
    if (Build.VERSION.SDK_INT >= 26 && manager.canAutofill())
        textItem(resources, TextContextMenuItems.Autofill) {
            manager.autofill()
            close()
        }
    separator()
}

internal fun TextFieldSelectionManager.contextMenuBuilder(
    contextMenuState: ContextMenuState,
    itemsAvailability: State<MenuItemsAvailability>
): ContextMenuScope.() -> Unit = {
    val availability: MenuItemsAvailability = itemsAvailability.value
    TextItem(
        state = contextMenuState,
        label = TextContextMenuItems.Cut,
        enabled = availability.canCut,
    ) {
        cut()
    }
    TextItem(
        state = contextMenuState,
        label = TextContextMenuItems.Copy,
        enabled = availability.canCopy,
    ) {
        copy(cancelSelection = false)
    }
    TextItem(
        state = contextMenuState,
        label = TextContextMenuItems.Paste,
        enabled = availability.canPaste,
    ) {
        paste()
    }
    TextItem(
        state = contextMenuState,
        label = TextContextMenuItems.SelectAll,
        enabled = availability.canSelectAll,
    ) {
        selectAll()
    }
    if (Build.VERSION.SDK_INT >= 26) {
        TextItem(
            state = contextMenuState,
            label = TextContextMenuItems.Autofill,
            enabled = editable && value.selection.collapsed
        ) {
            autofill()
        }
    }
}
