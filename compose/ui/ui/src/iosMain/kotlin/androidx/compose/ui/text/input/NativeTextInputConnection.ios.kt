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
import androidx.compose.ui.platform.EmptyInputTraits
import androidx.compose.ui.platform.UIKitNativeTextInputContext
import androidx.compose.ui.platform.UIKitNativeTextInputContextMenuCustomAction
import androidx.compose.ui.platform.getUITextInputTraits
import androidx.compose.ui.platform.toUIColor
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.utils.CMPEditMenuCustomAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.FocusedViewsList
import androidx.compose.ui.window.IntermediateTextScrollView
import androidx.compose.ui.window.NativeTextInputView
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

internal class NativeTextInputConnection(
    updateView: () -> Unit,
    view: UIView,
    coroutineScope: CoroutineScope,
    focusedViewsList: FocusedViewsList?,
    onKeyboardPresses: (Set<*>) -> Unit,
    focusManager: () -> ComposeSceneFocusManager?
) : BaseTextInputConnection(
    updateView,
    view,
    coroutineScope,
    focusedViewsList,
    onKeyboardPresses,
    focusManager
), UIKitNativeTextInputContext {
    private val scrollView by lazy { IntermediateTextScrollView() }

    override val textUIView = NativeTextInputView(
        coroutineScope = coroutineScope
    ).also {
        view.addSubview(scrollView)
        scrollView.textView = it

        it.onKeyboardPresses = onKeyboardPresses
        it.clipsToBounds = false

        // Resizing should be done later
        it.resignFirstResponder()
        it.becomeFirstResponder()

        setupTintColor() // TODO: onAttach?
    }


    override fun open(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        super.open(value, imeOptions, onEditCommand, onImeActionPerformed)

        textUIView.input = this
        textUIView.inputTraits = getUITextInputTraits(imeOptions)

        showKeyboard()
    }

    override fun detachView() {
        // Out-of-bounds non-empty frame is required to hide text keyboard focus frame
        val outOfBoundsFrame = CGRectMake(-100000.0, 0.0, 1.0, 1.0)

        textUIView.input = null
        textUIView.inputTraits = EmptyInputTraits

        textUIView.let { textView ->
            textView.setFrame(outOfBoundsFrame)
            textUIView.onKeyboardPresses = NoOpOnKeyboardPresses
            coroutineScope.launch {
                delay(CLEAR_FOCUS_DELAY)
                if (scrollView.textView == textView) {
                    scrollView.textView = null
                    textView.removeFromSuperview()
                }
            }
        }
        scrollView.removeFromSuperview()
    }

    override fun stateWillChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textUIView.textWillChange()
        }
        if (selectionChanged) {
            textUIView.selectionWillChange()
        }
    }

    override fun stateDidChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textUIView.textDidChange()
        }
        if (selectionChanged) {
            textUIView.selectionDidChange()
        }
    }

    override fun updateTextViewPosition() {
        val rect = textFieldFrameInRoot ?: return
        // Since Compose content is rendered on a MetalView and the UITextInput-implementing
        // view is overlayed on top of it, we need to synchronize the Compose text
        // field with the IntermediateTextScrollView (which contains IntermediateUITextView)
        // to ensure native iOS text input controls
        // align correctly with the rendered text.
        val layoutResult = textLayoutResult ?: return
        val unclippedTextPosition = unclippedTextPosition ?: return

        val contentBounds = calculateContentBounds(
            layoutResult,
            rect,
            unclippedTextPosition
        )
        currentContentBounds = contentBounds
        val contentInsets = calculateContentInsets(rect, contentBounds)
        currentContentInsets = contentInsets
        scrollView.setFrame(
            rect.toDpRect(view.density),
            contentBounds.toDpRect(view.density),
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

    private fun calculateContentInsets(textFieldFrame: Rect, contentBounds: Rect): DpInsets = with(view.density) {
        return DpInsets(
            left = max(0f, -contentBounds.left).toDp(),
            top = max(0f, -contentBounds.top).toDp(),
            right = max(0f, textFieldFrame.width - contentBounds.width + contentBounds.left).toDp(),
            bottom = max(0f, textFieldFrame.height - contentBounds.height + contentBounds.top).toDp()
        )
    }

    override fun sendEditCommand(vararg commands: EditCommand) {
        super.sendEditCommand(*commands)
        // For Native Text Input it's essential to trigger view update right after send edit command,
        // otherwise UIKit calls may use an invalid layout state
        coroutineScope.launch {
            updateView()
        }
    }

    override fun beginFloatingCursor(offset: DpOffset) {
        val cursorPos = getState()?.selection?.start ?: return
        val cursorRect = textLayoutResult?.getCursorRect(cursorPos) ?: return
        floatingCursorTranslation = cursorRect.center - offset.toOffset(view.density)
    }

    override fun caretDpRectForPosition(position: Int): DpRect? {
        val text = getState()?.text ?: return null
        if (position < 0 || position > text.length) {
            return null
        }
        val currentTextLayoutResult = textLayoutResult ?: return null
        if (position > currentTextLayoutResult.multiParagraph.intrinsics.annotatedString.length) {
            return null
        }
        val rect = currentTextLayoutResult.getCursorRect(position)
        return rect.toDpRect(view.density).let {
            val hafWidth = cursorThickness / 2
            val center = (it.left + it.right) / 2
            it.copy(left = center - hafWidth, right = center + hafWidth)
        }
    }



    // If not specified, iOS would use the default system tint color
    private var selectionTintColor: Color? = null
    private fun setupTintColor() {
        textUIView.let {
            val uiColor = selectionTintColor?.toUIColor()
            it.setTintColor(uiColor)
        }
    }

    override fun usingNativeTextInput(): Boolean = true

    override fun updateNativeTextInputEditMenuState(
        copy: (() -> Unit)?,
        paste: (() -> Unit)?,
        cut: (() -> Unit)?,
        selectAll: (() -> Unit)?,
        customActions: List<UIKitNativeTextInputContextMenuCustomAction>?
    ) {
        textUIView.updateMenuActions(
            copy,
            paste,
            cut,
            selectAll,
            customActions?.map { action ->
                CMPEditMenuCustomAction(action.title, action.action)
            } ?: emptyList()
        )
    }

    override fun updateNativeTextInputTintColor(color: Color?) {
        selectionTintColor = color
        setupTintColor()
    }

    /**
     * Matches DefaultCursorThickness
     *
     * Must be at least 1.dp to make caret interactable
     */
    private val cursorThickness = 2.dp
}

// Insets in DP
internal data class DpInsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)