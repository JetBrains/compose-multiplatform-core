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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

class TextFieldFocusTest : OnCanvasTests {

    @Test
    fun canMoveFocusForwardAndBackUsingTab() = runApplicationTest {
        val focusRequester = FocusRequester()

        suspend fun waitForSingleLineHtmlInput(): HTMLInputElement {
            while (true) {
                val element = getShadowRoot().querySelector("input")
                if (element is HTMLInputElement) {
                    return element
                }
                yield()
            }
        }

        var firstTextFieldFocusState: FocusState? = null
        var secondTextFieldFocusState: FocusState? = null

        createComposeWindow {
            Column {
                TextField(
                    state = rememberTextFieldState(initialText = "Hello"),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged({
                            firstTextFieldFocusState = it
                        }),
                    lineLimits = TextFieldLineLimits.SingleLine
                )

                TextField(
                    state = rememberTextFieldState(initialText = "World"),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.onFocusChanged({
                        secondTextFieldFocusState = it
                    })
                )
            }
        }

        var lastKeydownEventOnRoot: Event? = null

        focusRequester.requestFocus()

        val htmlInput1 = waitForSingleLineHtmlInput()
        assertNotNull(firstTextFieldFocusState)
        assertNotNull(secondTextFieldFocusState)
        assertEquals(true, firstTextFieldFocusState.isFocused)
        assertEquals(false, secondTextFieldFocusState.isFocused)

        getShadowRoot().addEventListener("keydown", {
            lastKeydownEventOnRoot = it
        })

        val tabKeyDown = keyEvent(
            key = "Tab",
            type = "keydown",
            keyCode = Key.Tab.keyCode.toInt(),
            code = "Tab"
        )
        htmlInput1.dispatchEvent(tabKeyDown)
        awaitAnimationFrame()
        assertNotNull(lastKeydownEventOnRoot)
        assertEquals("Tab", (lastKeydownEventOnRoot as KeyboardEvent).key)
        assertFalse((lastKeydownEventOnRoot as KeyboardEvent).shiftKey)
        assertTrue(lastKeydownEventOnRoot!!.defaultPrevented)
        lastKeydownEventOnRoot = null

        assertEquals(false, firstTextFieldFocusState.isFocused)
        assertEquals(true, secondTextFieldFocusState.isFocused)

        /* Now move focus back using Tab+Shift */

        val htmlInput2 = waitForSingleLineHtmlInput()
        val tabKeyDownWithShift = keyEvent(
            key = "Tab",
            type = "keydown",
            keyCode = Key.Tab.keyCode.toInt(),
            code = "Tab",
            shiftKey = true
        )

        htmlInput2.dispatchEvent(tabKeyDownWithShift)
        awaitAnimationFrame()

        assertEquals(true, firstTextFieldFocusState.isFocused)
        assertEquals(false, secondTextFieldFocusState.isFocused)

        assertNotNull(lastKeydownEventOnRoot)
        assertEquals("Tab", (lastKeydownEventOnRoot as KeyboardEvent).key)
        assertTrue((lastKeydownEventOnRoot as KeyboardEvent).shiftKey)
        assertTrue(lastKeydownEventOnRoot!!.defaultPrevented)
    }

    /**
     * Regression test for https://youtrack.jetbrains.com/issue/CMP-9388
     * Tests that Tab navigation works correctly with read-only TextFields.
     *
     * Read-only TextFields don't create a backing HTML input element, so when focus
     * moves TO a read-only TextField, subsequent Tab presses must come from the canvas.
     *
     * Note: The actual bug (focus not working after leaving a TextField) is caused by
     * the browser not knowing where to send key events when no element has DOM focus.
     * This can't be fully simulated in tests since programmatic dispatchEvent() bypasses
     * browser focus routing. The fix ensures focusFallbackElement.focus() is called in
     * stopInput() to give the canvas DOM focus.
     */
    @Test
    fun canTabThroughReadOnlyTextField() = runApplicationTest {
        val focusRequester = FocusRequester()

        suspend fun waitForSingleLineHtmlInput(): HTMLInputElement {
            while (true) {
                val element = getShadowRoot().querySelector("input")
                if (element is HTMLInputElement) {
                    return element
                }
                yield()
            }
        }

        var field1FocusState: FocusState? = null
        var field2FocusState: FocusState? = null
        var field3FocusState: FocusState? = null  // read-only
        var field4FocusState: FocusState? = null

        createComposeWindow {
            Column {
                TextField(
                    state = rememberTextFieldState(initialText = "Field 1"),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { field1FocusState = it },
                    lineLimits = TextFieldLineLimits.SingleLine
                )

                TextField(
                    state = rememberTextFieldState(initialText = "Field 2"),
                    modifier = Modifier.onFocusChanged { field2FocusState = it },
                    lineLimits = TextFieldLineLimits.SingleLine
                )

                TextField(
                    state = rememberTextFieldState(initialText = "Field 3 (read-only)"),
                    modifier = Modifier.onFocusChanged { field3FocusState = it },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    readOnly = true
                )

                TextField(
                    state = rememberTextFieldState(initialText = "Field 4"),
                    modifier = Modifier.onFocusChanged { field4FocusState = it },
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
        }

        focusRequester.requestFocus()
        awaitAnimationFrame()

        // Verify initial focus on Field 1
        assertNotNull(field1FocusState)
        assertEquals(true, field1FocusState!!.isFocused)

        val tabKeyDown = keyEvent(
            key = "Tab",
            type = "keydown",
            keyCode = Key.Tab.keyCode.toInt(),
            code = "Tab"
        )

        // Tab from Field 1 to Field 2
        var htmlInput = waitForSingleLineHtmlInput()
        htmlInput.dispatchEvent(tabKeyDown)
        awaitAnimationFrame()

        assertEquals(false, field1FocusState!!.isFocused)
        assertEquals(true, field2FocusState!!.isFocused)

        // Tab from Field 2 to Field 3 (read-only)
        // This removes the backing HTML input because read-only fields don't create one
        htmlInput = waitForSingleLineHtmlInput()
        htmlInput.dispatchEvent(tabKeyDown)
        awaitAnimationFrame()

        assertEquals(false, field2FocusState!!.isFocused)
        assertEquals(true, field3FocusState!!.isFocused, "Read-only Field 3 should be focused")

        // Verify no backing input exists (read-only TextField doesn't create one)
        val inputAfterReadOnlyFocus = getShadowRoot().querySelector("input")
        assertEquals(null, inputAfterReadOnlyFocus, "Read-only TextField should not have backing input")

        // Tab from read-only Field 3 to Field 4
        // Since there's no backing input, we dispatch Tab to the canvas
        // This is where the bug manifests without the fix - the canvas wouldn't have DOM focus
        val canvas = getCanvas()
        canvas.dispatchEvent(tabKeyDown)
        awaitAnimationFrame()

        assertEquals(false, field3FocusState!!.isFocused, "Read-only Field 3 should lose focus")
        assertEquals(true, field4FocusState!!.isFocused, "Field 4 should be focused (not back to Field 1)")
    }
}