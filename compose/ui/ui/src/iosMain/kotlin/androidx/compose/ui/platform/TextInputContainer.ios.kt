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

package androidx.compose.ui.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.unit.Density

/**
 * A per-scene factory of platform text inputs, each backed by a `UIView` that becomes the first
 * responder so that iOS can drive the keyboard, IME, the edit menu and native text selection over
 * text that lives in Compose state.
 */
@InternalComposeUiApi
interface TextInputContainer {
    /**
     * A handle to a single text input, owned by the composable that requested it.
     */
    interface Holder {
        fun setRect(rect: Rect)
        fun remove()

        fun showEditMenuAtRect(
            targetRect: Rect,
            copy: (() -> Unit)?,
            cut: (() -> Unit)?,
            paste: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<NativeTextInputContextMenuCustomAction>?
        )
        fun hideEditMenu()

        fun updateNativeTextInputEditMenuState(
            copy: (() -> Unit)?,
            cut: (() -> Unit)?,
            paste: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<NativeTextInputContextMenuCustomAction>?
        )

        fun updateNativeTextInputTintColor(color: Color?)

        fun usingNativeTextInput(): Boolean
    }

    /**
     * Provides a temporary connection between non-editable text field and iOS text input.
     * Used to support the auto-safe/autofill password feature.
     */
    interface Delegate {
        val text: String
        val isFocused: Boolean
        val selectionTextRange: TextRange
        val markedTextRange: TextRange?
        val imeOptions: ImeOptions
        val editorToken: Any?

        fun insertText(text: String)
        fun replaceRange(range: TextRange, text: String)
        fun deleteBackward()
        fun setSelectedText(range: TextRange?)
        fun setMarkedText(markedText: String?, selectedRange: TextRange)
        fun unmarkText()
    }

    /**
     * Attaches an editable text input for [delegate] to the scene.
     */
    fun createTextInput(delegate: Delegate): Holder

    /**
     * Attaches a non-editable text selection containfor [delegate] to the scene.
     */
    fun createSelectionContainer(delegate: Delegate): Holder

    /**
     * HACK: In some cases it's impossible to detect if the native text input is attached to
     * particular [Holder]. In order to fix that, return extra flag that indicates if the current
     * active text input is the native text input.
     */
    fun activeSessionUsesNativeTextInput(): Boolean
}

/**
 * A [TextInputContainer] that provides no platform text input, used when the scene is not hosted by
 * UIKit views, as in tests. Text fields then draw and edit the text themselves.
 */
internal object EmptyTextInputContainer : TextInputContainer {

    private object EmptyHolder : TextInputContainer.Holder {
        override fun setRect(rect: Rect) = Unit

        override fun remove() = Unit

        override fun showEditMenuAtRect(
            targetRect: Rect,
            copy: (() -> Unit)?,
            cut: (() -> Unit)?,
            paste: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<NativeTextInputContextMenuCustomAction>?
        ) = Unit

        override fun hideEditMenu() = Unit

        override fun updateNativeTextInputEditMenuState(
            copy: (() -> Unit)?,
            cut: (() -> Unit)?,
            paste: (() -> Unit)?,
            selectAll: (() -> Unit)?,
            customActions: List<NativeTextInputContextMenuCustomAction>?
        ) = Unit

        override fun updateNativeTextInputTintColor(color: Color?) = Unit

        override fun usingNativeTextInput(): Boolean = false
    }

    override fun createTextInput(delegate: TextInputContainer.Delegate): TextInputContainer.Holder =
        EmptyHolder

    override fun createSelectionContainer(delegate: TextInputContainer.Delegate): TextInputContainer.Holder =
        EmptyHolder

    override fun activeSessionUsesNativeTextInput(): Boolean = false
}
