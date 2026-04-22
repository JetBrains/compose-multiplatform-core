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
import androidx.compose.ui.platform.EmptyInputTraits
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.getUITextInputTraits
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.utils.CMPEditMenuCustomAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.asCGRect
import androidx.compose.ui.unit.asDpOffset
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.window.FocusedViewsList
import androidx.compose.ui.window.ComposeTextInputView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

internal open class ComposeTextInputConnection(
    updateView: () -> Unit,
    view: UIView,
    coroutineScope: CoroutineScope,
    viewConfiguration: ViewConfiguration,
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
), TextToolbar {

    override val textUIView =
        ComposeTextInputView(
            doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
            coroutineScope = coroutineScope
        ).also {
            it.setAutoresizingMask(
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            )
            it.onKeyboardPresses = onKeyboardPresses
            view.addSubview(it)
            it.setFrame(view.bounds)
        }

    override fun attachInputToView(imeOptions: ImeOptions) {
        textUIView.input = this
        textUIView.inputTraits = getUITextInputTraits(imeOptions)
    }

    override fun detachView() {
        // Out-of-bounds non-empty frame is required to hide text keyboard focus frame
        val outOfBoundsFrame = CGRectMake(-100000.0, 0.0, 1.0, 1.0)

        textUIView.input = null
        textUIView.inputTraits = EmptyInputTraits

        showMenuOrUpdatePosition = {}
        textUIView.let { view ->
            view.setFrame(outOfBoundsFrame)
            view.onKeyboardPresses = NoOpOnKeyboardPresses
            coroutineScope.launch {
                delay(CLEAR_FOCUS_DELAY)
                view.removeFromSuperview()
            }
        }
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
        if (textChanged || selectionChanged) {
            updateView()
        }
    }

    override fun updateViewGeometry(
        textFieldFrame: Rect,
        unclippedTextPosition: Offset
    ) {
        super.updateViewGeometry(textFieldFrame, unclippedTextPosition)
        showMenuOrUpdatePosition()
    }

    override fun updateTextViewPosition(unclippedTextPosition: Offset) {
        val rect = textFieldFrameInRoot ?: return
        textUIView.setFrame(rect.toDpRect(view.density).asCGRect())
    }

    override fun beginFloatingCursor(offset: DpOffset) {
        val cursorPos = getCursorPos() ?: getState()?.selection?.start ?: return
        val cursorRect = textLayoutResult?.getCursorRect(cursorPos) ?: return
        floatingCursorTranslation = cursorRect.center - offset.toOffset(view.density)
    }

    private fun textMenuAppearanceChanged() {
        textInputServiceInvalidationsCount++
        coroutineScope.launch {
            // Time to show, hide or update state of context menu
            delay(500.milliseconds)
            textInputServiceInvalidationsCount--
        }
    }
    // Fixes a problem where the menu is shown before the textUIView gets its final layout.
    private var showMenuOrUpdatePosition = {}

    override val status: TextToolbarStatus
        get() = if (textUIView.isTextMenuShown())
            TextToolbarStatus.Shown
        else
            TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        showMenuOrUpdatePosition = {
            textUIView.let { textUIView ->
                val density = view.density
                val offset = textUIView.frame.useContents { origin.asDpOffset().toOffset(density) }
                val target = rect.translate(-offset).toDpRect(density).asCGRect()
                textUIView.showEditMenuAtRect(
                    targetRect = target,
                    copy = onCopyRequested,
                    cut = onCutRequested,
                    paste = onPasteRequested,
                    selectAll = onSelectAllRequested,
                    customActions = emptyList<CMPEditMenuCustomAction>()
                )
                textMenuAppearanceChanged()
            }
        }
        showMenuOrUpdatePosition()
    }


    override fun hide() {
        showMenuOrUpdatePosition = {}
        textUIView.let {
            it.hideTextMenu()
            textMenuAppearanceChanged()
        }
    }
}

internal class SelectionContainerConnection(
    view: UIView,
    coroutineScope: CoroutineScope,
    viewConfiguration: ViewConfiguration,
    focusManager: () -> ComposeSceneFocusManager?
) : ComposeTextInputConnection(
    {},
    view,
    coroutineScope,
    viewConfiguration,
    null,
    {},
    focusManager
) {
    override fun close() {
        textUIView.resignFirstResponder()
        super.close()
    }
}
