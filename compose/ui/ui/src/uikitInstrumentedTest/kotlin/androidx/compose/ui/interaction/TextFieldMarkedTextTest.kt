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

package androidx.compose.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.focusedUITextInput
import androidx.compose.ui.test.utils.TestTextField
import androidx.compose.ui.test.utils.TestTextFieldState
import androidx.compose.ui.test.utils.runTextFieldTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextInputProtocol

@OptIn(ExperimentalForeignApi::class)
class TextFieldMarkedTextTest {

    @Test
    fun setMarkedText() = runTextFieldTest(
        newContextMenuEnabled = false, // Context menu is not goal of this test
    ) { _, nativeInput, textFieldType ->
        // "hello" with the cursor at its end, so the composition starts right after the typed text.
        val state = TestTextFieldState(
            text = "hello",
            selection = TextRange(5),
            textFieldType = textFieldType,
        )
        val focusRequester = FocusRequester()
        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                TestTextField(
                    state = state,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag(FIELD_TAG),
                    keyboardOptions = KeyboardOptions(
                        platformImeOptions = PlatformImeOptions { usingNativeTextInput(nativeInput) }
                    ),
                )
            }
        }
        focusRequester.requestFocus()
        waitForIdle()

        val input = focusedUITextInput()

        // The input starts composing at the caret: "hello[ wor]".
        input.setMarkedText(markedText = " wor", selectedRange = NSMakeRange(0u, 0u))
        waitForIdle()

        assertMarkedTextRange(input, start = 5, end = 9)
        assertEquals("hello wor", state.text, "Composing text should be inserted after \"hello\"")
        assertEquals(TextRange(5, 9), state.composition, "Composing region should cover the marked text")
        assertEquals(TextRange(5), state.selection, "Selection should stay at the composing region start")

        // The input updates the composition and selects a range inside the marked text: "hello[ world]".
        input.setMarkedText(markedText = " world", selectedRange = NSMakeRange(3u, 2u))
        waitForIdle()

        assertMarkedTextRange(input, start = 5, end = 11)
        assertEquals("hello world", state.text, "Updated marked text should replace the previous one")
        assertEquals(TextRange(5, 11), state.composition, "Composing region should cover the updated marked text")
        assertEquals(TextRange(8, 10), state.selection, "Selection should be reported relative to the marked text")

        // Unmarking commits the marked text and keeps the selection.
        input.unmarkText()
        waitForIdle()

        assertNull(input.markedTextRange(), "markedTextRange should be nil after unmarkText")
        assertEquals("hello world", state.text, "Unmarked text should stay in the field")
        assertNull(state.composition, "Composition should be cleared after unmarkText")
        assertEquals(TextRange(8, 10), state.selection, "Selection should be kept after unmarkText")
    }

    private fun assertMarkedTextRange(input: UITextInputProtocol, start: Int, end: Int) {
        val markedRange = assertNotNull(input.markedTextRange(), "markedTextRange should not be nil while composing")
        val actualStart = input.offsetFromPosition(input.beginningOfDocument(), markedRange.start).toInt()
        val actualEnd = input.offsetFromPosition(input.beginningOfDocument(), markedRange.end).toInt()
        assertEquals(start, actualStart, "Unexpected markedTextRange start")
        assertEquals(end, actualEnd, "Unexpected markedTextRange end")
    }

    private companion object {
        const val FIELD_TAG = "textField"
    }
}
