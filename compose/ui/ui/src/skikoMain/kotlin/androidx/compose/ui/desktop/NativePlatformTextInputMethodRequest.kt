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

package androidx.compose.ui.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A [PlatformTextInputMethodRequest] whose IME interaction is driven entirely through
 * platform-specific methods (e.g. macOS NSTextInputClient callbacks, or Linux/GTK `commitText`)
 * rather than the generic Compose text-field accessors.
 *
 * It provides default implementations for the generic [PlatformTextInputMethodRequest] members so
 * that platform request types (and external editors that implement them) only need to implement
 * their platform-specific surface. These defaults throw because they are not used on the native
 * desktop path; Compose's own `BasicTextField` reaches the native path through an adapter that
 * supplies the real generic members.
 */
@OptIn(ExperimentalComposeUiApi::class)
interface NativePlatformTextInputMethodRequest : PlatformTextInputMethodRequest {
    override val value: () -> TextFieldValue
        get() = unsupported("value")
    override val state: TextEditorState
        get() = unsupported("state")
    override val imeOptions: ImeOptions
        get() = unsupported("imeOptions")
    override val onEditCommand: (List<EditCommand>) -> Unit
        get() = unsupported("onEditCommand")
    override val onImeAction: ((ImeAction) -> Unit)?
        get() = null
    override val textLayoutResult: () -> TextLayoutResult?
        get() = { null }
    override val focusedRectInRoot: () -> Rect?
        get() = { null }
    override val textFieldRectInRoot: () -> Rect?
        get() = { null }
    override val textClippingRectInRoot: () -> Rect?
        get() = { null }
    override val unclippedTextOffsetInRoot: () -> Offset?
        get() = { null }
    override val firstTextRangeAndRectInRoot: (TextRange) -> Pair<TextRange, Rect>
        get() = unsupported("firstTextRangeAndRectInRoot")
    override val characterIndexAtOffsetInRoot: (Offset) -> Int
        get() = unsupported("characterIndexAtOffsetInRoot")
    override val editText: (block: TextEditingScope.() -> Unit) -> Unit
        get() = unsupported("editText")
}

private fun unsupported(member: String): Nothing =
    throw UnsupportedOperationException(
        "$member is not supported on a NativePlatformTextInputMethodRequest; " +
            "IME is driven through the platform-specific request methods instead."
    )
