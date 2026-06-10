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

package androidx.compose.foundation.text.input.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Job

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun createLegacyPlatformTextInputServiceAdapter():
    LegacyPlatformTextInputServiceAdapter {
    return object : LegacyPlatformTextInputServiceAdapter() {
        private var job: Job? = null

        private var textFieldValue by mutableStateOf(TextFieldValue())
        private var textLayoutResult by mutableStateOf<TextLayoutResult?>(null)
        private var textToRootMatrix by mutableStateOf(Matrix())
        private var focusedRectInRoot by mutableStateOf(Rect.Zero)
        private var textFieldRectInRoot by mutableStateOf(Rect.Zero)
        private var textClippingRectInRoot by mutableStateOf(Rect.Zero)
        private var unclippedTextOffsetInRoot by mutableStateOf(Offset.Zero)

        override fun startInput(
            value: TextFieldValue,
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit
        ) {
            textFieldValue = value
            val node = textInputModifierNode ?: return

            job = node.launchTextInputSession {
                // The legacy text field produces the generic request; the per-target dispatch adapts it
                // to the active session's platform request (desktop sessions are typed). See
                // [startPlatformTextInputMethod].
                startPlatformTextInputMethod(
                    makeRequest(
                        imeOptions = imeOptions,
                        onEditCommand = onEditCommand,
                        onImeActionPerformed = onImeActionPerformed
                    )
                )
            }
        }

        override fun stopInput() {
            job?.cancel()
            job = null
            textFieldValue = TextFieldValue()
        }

        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
            this.textFieldValue = newValue
        }

        override fun updateTextLayoutResult(
            textFieldValue: TextFieldValue,
            offsetMapping: OffsetMapping,
            textLayoutResult: TextLayoutResult,
            textFieldToRootTransform: (Matrix) -> Unit,
            innerTextFieldBounds: Rect,
            decorationBoxBounds: Rect
        ) {
            this.textFieldValue = textFieldValue
            this.textLayoutResult = textLayoutResult

            val matrix = Matrix().also { textFieldToRootTransform(it) }
            textToRootMatrix = matrix
            textFieldRectInRoot = matrix.map(decorationBoxBounds)
            textClippingRectInRoot = matrix.map(innerTextFieldBounds)
            val cursorOffset = offsetMapping.originalToTransformed(textFieldValue.selection.max)
            focusedRectInRoot = matrix.map(textLayoutResult.getCursorRect(cursorOffset))
            unclippedTextOffsetInRoot = textClippingRectInRoot.topLeft - innerTextFieldBounds.topLeft
        }

        override fun startStylusHandwriting() {}

        private fun makeRequest(
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit
        ): SkikoPlatformTextInputMethodRequest {
            val textEditorState = object : TextEditorState {
                override val selection: TextRange get() = textFieldValue.selection
                override val composition: TextRange? get() = textFieldValue.composition
                override val length: Int get() = textFieldValue.text.length
                override fun get(index: Int): Char = textFieldValue.text[index]
                override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                    textFieldValue.text.subSequence(startIndex, endIndex)
            }

            val editBlock: (block: TextEditingScope.() -> Unit) -> Unit = { block ->
                val commands = mutableListOf<EditCommand>()
                with(TextEditingScope(commands)) {
                    block()
                    onEditCommand(commands)
                }
            }

            fun firstTextRangeAndRectInRoot(range: TextRange): Pair<TextRange, Rect> {
                val layoutResult = textLayoutResult ?: return range to Rect.Zero
                val line = layoutResult.getLineForOffset(range.start)
                val lineEnd = layoutResult.getLineEnd(line, visibleEnd = true)
                val clampedEnd = range.end.coerceAtMost(lineEnd)
                val firstRange = TextRange(range.start, clampedEnd)
                val rect = Rect(
                    left = layoutResult.getHorizontalPosition(
                        offset = range.start,
                        usePrimaryDirection = true,
                    ),
                    top = layoutResult.getLineTop(line),
                    right = layoutResult.getHorizontalPosition(
                        offset = clampedEnd,
                        usePrimaryDirection = true,
                    ),
                    bottom = layoutResult.getLineBottom(line),
                )
                return firstRange to textToRootMatrix.map(rect)
            }

            fun characterIndexAtOffsetInRoot(offsetInRoot: Offset): Int {
                val layoutResult = textLayoutResult ?: return 0
                val inverse = Matrix(textToRootMatrix.values.copyOf()).also { it.invert() }
                val local = inverse.map(offsetInRoot)
                return layoutResult.getOffsetForPosition(local)
            }

            return SkikoPlatformTextInputMethodRequest(
                value = { textFieldValue },
                state = textEditorState,
                imeOptions = imeOptions,
                onEditCommand = onEditCommand,
                onImeAction = onImeActionPerformed,
                textLayoutResult = { textLayoutResult },
                focusedRectInRoot = { focusedRectInRoot },
                textFieldRectInRoot = { textFieldRectInRoot },
                textClippingRectInRoot = { textClippingRectInRoot },
                unclippedTextOffsetInRoot = { unclippedTextOffsetInRoot },
                firstTextRangeAndRectInRoot = ::firstTextRangeAndRectInRoot,
                characterIndexAtOffsetInRoot = ::characterIndexAtOffsetInRoot,
                editText = editBlock
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun TextEditingScope(commands: MutableList<EditCommand>) = object : TextEditingScope {
    override fun deleteSurroundingTextInCodePoints(
        lengthBeforeCursor: Int,
        lengthAfterCursor: Int
    ) {
        commands.add(
            DeleteSurroundingTextCommand(lengthBeforeCursor, lengthAfterCursor)
        )
    }

    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int
    ) {
        commands.add(
            CommitTextCommand(text.toString(), newCursorPosition)
        )
    }

    override fun setComposingText(
        text: CharSequence,
        newCursorPosition: Int
    ) {
        commands.add(
            SetComposingTextCommand(text.toString(), newCursorPosition)
        )
    }

    override fun finishComposingText() {
        commands.add(
            FinishComposingTextCommand()
        )
    }

    override fun setComposition(composition: TextRange) {
        commands.add(
            SetComposingRegionCommand(composition.start, composition.end)
        )
    }

    override fun setSelection(selection: TextRange) {
        commands.add(
            SetSelectionCommand(selection.start, selection.end)
        )
    }
}