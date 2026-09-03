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
import androidx.compose.ui.text.input.TextInputConnection.Companion.CLEAR_FOCUS_DELAY
import androidx.compose.ui.text.input.stateSnapshot
import androidx.compose.ui.text.input.usingNativeTextInput
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.toCGRect
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.window.FocusedViewsList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

@OptIn(ExperimentalComposeUiApi::class)
internal class TextInputService(
    private var updateView: () -> Unit,
    private val view: UIView,
    private val viewConfiguration: ViewConfiguration,
    private val focusedViewsList: FocusedViewsList?,
    private var listener: Listener,
    private var focusManager: () -> ComposeSceneFocusManager?,
    coroutineContext: CoroutineContext
) {

    interface Listener {
        fun onInputWillStart() = Unit
        fun onInputDidStart() = Unit
        fun onInputDidStop() = Unit
    }

    private object EmptyListener : Listener

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var currentInputConnection: TextInputConnection? by mutableStateOf(null)

    val hasInvalidations: Boolean
        get() = currentInputConnection?.hasInvalidations ?: false

    private val holders = mutableSetOf<TextInputHolder>()

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
        currentInputConnection?.stop()
        listener.onInputWillStart()

        currentInputConnection = holderFor(request)?.connection
        currentInputConnection?.start(request)
        listener.onInputDidStart()
    }

    private fun holderFor(request: PlatformTextInputMethodRequest): TextInputHolder? {
        val editorToken = request.editorToken
        return holders.firstOrNull { it.delegate.editorToken === editorToken }
    }

    private fun stopInput() {
        currentInputConnection?.stop()
        currentInputConnection = null
        listener.onInputDidStop()
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
            override val status: TextToolbarStatus
                get() = (currentInputConnection as? ComposeTextInputConnection)?.toolbarStatus ?: TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val connection = activeMenuConnection() ?: return
                connection.showToolbarMenu(
                    rect = rect,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested,
                    customActions = null,
                )
            }

            override fun hide() {
                (currentInputConnection as? ComposeTextInputConnection)?.hideToolbar()

                if (currentInputConnection is SelectionContainerConnection) {
                    currentInputConnection?.rootView?.removeFromSuperview()
                    currentInputConnection?.dispose()
                    currentInputConnection = null
                }
            }

            private fun activeMenuConnection(): ComposeTextInputConnection? {
                currentInputConnection?.let { return it as? ComposeTextInputConnection }
                holders.firstOrNull { it.delegate.isFocused }
                    ?.let { it.connection as? ComposeTextInputConnection }
                    ?.let {
                        currentInputConnection?.stop()
                        currentInputConnection = it
                        return it
                    }
                return startSelectionContainerConnection()
            }

            private fun startSelectionContainerConnection(): ComposeTextInputConnection {
                val connection = SelectionContainerConnection(
                    coroutineScope = coroutineScope,
                    viewConfiguration = viewConfiguration,
                    focusManager = focusManager
                ).also {
                    it.rootView.setFrame(view.bounds)
                    view.addSubview(it.rootView)
                }
                currentInputConnection = connection
                connection.start(
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
                )
                return connection
            }
        }
    }

    val textInputContainer by lazy(LazyThreadSafetyMode.NONE) {
        object : TextInputContainer {
            override fun createTextInput(
                delegate: TextInputContainer.Delegate
            ): TextInputContainer.Holder {
                val connection = if (delegate.imeOptions.platformImeOptions?.usingNativeTextInput == true) {
                    NativeTextInputConnection(
                        inactiveTextInputDelegate = InactiveTextInputAdapter(delegate),
                        updateView = updateView,
                        coroutineScope = coroutineScope,
                        focusedViewsList = focusedViewsList,
                        focusManager = focusManager,
                    )
                } else {
                    ComposeTextInputConnection(
                        inactiveTextEditingDelegate = InactiveTextInputAdapter(delegate),
                        updateView = updateView,
                        coroutineScope = coroutineScope,
                        viewConfiguration = viewConfiguration,
                        focusedViewsList = focusedViewsList,
                        focusManager = focusManager
                    )
                }

                this@TextInputService.view.addSubview(connection.rootView)

                val holder = TextInputHolder(
                    delegate = delegate,
                    connection = connection,
                    onRemove = { holders.remove(it) },
                )

                holders.add(holder)

                return holder
            }

            override fun createSelectionContainer(delegate: TextInputContainer.Delegate): TextInputContainer.Holder {
                val connection = SelectionContainerConnection(
                    coroutineScope = coroutineScope,
                    viewConfiguration = viewConfiguration,
                    focusManager = focusManager
                )
                view.addSubview(connection.rootView)
                val holder = TextInputHolder(
                    delegate = delegate,
                    connection = connection,
                    onRemove = { holders.remove(it) },
                )
                holders.add(holder)

                return holder
            }

            override fun activeSessionUsesNativeTextInput(): Boolean =
                currentInputConnection is NativeTextInputConnection
        }
    }

    fun dispose() {
        stopInput()

        holders.toList().forEach { it.remove() }

        listener = EmptyListener
        updateView = {}
        focusManager = { null }
    }
}

private class TextInputHolder(
    val delegate: TextInputContainer.Delegate,
    val connection: TextInputConnection,
    val onRemove: (TextInputHolder) -> Unit,
): TextInputContainer.Holder {
    override fun setRect(rect: Rect) {
        connection.rootView.setFrame(rect.toDpRect(connection.rootView.density).toCGRect())
    }

    override fun remove() {
        connection.stop()

        onRemove(this)

        connection.rootView.resignFirstResponder()
        connection.dispose()

        removeView()
    }

    private fun removeView() {
        // Out-of-bounds non-empty frame is required to hide text keyboard focus frame
        val outOfBoundsFrame = CGRectMake(-100000.0, 0.0, 1.0, 1.0)

        val rootView = connection.rootView
        rootView.setFrame(outOfBoundsFrame)
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, CLEAR_FOCUS_DELAY.inWholeMilliseconds * NSEC_PER_MSEC.toLong()),
            dispatch_get_main_queue()
        ) {
            rootView.removeFromSuperview()
        }
    }

    override fun showEditMenuAtRect(
        targetRect: Rect,
        copy: (() -> Unit)?,
        cut: (() -> Unit)?,
        paste: (() -> Unit)?,
        selectAll: (() -> Unit)?,
        customActions: List<NativeTextInputContextMenuCustomAction>?
    ) {
        val connection = connection as? ComposeTextInputConnection ?: return
        connection.showToolbarMenu(
            rect = targetRect,
            onCopyRequested = copy,
            onPasteRequested = paste,
            onCutRequested = cut,
            onSelectAllRequested = selectAll,
            customActions = customActions,
        )
    }

    override fun hideEditMenu() {
        val connection = connection as? ComposeTextInputConnection ?: return
        connection.hideToolbar()
    }

    override fun updateNativeTextInputEditMenuState(
        copy: (() -> Unit)?,
        cut: (() -> Unit)?,
        paste: (() -> Unit)?,
        selectAll: (() -> Unit)?,
        customActions: List<NativeTextInputContextMenuCustomAction>?
    ) {
        connection.setAvailableEditMenuActions(
            copy = copy,
            cut = cut,
            paste = paste,
            selectAll = selectAll,
            customActions = customActions,
        )
    }

    override fun updateNativeTextInputTintColor(color: Color?) {
        val connection = connection as? NativeTextInputConnection ?: return
        connection.updateNativeTextInputTintColor(color)
    }

    override fun usingNativeTextInput(): Boolean {
        return delegate.imeOptions.platformImeOptions?.usingNativeTextInput ?: false
    }
}