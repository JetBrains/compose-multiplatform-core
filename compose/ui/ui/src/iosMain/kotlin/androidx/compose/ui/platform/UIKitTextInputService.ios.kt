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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ComposeTextInputConnection
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.NativeTextInputConnection
import androidx.compose.ui.text.input.SelectionContainerConnection
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputConnection
import androidx.compose.ui.text.input.stateSnapshot
import androidx.compose.ui.text.input.usingNativeTextInput
import androidx.compose.ui.window.FocusedViewsList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIView

@OptIn(ExperimentalComposeUiApi::class)
internal class UIKitTextInputService(
    private var updateView: () -> Unit,
    private val view: UIView,
    private val viewConfiguration: ViewConfiguration,
    private val focusedViewsList: FocusedViewsList?,
    private var onInputStarted: () -> Unit,
    private var focusManager: () -> ComposeSceneFocusManager?,
    coroutineContext: CoroutineContext
) {

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var currentInputConnection: TextInputConnection? by mutableStateOf(null)

    private var updateEditMenuState = {}

    val hasInvalidations: Boolean
        get() = currentInputConnection?.hasInvalidations ?: false

    suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
        coroutineScope {
            launch {
                snapshotFlow { request.stateSnapshot() }.collect {
                    currentInputConnection?.onTextFieldValueUpdated(it)
                }
            }
            launch {
                snapshotFlow {
                    Triple(
                        request.textFieldRectInRoot(),
                        request.textClippingRectInRoot(),
                        request.unclippedTextOffsetInRoot(),
                    )
                }.collect {
                    currentInputConnection?.onViewGeometryUpdated()
                }
            }
            suspendCancellableCoroutine<Nothing> { continuation ->
                startInput(request)

                continuation.invokeOnCancellation {
                    stopInput()
                }
            }
        }
    }

    private fun startInput(request: PlatformTextInputMethodRequest) {
        val usingNativeTextInput = request.imeOptions.platformImeOptions?.usingNativeTextInput ?: false

        currentInputConnection?.stop()
        currentInputConnection = if (usingNativeTextInput) {
            NativeTextInputConnection(
                updateView = updateView,
                view = view,
                coroutineScope = coroutineScope,
                focusedViewsList = focusedViewsList,
                focusManager = focusManager,
            )
        } else {
            ComposeTextInputConnection(
                updateView = updateView,
                view = view,
                coroutineScope = coroutineScope,
                viewConfiguration = viewConfiguration,
                focusedViewsList = focusedViewsList,
                focusManager = focusManager
            )
        }
        currentInputConnection?.start(request)
        updateEditMenuState()
        onInputStarted()
    }

    private fun stopInput() {
        currentInputConnection?.stop()
        currentInputConnection = null
    }

    fun showSoftwareKeyboard() {
        currentInputConnection?.showKeyboard()
    }

    fun hideSoftwareKeyboard() {
        currentInputConnection?.dismissKeyboard()
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean =
        currentInputConnection?.onPreviewKeyEvent(event) ?: false

    val textToolbar: TextToolbar by lazy(LazyThreadSafetyMode.NONE) {
        object : TextToolbar {
            // Reflects whether the hosting CMPEditMenuView is alive, not the menu's literal visibility,
            // so hide() always runs and the view can't linger in an orphaned state. Android's TextToolbar
            // status is intent-based in a similar way (see AndroidTextToolbar.android.kt)
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
                private set

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                status = TextToolbarStatus.Shown
                if (currentInputConnection == null) {
                    // Entry point for showing the context menu in SelectionContainer scenarios, where
                    // there is no active text input session. iOS requires a UIView that can become first
                    // responder in order to host the context menu, so we create a dedicated connection
                    // without the text input session backed by a hidden view for this purpose.
                    currentInputConnection = SelectionContainerConnection(
                        view = view,
                        coroutineScope = coroutineScope,
                        viewConfiguration = viewConfiguration,
                        focusManager = focusManager
                    )
                    currentInputConnection?.start(emptyTextInputRequest())
                }
                (currentInputConnection as? ComposeTextInputConnection)?.showToolbarMenu(
                    rect = rect,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
                (currentInputConnection as? ComposeTextInputConnection)?.hideToolbar()

                if (currentInputConnection is SelectionContainerConnection) {
                    // stop() removes the view from the hierarchy and resigns first responder.
                    currentInputConnection?.stop()
                    currentInputConnection = null
                }
            }
        }
    }

    val nativeTextInputContext = object : UIKitNativeTextInputContext {
        override fun usingNativeTextInput(): Boolean =
            currentInputConnection is NativeTextInputConnection

        override fun updateNativeTextInputEditMenuState(
            copy: (() -> Unit)?,
            paste: (() -> Unit)?,
            cut: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<UIKitNativeTextInputContextMenuCustomAction>?
        ) {
            fun update() {
                currentInputConnection?.setAvailableEditMenuActions(
                    copy = copy,
                    paste = paste,
                    cut = cut,
                    selectAll = selectAll,
                    customActions = customActions
                )
                updateEditMenuState = {}
            }

            if (currentInputConnection == null) {
                // Fixes race conditions when the `updateNativeTextInputEditMenuState` called before
                // the input session start.
                updateEditMenuState = ::update
            } else {
                update()
            }
        }

        override fun updateNativeTextInputTintColor(color: Color?) {
            (currentInputConnection as? NativeTextInputConnection)?.updateNativeTextInputTintColor(
                color
            )
        }
    }

    fun dispose() {
        stopInput()
        onInputStarted = {}
        updateView = {}
        focusManager = { null }
    }
}

/**
 * A stub [PlatformTextInputMethodRequest] with no real editing state. Used to start a
 * [SelectionContainerConnection] purely to attach its hosting view for the context menu —
 * there is no text editing session behind it.
 */
private fun emptyTextInputRequest(): PlatformTextInputMethodRequest =
    object : PlatformTextInputMethodRequest {
        override val value: () -> TextFieldValue get() = { TextFieldValue() }
        override val state: TextEditorState = object : TextEditorState {
            override val selection: TextRange get() = TextRange(0, 0)
            override val composition: TextRange? get() = null
            override val length: Int get() = 0
            override fun get(index: Int): Char = ' '
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = ""
            override val text: String get() = ""
        }
        override val imeOptions: ImeOptions get() = ImeOptions.Default
        override val onEditCommand: (List<EditCommand>) -> Unit get() = { _ -> }
        override val onImeAction: ((ImeAction) -> Unit)? get() = null
        override val textLayoutResult: () -> TextLayoutResult? get() = { null }
        override val focusedRectInRoot: () -> Rect? get() = { null }
        override val textFieldRectInRoot: () -> Rect? get() = { null }
        override val textClippingRectInRoot: () -> Rect? get() = { null }
        override val unclippedTextOffsetInRoot: () -> Offset? get() = { null }
        override val editText: (block: TextEditingScope.() -> Unit) -> Unit get() = { _ -> }
    }
