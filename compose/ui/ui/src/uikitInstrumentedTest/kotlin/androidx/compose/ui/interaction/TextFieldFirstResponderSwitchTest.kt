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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.utils.TestTextField
import androidx.compose.ui.test.utils.TestTextFieldState
import androidx.compose.ui.test.utils.findFirstDescendant
import androidx.compose.ui.test.utils.runTextFieldTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.window.ComposeTextInputView
import androidx.compose.ui.window.NativeTextInputView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFieldFirstResponderSwitchTest {

    @Test
    fun switchingNativeTextInputShouldSwitchFirstResponder() =
        runTextFieldTest { _, usingNativeTextInput, textFieldType ->
            val state = TestTextFieldState(text = FIELD_TEXT, textFieldType = textFieldType)
            var nativeTextInput by mutableStateOf(usingNativeTextInput)
            val focusRequester = FocusRequester()

            fun assertFirstResponder(isNativeTextInput: Boolean) {
                if (isNativeTextInput) {
                    assertTrue(findFirstDescendant { it.isFirstResponder } is NativeTextInputView)
                } else {
                    assertTrue(findFirstDescendant { it.isFirstResponder } is ComposeTextInputView)
                }
            }

            setContent {
                TestTextField(
                    state = state,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        platformImeOptions = PlatformImeOptions {
                            usingNativeTextInput(nativeTextInput)
                        }
                    ),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }

            assertFirstResponder(isNativeTextInput = usingNativeTextInput)

            nativeTextInput = !usingNativeTextInput
            waitForIdle()

            assertFirstResponder(isNativeTextInput = !usingNativeTextInput)
        }

    @Test
    fun tapInsideTextFieldShouldWorkAfterSwitchingNativeTextInput() =
        runTextFieldTest { _, usingNativeTextInput, textFieldType ->
            val state = TestTextFieldState(
                text = FIELD_TEXT,
                selection = TextRange(FIELD_TEXT.length),
                textFieldType = textFieldType
            )
            var nativeTextInput by mutableStateOf(usingNativeTextInput)
            val focusRequester = FocusRequester()

            setContent {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TestTextField(
                        state = state,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                        keyboardOptions = KeyboardOptions(
                            platformImeOptions = PlatformImeOptions {
                                usingNativeTextInput(nativeTextInput)
                            }
                        ),
                    )
                }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }

            // 1. Click at the start of the text.
            findNodeWithTag(FIELD_TAG).tapCharacter(0)
            waitForIdle()
            assertEquals(TextRange(0), state.selection)

            // 2. Toggle usingNativeTextInput.
            nativeTextInput = !usingNativeTextInput
            waitForIdle()

            // 3. Click at the end of the text — it must keep working after the switch.
            findNodeWithTag(FIELD_TAG).tapCharacter(FIELD_TEXT.length)
            waitForIdle()
            assertEquals(TextRange(FIELD_TEXT.length), state.selection)
        }

    companion object {
        private const val FIELD_TEXT = "0123456789"
        private const val FIELD_TAG = "textField"
    }
}
