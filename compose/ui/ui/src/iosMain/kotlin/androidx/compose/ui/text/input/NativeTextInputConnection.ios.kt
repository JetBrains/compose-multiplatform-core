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

package androidx.compose.ui.text.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.DpInsets
import androidx.compose.ui.platform.NativeTextEditingDelegate
import androidx.compose.ui.platform.TextLayoutDirection
import androidx.compose.ui.platform.NativeTextInputContextMenuCustomAction
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.toUIColor
import androidx.compose.ui.platform.detachedCopy
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.utils.CMPEditMenuCustomAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.FocusedViewsList
import androidx.compose.ui.window.NativeTextInputScrollView
import androidx.compose.ui.window.NativeTextInputView
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIView

internal class NativeTextInputConnection(
    private var inactiveTextInputDelegate: NativeTextEditingDelegate,
    updateView: () -> Unit,
    coroutineScope: CoroutineScope,
    focusedViewsList: FocusedViewsList?,
    focusManager: () -> ComposeSceneFocusManager?
) : TextInputConnection(
    updateView = updateView,
    coroutineScope = coroutineScope,
    focusedViewsList = focusedViewsList,
    focusManager = focusManager
), NativeTextEditingDelegate {
    override val isInteractive: Boolean = true

    private val scrollView by lazy { NativeTextInputScrollView() }

    override val textInputView = NativeTextInputView(initialInput = inactiveTextInputDelegate).also {
        scrollView.textView = it
    }

    override val rootView: UIView get() = scrollView

    override fun start(request: PlatformTextInputMethodRequest) {
        super.start(request)

        textInputView.input = this

        textInputView.resignFirstResponder()
        textInputView.becomeFirstResponder()
        setupTintColor()
        onViewGeometryUpdated()
    }

    override fun stop() {
        super.stop()

        textInputView.input = inactiveTextInputDelegate
    }

    override fun dispose() {
        super.dispose()

        // Keep answering for the text that was here, without holding the text field alive.
        inactiveTextInputDelegate = inactiveTextInputDelegate.detachedCopy()
        textInputView.input = inactiveTextInputDelegate
    }

    override fun stateWillChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textInputView.textWillChange()
        }
        if (selectionChanged) {
            textInputView.selectionWillChange()
        }
    }

    override fun stateDidChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textInputView.textDidChange()
        }
        if (selectionChanged) {
            textInputView.selectionDidChange()
        }
    }

    override fun onTextFieldValueUpdated(newValue: TextFieldValue) {
        super.onTextFieldValueUpdated(newValue)

        // textLayoutResult might be updated after the text fiend value change
        onViewGeometryUpdated()
    }

    override fun onViewGeometryUpdated() {
        val rect = textFieldRectInRoot ?: return
        val offset = unclippedTextOffsetInRoot ?: return
        // Since Compose content is rendered on a MetalView and the UITextInput-implementing
        // view is overlayed on top of it, we need to synchronize the Compose text
        // field with the IntermediateTextScrollView (which contains IntermediateUITextView)
        // to ensure native iOS text input controls
        // align correctly with the rendered text.
        val layoutResult = textLayoutResult ?: return

        val contentBounds = calculateContentBounds(layoutResult, rect, offset)
        val contentInsets = calculateContentInsets(rect, contentBounds)
        scrollView.setFrame(
            rect.toDpRect(rootView.density),
            contentBounds.toDpRect(rootView.density),
            contentInsets
        )
    }

    private fun calculateContentBounds(textLayoutResult: TextLayoutResult, textFieldFrame: Rect, unclippedTextPosition: Offset): Rect {
        val textSize = textLayoutResult.size.toSize()
        val contentBounds = Rect(
            offset = Offset(x = textFieldFrame.left - unclippedTextPosition.x, y = textFieldFrame.top - unclippedTextPosition.y),
            size = textSize
        )
        return contentBounds
    }

    private fun calculateContentInsets(textFieldFrame: Rect, contentBounds: Rect): DpInsets =
        with(rootView.density) {
            DpInsets(
                left = max(0f, -contentBounds.left).toDp(),
                top = max(0f, -contentBounds.top).toDp(),
                right = max(
                    0f,
                    textFieldFrame.width - contentBounds.width + contentBounds.left
                ).toDp(),
                bottom = max(
                    0f,
                    textFieldFrame.height - contentBounds.height + contentBounds.top
                ).toDp()
            )
        }

    override fun caretDpRectForPosition(position: Int): DpRect? {
        val text = currentTextFieldValue?.text ?: return null
        if (position < 0 || position > text.length) {
            return null
        }
        val currentTextLayoutResult = textLayoutResult ?: return null
        if (position > currentTextLayoutResult.multiParagraph.intrinsics.annotatedString.length) {
            return null
        }
        val rect = currentTextLayoutResult.getCursorRect(position)
        return rect.toDpRect(rootView.density).let {
            val halfWidth = CURSOR_THICKNESS / 2
            val center = (it.left + it.right) / 2
            it.copy(left = center - halfWidth, right = center + halfWidth)
        }
    }

    override fun closestPositionToPoint(point: DpOffset): Int? {
        return textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
    }

    override fun closestPositionToPoint(point: DpOffset, withinRange: TextRange): Int? {
        val pointOffset =
            textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
                ?: return null
        return pointOffset.coerceIn(withinRange.start, withinRange.end)
    }

    override fun characterRangeAtPoint(point: DpOffset): TextRange? {
        val pointOffset =
            textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
                ?: return null
        return textLayoutResult?.getWordBoundary(pointOffset)
    }

    override fun positionWithinRange(
        range: TextRange,
        farthestInDirection: TextLayoutDirection
    ): Int? {
        if (isIncorrect(range)) return null
        return when (farthestInDirection) {
            TextLayoutDirection.Up -> range.start
            TextLayoutDirection.Down -> range.end
            else -> {
                val layout = textLayoutResult ?: return null
                val startLine = layout.getLineForOffset(range.start)
                val endLine = layout.getLineForOffset(range.end)

                val candidateOffsets = buildSet {
                    add(range.start)
                    add(range.end)

                    for (line in startLine..endLine) {
                        add(max(range.start, layout.getLineStart(line)))
                        add(min(range.end, layout.getLineEnd(line)))
                    }
                }

                when (farthestInDirection) {
                    TextLayoutDirection.Left ->
                        candidateOffsets.minByOrNull { layout.getHorizontalPosition(it, true) }
                    TextLayoutDirection.Right ->
                        candidateOffsets.maxByOrNull { layout.getHorizontalPosition(it, true) }
                    else -> null
                }
            }
        }
    }

    // If not specified, iOS would use the default system tint color
    private var selectionTintColor: Color? = null
    private fun setupTintColor() {
        textInputView.setTintColor(selectionTintColor?.toUIColor())
    }

    override fun setAvailableEditMenuActions(
        copy: (() -> Unit)?,
        paste: (() -> Unit)?,
        cut: (() -> Unit)?,
        selectAll: (() -> Unit)?,
        customActions: List<NativeTextInputContextMenuCustomAction>?
    ) {
        textInputView.updateMenuActions(
            copy,
            paste,
            cut,
            selectAll,
            customActions?.map { action ->
                CMPEditMenuCustomAction(action.title, action.action)
            } ?: emptyList()
        )
    }

    fun updateNativeTextInputTintColor(color: Color?) {
        selectionTintColor = color
        setupTintColor()
    }
}