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

package androidx.compose.foundation.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush

// CoreTextField.kt
internal actual fun Modifier.textFieldCursor(
    state: LegacyTextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping,
    cursorBrush: Brush,
    showCursor: Boolean,
): Modifier = cursor(state, value, offsetMapping, cursorBrush, showCursor)

internal actual fun Modifier.textFieldDraw(
    state: LegacyTextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping,
): Modifier = defaultTextFieldDraw(state, value, offsetMapping)

// KeyMapping.kt
internal actual val platformDefaultKeyMapping: KeyMapping = defaultKeyMapping

// TextFieldKeyInput.kt
internal actual val KeyEvent.isTypedEvent: Boolean
    get() = type == KeyEventType.KeyDown &&
        !isISOControl(utf16CodePoint) &&
        !isCtrlPressed

private fun isISOControl(codePoint: Int): Boolean =
    codePoint in 0x00..0x1F ||
    codePoint in 0x7F..0x9F

// TextFieldPointerModifier.common.kt
@Composable
internal actual fun Modifier.textFieldPointer(
    manager: TextFieldSelectionManager,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    state: LegacyTextFieldState,
    focusRequester: FocusRequester,
    readOnly: Boolean,
    offsetMapping: OffsetMapping
): Modifier = defaultTextFieldPointer(
    manager,
    enabled,
    interactionSource,
    state,
    focusRequester,
    readOnly,
    offsetMapping,
)

// TextFieldScroll.kt
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun rememberTextFieldOverscrollEffect(): OverscrollEffect? = null

internal actual fun Modifier.textFieldScroll(
    scrollerPosition: TextFieldScrollerPosition,
    textFieldValue: TextFieldValue,
    visualTransformation: VisualTransformation,
    overscrollEffect: OverscrollEffect?,
    textLayoutResultProvider: () -> TextLayoutResultProxy?
): Modifier = defaultTextFieldScroll(
    scrollerPosition,
    textFieldValue,
    visualTransformation,
    overscrollEffect,
    textLayoutResultProvider,
)

// TouchMode.kt
internal actual val isInTouchMode = false
