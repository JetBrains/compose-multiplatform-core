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

package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.browser.document
import kotlinx.browser.window

internal interface ComposeCommandCommunicator {
    fun sendEditCommand(commands: List<EditCommand>)
    fun sendEditCommand(command: EditCommand) = sendEditCommand(listOf(command))

    fun sendKeyboardEvent(keyboardEvent: KeyEvent): Boolean
}

private fun setBackingInputLeft(value: Float): Unit = js("document.documentElement.style.setProperty(\"--compose-internal-web-backing-input-left\", value)")
private fun setBackingInputTop(value: Float): Unit = js("document.documentElement.style.setProperty(\"--compose-internal-web-backing-input-top\", value)")
private fun setBackingInputWidth(value: Float): Unit = js("document.documentElement.style.setProperty(\"--compose-internal-web-backing-input-width\", value)")
private fun setBackingInputHeight(value: Float): Unit = js("document.documentElement.style.setProperty(\"--compose-internal-web-backing-input-height\", value)")

/**
 * The purpose of this entity is to isolate synchronization between a TextFieldValue
 * and the DOM HTMLTextAreaElement we are actually listening events on in order to show
 * the virtual keyboard.
 */
internal class BackingDomInput(
    imeOptions: ImeOptions,
    composeCommunicator : ComposeCommandCommunicator,
) {
    private val inputStrategy = DomInputStrategy(
        imeOptions,
        composeCommunicator
    )

    private companion object {
        init {
            setBackingInputLeft(0f)
            setBackingInputTop(0f)
            setBackingInputWidth(0f)
            setBackingInputHeight(0f)
        }
    }

    private val backingElement = inputStrategy.htmlInput

    fun register() {
        document.body?.appendChild(backingElement)
    }

    fun focus() {
        // we focus twice to be sure that ios and non-ios browser both manage to focus
        // see https://youtrack.jetbrains.com/issue/CMP-8013
        // and https://youtrack.jetbrains.com/issue/CMP-7836/
        backingElement.focus()
        window.requestAnimationFrame {
            if (document.activeElement != backingElement) {
                backingElement.focus()
            }
        }
    }

    fun blur() {
        backingElement.blur()
    }

    fun updateHtmlInputPosition(offset: Offset) {
        setBackingInputLeft(offset.x)
        setBackingInputTop(offset.y)
    }

    fun updateHtmlInputGeometry(width: Float, height: Float) {
        setBackingInputWidth(width)
        setBackingInputHeight(height)
    }

    fun updateState(textFieldValue: TextFieldValue) {
        inputStrategy.updateState(textFieldValue)
        focus()
    }

    fun dispose() {
        backingElement.remove()
    }
}