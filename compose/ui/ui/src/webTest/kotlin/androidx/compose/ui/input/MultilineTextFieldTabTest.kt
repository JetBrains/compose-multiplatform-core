/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

/**
 * https://youtrack.jetbrains.com/issue/CMP-5822 - a multiline `BasicTextField`
 * swallows Tab keypresses and inserts a `\t` character instead of moving the focus to the
 * next focusable component.
 */
class MultilineTextFieldTabTest : OnCanvasTests {

    private suspend fun waitForMultilineHtmlInput(): HTMLElement {
        while (true) {
            val element = getShadowRoot().querySelector("div.compose-backing-field")
            if (element is HTMLElement) {
                return element
            }
            yield()
        }
    }

    private fun tabKeyDown(shiftKey: Boolean = false): KeyboardEvent = keyEvent(
        key = "Tab",
        type = "keydown",
        keyCode = Key.Tab.keyCode.toInt(),
        code = "Tab",
        shiftKey = shiftKey
    )

    @Ignore // CMP-5822: multiline BasicTextField inserts '\t' instead of moving the focus
    @Test
    fun multilineBasicTextField1MovesFocusOnTab() = runApplicationTest {
        val focusRequester = FocusRequester()
        val value = mutableStateOf(TextFieldValue("Hello"))

        var multilineFocusState: FocusState? = null
        var nextFocusState: FocusState? = null

        createComposeWindow {
            Column {
                BasicTextField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    singleLine = false,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { multilineFocusState = it }
                )

                BasicTextField(
                    value = TextFieldValue("World"),
                    onValueChange = {},
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { nextFocusState = it }
                )
            }
        }

        focusRequester.requestFocus()

        val htmlInput = waitForMultilineHtmlInput()
        assertNotNull(multilineFocusState)
        assertNotNull(nextFocusState)
        assertEquals(true, multilineFocusState.isFocused)
        assertEquals(false, nextFocusState.isFocused)

        htmlInput.dispatchEvent(tabKeyDown())
        awaitAnimationFrame()

        assertEquals(
            "Hello",
            value.value.text,
            "Tab must not be inserted into a multiline BasicTextField"
        )
        assertEquals(false, multilineFocusState.isFocused, "Tab should move the focus away")
        assertEquals(true, nextFocusState.isFocused, "Tab should focus the next component")
    }

    @Ignore // CMP-5822: multiline BasicTextField inserts '\t' instead of moving the focus
    @Test
    fun multilineBasicTextField2MovesFocusOnTab() = runApplicationTest {
        val focusRequester = FocusRequester()
        val state = TextFieldState("Hello")

        var multilineFocusState: FocusState? = null
        var nextFocusState: FocusState? = null

        createComposeWindow {
            Column {
                BasicTextField(
                    state = state,
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { multilineFocusState = it }
                )

                BasicTextField(
                    state = TextFieldState("World"),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.onFocusChanged { nextFocusState = it }
                )
            }
        }

        focusRequester.requestFocus()

        val htmlInput = waitForMultilineHtmlInput()
        assertNotNull(multilineFocusState)
        assertNotNull(nextFocusState)
        assertEquals(true, multilineFocusState.isFocused)
        assertEquals(false, nextFocusState.isFocused)

        htmlInput.dispatchEvent(tabKeyDown())
        awaitAnimationFrame()

        assertEquals(
            "Hello",
            state.text.toString(),
            "Tab must not be inserted into a multiline BasicTextField"
        )
        assertEquals(false, multilineFocusState.isFocused, "Tab should move the focus away")
        assertEquals(true, nextFocusState.isFocused, "Tab should focus the next component")
    }

    /**
     * Documents the current (incorrect) behaviour described in CMP-5822:
     * Tab is consumed by the multiline text field and a `\t` character is inserted.
     *
     * This test is expected to fail as soon as CMP-5822 is fixed - at that point it should be
     * removed and the assertions above should be un-ignored.
     */
    @Test
    fun multilineBasicTextFieldCurrentlySwallowsTab() = runApplicationTest {
        val focusRequester = FocusRequester()
        val value = mutableStateOf(TextFieldValue("Hello"))

        var multilineFocusState: FocusState? = null
        var nextFocusState: FocusState? = null

        createComposeWindow {
            Column {
                BasicTextField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    singleLine = false,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { multilineFocusState = it }
                )

                BasicTextField(
                    value = TextFieldValue("World"),
                    onValueChange = {},
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { nextFocusState = it }
                )
            }
        }

        focusRequester.requestFocus()

        val htmlInput = waitForMultilineHtmlInput()
        assertNotNull(multilineFocusState)
        assertNotNull(nextFocusState)

        htmlInput.dispatchEvent(tabKeyDown())
        awaitAnimationFrame()

        assertTrue(
            value.value.text.contains('\t'),
            "CMP-5822: a tab character is inserted instead of moving the focus"
        )
        assertEquals(true, multilineFocusState.isFocused, "CMP-5822: the focus is trapped")
        assertEquals(false, nextFocusState.isFocused, "CMP-5822: the focus is trapped")
    }
}
