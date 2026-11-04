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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextInputProtocol

/**
 * Checks that marked (composing) text driven through the `UITextInput` contract — `setMarkedText`,
 * `markedTextRange` and `unmarkText`, called the way the system input calls them — is correctly
 * reflected in the focused Compose text field: the inserted text, the composing region reported
 * back to UIKit, the composition in the Compose value, and the IME selection inside the marked
 * text.
 *
 * Note: the marked text *style* is intentionally not asserted. On iOS the system underlines marked
 * text by setting `UITextInput.markedTextStyle` on the text input, but both input views
 * (`NativeTextInputView` and `ComposeTextInputView`) currently discard it — `setMarkedTextStyle`
 * is a no-op and `markedTextStyle()` returns null — and the Compose text pipeline applies no
 * styling to the composition range. The underscore attribute is therefore not observable through
 * any API available to this test.
 */
class TextFieldMarkedTextTest {

    @Test fun setMarkedText_btf1() = runUIKitInstrumentedTest { checkMarkedText(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun setMarkedText_btf2() = runUIKitInstrumentedTest { checkMarkedText(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun setMarkedText_nitiBtf1() = runUIKitInstrumentedTest { checkMarkedText(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun setMarkedText_nitiBtf2() = runUIKitInstrumentedTest { checkMarkedText(BasicTextFieldType.V2, nativeInput = true) }
}

private const val FIELD_TAG = "textField"

@Suppress("DEPRECATION")
private fun UIKitInstrumentedTest.setUpMarkedTextField(
    btf: BasicTextFieldType,
    nativeInput: Boolean,
): () -> TextFieldValue {
    val ime = KeyboardOptions(
        // Disable autocorrect/spell-check so the native iOS suggestion machinery does not
        // interfere with the marked text driven by the test.
        autoCorrectEnabled = false,
        platformImeOptions = PlatformImeOptions { usingNativeTextInput(nativeInput) }
    )
    val focusRequester = FocusRequester()
    // "hello" with the cursor at its end, so the composition starts right after the typed text.
    val value: () -> TextFieldValue
    when (btf) {
        BasicTextFieldType.V1 -> {
            val textFieldValue = mutableStateOf(TextFieldValue("hello", selection = TextRange(5)))
            setContent {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    BasicTextField(
                        value = textFieldValue.value,
                        onValueChange = { textFieldValue.value = it },
                        keyboardOptions = ime,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            value = { textFieldValue.value }
        }
        BasicTextFieldType.V2 -> {
            val state = TextFieldState("hello", initialSelection = TextRange(5))
            setContent {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    BasicTextField(
                        state = state,
                        keyboardOptions = ime,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            value = { TextFieldValue(state.text.toString(), state.selection, state.composition) }
        }
    }

    focusRequester.requestFocus()
    waitForIdle()
    return value
}

@OptIn(ExperimentalForeignApi::class)
private fun UIKitInstrumentedTest.checkMarkedText(btf: BasicTextFieldType, nativeInput: Boolean) {
    val value = setUpMarkedTextField(btf, nativeInput)
    val input = assertNotNull(findFocusedUITextInput(), "No focused text input view found")

    // The input starts composing at the caret: "hello[ wor]".
    input.setMarkedText(markedText = " wor", selectedRange = NSMakeRange(0u, 0u))
    waitForIdle()

    assertMarkedTextRange(input, start = 5, end = 9)
    assertEquals("hello wor", value().text, "Composing text should be inserted after \"hello\"")
    assertEquals(TextRange(5, 9), value().composition, "Composing region should cover the marked text")
    assertEquals(TextRange(5), value().selection, "Selection should stay at the composing region start")

    // The input updates the composition and selects a range inside the marked text: "hello[ world]".
    input.setMarkedText(markedText = " world", selectedRange = NSMakeRange(3u, 2u))
    waitForIdle()

    assertMarkedTextRange(input, start = 5, end = 11)
    assertEquals("hello world", value().text, "Updated marked text should replace the previous one")
    assertEquals(TextRange(5, 11), value().composition, "Composing region should cover the updated marked text")
    assertEquals(TextRange(8, 10), value().selection, "Selection should be reported relative to the marked text")

    // Unmarking commits the marked text and keeps the selection.
    input.unmarkText()
    waitForIdle()

    assertNull(input.markedTextRange(), "markedTextRange should be nil after unmarkText")
    assertEquals("hello world", value().text, "Unmarked text should stay in the field")
    assertNull(value().composition, "Composition should be cleared after unmarkText")
    assertEquals(TextRange(8, 10), value().selection, "Selection should be kept after unmarkText")
}

private fun assertMarkedTextRange(input: UITextInputProtocol, start: Int, end: Int) {
    val markedRange = assertNotNull(input.markedTextRange(), "markedTextRange should not be nil while composing")
    val actualStart = input.offsetFromPosition(input.beginningOfDocument(), markedRange.start).toInt()
    val actualEnd = input.offsetFromPosition(input.beginningOfDocument(), markedRange.end).toInt()
    assertEquals(start, actualStart, "Unexpected markedTextRange start")
    assertEquals(end, actualEnd, "Unexpected markedTextRange end")
}
