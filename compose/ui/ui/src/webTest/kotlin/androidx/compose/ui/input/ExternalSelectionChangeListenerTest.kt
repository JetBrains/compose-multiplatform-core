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

package androidx.compose.ui.input

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.setSelectionRange
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.document
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.Event

class ExternalSelectionChangeListenerTest : OnCanvasTests {

    @Test
    fun selectionChangeInBackingInputUpdatesComposeSelection() = runApplicationTest {
        val text = "hello world"
        val textFieldValue = mutableStateOf(
            TextFieldValue(text, selection = TextRange(text.length))
        )
        val focusRequester = FocusRequester()

        createComposeWindow {
            BasicTextField(
                value = textFieldValue.value,
                onValueChange = { value ->
                    textFieldValue.value = value
                },
                modifier = Modifier.focusRequester(focusRequester)
            )
        }

        focusRequester.requestFocus()
        val htmlInput = waitForHtmlInput()
        htmlInput.focus()
        awaitAnimationFrame()
        awaitIdle()

        assertEquals(TextRange(text.length), textFieldValue.value.selection)

        setSelectionRange(htmlInput, 1, 7)
        document.dispatchEvent(Event("selectionchange"))
        awaitAnimationFrame()
        awaitIdle()

        assertEquals(TextRange(1, 7), textFieldValue.value.selection)

        setSelectionRange(htmlInput, 8, 8)
        document.dispatchEvent(Event("selectionchange"))
        awaitAnimationFrame()
        awaitIdle()

        assertEquals(TextRange(8, 8), textFieldValue.value.selection)
    }

    private suspend fun waitForHtmlInput(): HTMLDivElement {
        while (true) {
            val element = getShadowRoot().querySelector("div.compose-backing-field")
            if (element is HTMLDivElement) {
                return element
            }
            yield()
        }
    }
}
