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

private external interface ElementCSSInlineStyle {
    val style: CSSStyleDeclaration
}

private abstract external class CSSStyleDeclaration  {
    fun getPropertyValue(property: String): Float
    fun setProperty(property: String, value: Float)
}

internal interface ComposeCommandCommunicator {
    fun sendEditCommand(commands: List<EditCommand>)
    fun sendEditCommand(command: EditCommand) = sendEditCommand(listOf(command))

    fun sendKeyboardEvent(keyboardEvent: KeyEvent): Boolean
}

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
        fun getDocumentRoot() = document.documentElement as ElementCSSInlineStyle

        private const val leftPropertyName = "--compose-internal-web-backing-input-left"
        private const val topPropertyName = "--compose-internal-web-backing-input-top"

        var cssInternalWebBackingInputLeft: Float
            get() = getDocumentRoot().style.getPropertyValue(leftPropertyName)
            set(value) = getDocumentRoot().style.setProperty(leftPropertyName, value)

        var cssInternalWebBackingInputTop: Float
            get() = getDocumentRoot().style.getPropertyValue(topPropertyName)
            set(value) = getDocumentRoot().style.setProperty(topPropertyName, value)

        init {
            getDocumentRoot().style.apply {
                cssInternalWebBackingInputLeft = 0f
                cssInternalWebBackingInputTop = 0f
            }
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
          cssInternalWebBackingInputLeft = offset.x
          cssInternalWebBackingInputTop = offset.y
    }

    fun updateHtmlInputGeometry(width: Int, height: Int) {
        backingElement.style.width = "${width}px"
        backingElement.style.height = "${height}px"
    }

    fun updateState(textFieldValue: TextFieldValue) {
        inputStrategy.updateState(textFieldValue)
        focus()
    }

    fun dispose() {
        backingElement.remove()
    }
}