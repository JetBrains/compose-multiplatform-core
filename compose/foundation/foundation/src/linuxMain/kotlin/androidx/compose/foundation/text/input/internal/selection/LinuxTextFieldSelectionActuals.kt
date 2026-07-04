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

package androidx.compose.foundation.text.input.internal.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.TextDragObserver
import androidx.compose.foundation.text.selection.MouseSelectionObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.foundation.text.input.internal.TextLayoutState
import androidx.compose.foundation.text.input.internal.TransformedTextFieldState
import kotlinx.coroutines.CoroutineScope

internal actual fun textFieldMagnifierNode(
    textFieldState: TransformedTextFieldState,
    textFieldSelectionState: TextFieldSelectionState,
    textLayoutState: TextLayoutState,
    visible: Boolean
): TextFieldMagnifierNode {
    return object : TextFieldMagnifierNode() {
        override fun update(
            textFieldState: TransformedTextFieldState,
            textFieldSelectionState: TextFieldSelectionState,
            textLayoutState: TextLayoutState,
            visible: Boolean
        ) {}
    }
}

internal actual suspend fun TextFieldSelectionState.detectTextFieldTapGestures(
    pointerInputScope: PointerInputScope,
    interactionSource: MutableInteractionSource?,
    requestFocus: () -> Unit,
    showKeyboard: () -> Unit,
) = defaultDetectTextFieldTapGestures(pointerInputScope, interactionSource, requestFocus, showKeyboard)

internal actual suspend fun TextFieldSelectionState.textFieldSelectionGestures(
    pointerInputScope: PointerInputScope,
    mouseSelectionObserver: MouseSelectionObserver,
    textDragObserver: TextDragObserver
) = pointerInputScope.defaultTextFieldSelectionGestures(mouseSelectionObserver, textDragObserver)

internal actual fun Modifier.addBasicTextFieldTextContextMenuComponents(
    state: TextFieldSelectionState,
    coroutineScope: CoroutineScope
): Modifier = this

internal actual class ClipboardPasteState actual constructor(private val clipboard: Clipboard) {
    actual val hasText = true
    actual val hasClip = true
    actual suspend fun update() {}
}
