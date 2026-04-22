/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ComposeTextInputConnection
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.NativeTextInputConnection
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SelectionContainerConnection
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputConnection
import androidx.compose.ui.text.input.usingNativeTextInput
import androidx.compose.ui.window.FocusedViewsList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIPress
import platform.UIKit.UIView

@Suppress("DEPRECATION") // TODO https://youtrack.jetbrains.com/issue/CMP-9858
internal class UIKitTextInputService(
    private val updateView: (usingNativeTextInput: Boolean) -> Unit,
    private val view: UIView,
    private val viewConfiguration: ViewConfiguration,
    private val focusedViewsList: FocusedViewsList?,
    private var onInputStarted: () -> Unit,
    /**
     * Callback to handle keyboard presses. The parameter is a [Set] of [UIPress] objects.
     * Erasure happens due to K/N not supporting Obj-C lightweight generics.
     */
    private var onKeyboardPresses: (Set<*>) -> Unit,
    private var focusManager: () -> ComposeSceneFocusManager?,
    coroutineContext: CoroutineContext
) : PlatformTextInputService {

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var currentInputConnection: TextInputConnection? by mutableStateOf(null)

    val hasInvalidations: Boolean
        get() = currentInputConnection?.hasInvalidations ?: false

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        val usingNativeTextInput = imeOptions.platformImeOptions?.usingNativeTextInput ?: false

        currentInputConnection?.close()
        currentInputConnection = if (usingNativeTextInput) NativeTextInputConnection(
            updateView = { updateView(true) },
            view = view,
            coroutineScope = coroutineScope,
            focusedViewsList = focusedViewsList,
            onKeyboardPresses = onKeyboardPresses,
            focusManager = focusManager
        ) else ComposeTextInputConnection(
            updateView = { updateView(false) },
            view = view,
            coroutineScope = coroutineScope,
            viewConfiguration = viewConfiguration,
            focusedViewsList = focusedViewsList,
            onKeyboardPresses = onKeyboardPresses,
            focusManager = focusManager
        )
        currentInputConnection?.open(value, imeOptions, onEditCommand, onImeActionPerformed)

        onInputStarted()
    }

    override fun stopInput() {
        currentInputConnection?.close()
        currentInputConnection = null
    }

    override fun showSoftwareKeyboard() {
        currentInputConnection?.showKeyboard()
    }

    override fun hideSoftwareKeyboard() {
        currentInputConnection?.dismissKeyboard()
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        currentInputConnection?.updateState(newValue)
    }

    fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
        currentInputConnection?.updateTextLayoutResult(textLayoutResult)
    }

    fun updateFocusedRect(rect: Rect) {
        currentInputConnection?.updateFocusedRect(rect)
    }

    fun updateTextFieldGeometry(
        textFieldFrame: Rect,
        clippingTextFrame: Rect,
        unclippedTextPosition: Offset
    ) {
        currentInputConnection?.updateViewGeometry(
            textFieldFrame,
            clippingTextFrame,
            unclippedTextPosition
        )
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean =
        currentInputConnection?.onPreviewKeyEvent(event) ?: false

    fun flushEditCommandsIfNeeded(force: Boolean = false) {
        currentInputConnection?.flushEditCommandsIfNeeded(force)
    }

    val toolbarHandler: TextToolbar = object : TextToolbar {

        override val status: TextToolbarStatus
            get() = (currentInputConnection as? TextToolbar)?.status ?: TextToolbarStatus.Hidden

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
            if (currentInputConnection == null) {
                currentInputConnection = SelectionContainerConnection(
                    view, coroutineScope, viewConfiguration, focusManager
                )
            }
            (currentInputConnection as? TextToolbar)?.showMenu(
                rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested
            )
        }

        override fun hide() {
            (currentInputConnection as? TextToolbar)?.hide()

            if (currentInputConnection is SelectionContainerConnection) {
                currentInputConnection?.close()
                currentInputConnection = null
            }

        }
    }

    val nativeTextInputContext = object : UIKitNativeTextInputContext {
        override fun usingNativeTextInput(): Boolean =
            (currentInputConnection as? UIKitNativeTextInputContext)?.usingNativeTextInput()
                ?: false

        override fun updateNativeTextInputEditMenuState(
            copy: (() -> Unit)?,
            paste: (() -> Unit)?,
            cut: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<UIKitNativeTextInputContextMenuCustomAction>?
        ) {
            (currentInputConnection as? UIKitNativeTextInputContext)?.updateNativeTextInputEditMenuState(
                copy, paste, cut, selectAll, customActions
            )
        }

        override fun updateNativeTextInputTintColor(color: Color?) {
            (currentInputConnection as? UIKitNativeTextInputContext)?.updateNativeTextInputTintColor(
                color
            )
        }

    }

    fun dispose() {
        stopInput()
        onInputStarted = { }
        onKeyboardPresses = { }
        focusManager = { null }
    }
}

