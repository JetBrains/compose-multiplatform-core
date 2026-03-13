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

package androidx.compose.foundation.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionAdjustment
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.foundation.text.selection.awaitSelectionGestures
import androidx.compose.foundation.text.selection.getTextFieldSelectionLayout
import androidx.compose.foundation.text.selection.isSelectionHandleInVisibleBound
import androidx.compose.foundation.text.selection.updateSelectionTouchMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue

internal fun Modifier.cupertinoTextFieldPointer(
    manager: TextFieldSelectionManager,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    state: LegacyTextFieldState,
    focusRequester: FocusRequester,
    readOnly: Boolean,
    offsetMapping: OffsetMapping
): Modifier = if (enabled) {
    this.composed {
        val currentState by rememberUpdatedState(state)
        val currentOffsetMapping by rememberUpdatedState(offsetMapping)
        val currentManager by rememberUpdatedState(manager)
        val currentFocusRequester by rememberUpdatedState(focusRequester)
        val currentReadOnly by rememberUpdatedState(readOnly)

        val tapHandlerModifier = tapPressTextFieldModifier(interactionSource, enabled) { offset ->
            if (currentState.hasFocus) {
                // To show keyboard if it was hidden. Even in selection mode (like native)
                requestFocusAndShowKeyboardIfNeeded(
                    currentState,
                    currentFocusRequester,
                    !currentReadOnly
                )
                if (currentState.handleState != HandleState.Selection) {
                    currentState.layoutResult?.let { layoutResult ->
                        TextFieldDelegate.cupertinoSetCursorOffsetFocused(
                            position = offset,
                            textLayoutResult = layoutResult,
                            editProcessor = currentState.processor,
                            offsetMapping = currentOffsetMapping,
                            showContextMenu = { show ->
                                // it shouldn't be selection, but this is a way to call a context menu in BasicTextField
                                if (show) {
                                    currentManager.enterSelectionMode()
                                } else {
                                    currentManager.exitSelectionMode()
                                }
                            },
                            onValueChange = currentState.onValueChange
                        )
                    }
                } else {
                    currentManager.deselect(offset)
                }
            } else {
                requestFocusAndShowKeyboardIfNeeded(
                    currentState,
                    currentFocusRequester,
                    !currentReadOnly
                )
                currentState.layoutResult?.let { layoutResult ->
                    TextFieldDelegate.setCursorOffset(
                        offset,
                        layoutResult,
                        currentState.processor,
                        currentOffsetMapping,
                        currentState.onValueChange
                    )
                }
            }
            if (currentState.textDelegate.text.isNotEmpty()) {
                currentState.handleState = HandleState.Cursor
            }
        }

        val longPressDragObserver = remember {
            object : TextDragObserver {
                var dragTotalDistance = Offset.Zero
                var dragBeginOffset = Offset.Zero
                var shouldUpdateMagnifierPosition = false

                override fun onStart(startPoint: Offset, selectionAdjustment: SelectionAdjustment) {
                    shouldUpdateMagnifierPosition = selectionAdjustment == SelectionAdjustment.None
                    if (shouldUpdateMagnifierPosition) {
                        currentManager.draggingHandle = Handle.SelectionEnd
                        currentManager.currentDragPosition = startPoint
                    } else {
                        currentManager.draggingHandle = null
                        currentManager.currentDragPosition = null
                    }

                    currentManager.hapticFeedBack?.performHapticFeedback(HapticFeedbackType.LongPress)

                    currentState.layoutResult?.let { layoutResult ->
                        TextFieldDelegate.setCursorOffset(
                            startPoint,
                            layoutResult,
                            currentState.processor,
                            currentOffsetMapping,
                            currentState.onValueChange
                        )
                        if (selectionAdjustment != SelectionAdjustment.None) {
                            currentManager.doRepeatingTapSelection(startPoint, selectionAdjustment)
                        }
                        dragBeginOffset = startPoint
                    }
                    dragTotalDistance = Offset.Zero
                }

                override fun onDrag(delta: Offset) {
                    dragTotalDistance += delta
                    currentState.layoutResult?.let { layoutResult ->
                        val currentDragPosition = dragBeginOffset + dragTotalDistance
                        if (shouldUpdateMagnifierPosition) {
                            currentManager.currentDragPosition = currentDragPosition
                        }
                        TextFieldDelegate.setCursorOffset(
                            currentDragPosition,
                            layoutResult,
                            currentState.processor,
                            currentOffsetMapping,
                            currentState.onValueChange
                        )
                    }
                }

                // Unnecessary here
                override fun onDown(point: Offset) {}

                override fun onUp() {}

                override fun onStop() {
                    shouldUpdateMagnifierPosition = false
                    currentManager.draggingHandle = null
                    currentManager.currentDragPosition = null
                }

                override fun onCancel() {
                    shouldUpdateMagnifierPosition = false
                    currentManager.draggingHandle = null
                    currentManager.currentDragPosition = null
                }
            }
        }

        this.updateSelectionTouchMode { currentState.isInTouchMode = it }
            .then(tapHandlerModifier)
            .pointerInput(manager.mouseSelectionObserver, longPressDragObserver) {
                awaitSelectionGestures(
                    manager.mouseSelectionObserver,
                    longPressDragObserver,
                )
            }
            .pointerHoverIcon(PointerIcon.Text)
    }
} else {
    this
}

private fun TextFieldSelectionManager.doRepeatingTapSelection(
    touchPointOffset: Offset,
    selectionAdjustment: SelectionAdjustment
) {
    if (value.text.isEmpty()) return
    enterSelectionMode()
    updateSelection(
        value = value,
        currentPosition = touchPointOffset,
        isStartOfSelection = true,
        isStartHandle = false,
        adjustment = selectionAdjustment
    )
}

/**
 * Copied from TextFieldSelectionManager.kt
 */
private fun TextFieldSelectionManager.updateSelection(
    value: TextFieldValue,
    currentPosition: Offset,
    isStartOfSelection: Boolean,
    isStartHandle: Boolean,
    adjustment: SelectionAdjustment
) {
    val layoutResult = state?.layoutResult ?: return
    val previousTransformedSelection = TextRange(
        offsetMapping.originalToTransformed(value.selection.start),
        offsetMapping.originalToTransformed(value.selection.end)
    )

    val currentOffset = layoutResult.getOffsetForPosition(
        position = currentPosition,
        coerceInVisibleBounds = false
    )

    val rawStartHandleOffset = if (isStartHandle || isStartOfSelection) currentOffset else
        previousTransformedSelection.start

    val rawEndHandleOffset = if (!isStartHandle || isStartOfSelection) currentOffset else
        previousTransformedSelection.end

    val previousSelectionLayout = previousSelectionLayout // for smart cast
    val rawPreviousHandleOffset = if (
        isStartOfSelection ||
        previousSelectionLayout == null ||
        previousRawDragOffset == -1
    ) {
        -1
    } else {
        previousRawDragOffset
    }

    val selectionLayout = getTextFieldSelectionLayout(
        layoutResult = layoutResult.value,
        rawStartHandleOffset = rawStartHandleOffset,
        rawEndHandleOffset = rawEndHandleOffset,
        rawPreviousHandleOffset = rawPreviousHandleOffset,
        previousSelectionRange = previousTransformedSelection,
        isStartOfSelection = isStartOfSelection,
        isStartHandle = isStartHandle,
    )

    if (!selectionLayout.shouldRecomputeSelection(previousSelectionLayout)) {
        return
    }

    this.previousSelectionLayout = selectionLayout
    previousRawDragOffset = currentOffset

    val newTransformedSelection = adjustment.adjust(selectionLayout)

    val originalSelection = TextRange(
        start = offsetMapping.transformedToOriginal(newTransformedSelection.start.offset),
        end = offsetMapping.transformedToOriginal(newTransformedSelection.end.offset)
    )
    if (originalSelection == value.selection) return

    hapticFeedBack?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    val newValue = createTextFieldValue(
        annotatedString = value.annotatedString,
        selection = originalSelection
    )
    onValueChange(newValue)

    // showSelectionHandleStart/End might be set to false when scrolled out of the view.
    // When the selection is updated, they must also be updated so that handles will be shown
    // or hidden correctly.
    state?.showSelectionHandleStart = isSelectionHandleInVisibleBound(true)
    state?.showSelectionHandleEnd = isSelectionHandleInVisibleBound(false)
}

/**
 * Copied from TextFieldSelectionManager.kt
 */
private fun createTextFieldValue(
    annotatedString: AnnotatedString,
    selection: TextRange
): TextFieldValue {
    return TextFieldValue(
        annotatedString = annotatedString,
        selection = selection
    )
}
