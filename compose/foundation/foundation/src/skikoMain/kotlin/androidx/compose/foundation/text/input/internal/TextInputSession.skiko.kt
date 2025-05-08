/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.text.input.internal

import androidx.compose.foundation.content.internal.ReceiveContentConfiguration
import androidx.compose.foundation.text.computeSizeForDefaultText
import androidx.compose.foundation.text.focusedRectInRoot
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldCharSequence
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.setSelectionCoerced
import androidx.compose.foundation.text.offsetByCodePoints
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun PlatformTextInputSession.platformSpecificTextInputSession(
    state: TransformedTextFieldState,
    layoutState: TextLayoutState,
    imeOptions: ImeOptions,
    receiveContentConfiguration: ReceiveContentConfiguration?,
    onImeAction: ((ImeAction) -> Unit)?,
    updateSelectionState: (() -> Unit)?,
    stylusHandwritingTrigger: MutableSharedFlow<Unit>?,
    viewConfiguration: ViewConfiguration?
): Nothing {
    val editProcessor = EditProcessor()
    fun onEditCommand(commands: List<EditCommand>) {
        editProcessor.reset(
            value = state.untransformedText.toTextFieldValue(),
            textInputSession = null
        )
        val newValue = editProcessor.apply(commands)

        state.editUntransformedTextAsUser(restartImeIfContentChanges = false) {
            // Update text
            replace(0, length, newValue.text)

            // Update selection
            val selection = newValue.selection
            setSelectionCoerced(selection.start, selection.end)

            // Update composition
            val composition = newValue.composition
            if (composition == null) {
                commitComposition()
            } else {
                setComposition(composition.start, composition.end)
            }
        }
    }

    fun editText(block: TextEditingScope.() -> Unit) {
        state.editUntransformedTextAsUser(restartImeIfContentChanges = false) {
            with(TextEditingScope(this)) {
                block()
            }
        }
    }

    coroutineScope {
        val outputValueFlow = callbackFlow {
            state.collectImeNotifications { _, _, _ ->
                // SkikoPlatformTextInputMethodRequest should work with an untransformed text on all platforms
                // This updates platform text input services after changing the state with latest value in onEditCommand
                trySend(state.untransformedText.toTextFieldValue())
            }
        }

        val focusedRectInRootFlow = snapshotFlow {
            val layoutResult = layoutState.layoutResult ?: return@snapshotFlow null
            val layoutCoords = layoutState.textLayoutNodeCoordinates ?: return@snapshotFlow null
            focusedRectInRoot(
                layoutResult = layoutResult,
                focusOffset = state.visualText.selection.max,
                sizeForDefaultText = {
                    layoutResult.layoutInput.let {
                        computeSizeForDefaultText(it.style, it.density, it.fontFamilyResolver)
                    }
                },
                convertLocalToRoot = layoutCoords::localToRoot,
            )
        }.filterNotNull()

        val textFieldRectInRoot = snapshotFlow {
            layoutState.decoratorNodeCoordinates?.boundsInRoot()
        }.filterNotNull()

        val textClippingRectInRoot = snapshotFlow {
            layoutState.coreNodeCoordinates?.boundsInRoot()
        }.filterNotNull()

        startInputMethod(
            SkikoPlatformTextInputMethodRequest(
                value = { state.untransformedText.toTextFieldValue() },
                state = state::untransformedText.asTextEditorState(),
                imeOptions = imeOptions,
                onEditCommand = ::onEditCommand,
                onImeAction = onImeAction,
                outputValue = outputValueFlow,
                textLayoutResult = snapshotFlow(layoutState::layoutResult).filterNotNull(),
                focusedRectInRoot = focusedRectInRootFlow,
                textFieldRectInRoot = textFieldRectInRoot,
                textClippingRectInRoot = textClippingRectInRoot,
                editText = ::editText
            )
        )
    }
}

private fun TextFieldCharSequence.toTextFieldValue() =
    TextFieldValue(toString(), selection, composition)

@OptIn(ExperimentalComposeUiApi::class)
private inline fun (() -> TextFieldCharSequence).asTextEditorState() = object : TextEditorState {

    override val length: Int
        get() = this@asTextEditorState().length

    override fun get(index: Int): Char = this@asTextEditorState()[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return this@asTextEditorState().subSequence(startIndex, endIndex)
    }

    override val selection: TextRange
        get() = this@asTextEditorState().selection

    override val composition: TextRange?
        get() = this@asTextEditorState().composition

    override fun toString(): String = this@asTextEditorState().toString()

}

@OptIn(ExperimentalComposeUiApi::class)
private fun TextEditingScope(buffer: TextFieldBuffer) = object : TextEditingScope {

    private var TextFieldBuffer.cursor: Int
        get() = if (selection.collapsed) selection.end else -1
        set(value) {
            setSelectionCoerced(value, value)
        }

    override fun deleteSurroundingTextInCodePoints(
        lengthBeforeCursor: Int,
        lengthAfterCursor: Int
    ) {
        val charSequence = buffer.asCharSequence()
        val selection = buffer.selection
        buffer.delete(
            start = selection.end,
            end = charSequence.offsetByCodePoints(
                index = selection.end,
                offset = lengthAfterCursor
            )
        )
        buffer.delete(
            start = charSequence.offsetByCodePoints(
                index = selection.start,
                offset = -lengthBeforeCursor
            ),
            end = selection.start
        )
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int) {
        // API description says replace ongoing composition text if there. Then, if there is no
        // composition text, insert text into cursor position or replace selection.
        val replacementRange = buffer.composition ?: buffer.selection
        buffer.replace(replacementRange.start, replacementRange.end, text)

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See API description for the meaning of newCursorPosition.
        val newCursorInBuffer =
            if (newCursorPosition > 0) {
                newCursor + newCursorPosition - 1
            } else {
                newCursor + newCursorPosition - text.length
            }
        buffer.setSelectionCoerced(newCursorInBuffer, newCursorInBuffer)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
        val replacementRange = buffer.composition ?: buffer.selection
        // API doc says, if there is ongoing composing text, replace it with new text.
        // If there is no composing text, insert composing text into cursor position with
        // removing selected text if any.
        buffer.replace(replacementRange.start, replacementRange.end, text)
        if (text.isNotEmpty()) {
            buffer.setComposition(replacementRange.start, replacementRange.start + text.length)
        }

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See API description for the meaning of newCursorPosition.
        val newCursorInBuffer =
            if (newCursorPosition > 0) {
                newCursor + newCursorPosition - 1
            } else {
                newCursor + newCursorPosition - text.length
            }

        buffer.cursor = newCursorInBuffer
    }
}


@OptIn(ExperimentalComposeUiApi::class)
internal data class SkikoPlatformTextInputMethodRequest(
    override val value: () -> TextFieldValue,
    override val state: TextEditorState,
    override val imeOptions: ImeOptions,
    override val onEditCommand: (List<EditCommand>) -> Unit,
    override val onImeAction: ((ImeAction) -> Unit)?,
    override val outputValue: Flow<TextFieldValue>,
    override val textLayoutResult: Flow<TextLayoutResult>,
    override val focusedRectInRoot: Flow<Rect>,
    override val textFieldRectInRoot: Flow<Rect>,
    override val textClippingRectInRoot: Flow<Rect>,
    override val editText: (block: TextEditingScope.() -> Unit) -> Unit
): PlatformTextInputMethodRequest
