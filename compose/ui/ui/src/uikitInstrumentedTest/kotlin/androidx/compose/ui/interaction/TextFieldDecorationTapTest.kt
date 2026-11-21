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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.test.utils.TestTextField
import androidx.compose.ui.test.utils.TestTextFieldState
import androidx.compose.ui.test.utils.runTextFieldTest
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.test.utils.wait
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class TextFieldDecorationTapTest {

    @Test
    fun tapOverDecorationShouldFocusesTextFieldAndPlacesCursorAtEnd() =
        runTextFieldTest { _, usingNativeTextInput, textFieldType ->
            val state = TestTextFieldState(text = FIELD_TEXT, textFieldType = textFieldType)
            var isFocused by mutableStateOf(false)

            setContent {
                DecoratedTextField(
                    state = state,
                    usingNativeTextInput = usingNativeTextInput,
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            assertFalse(isFocused, "The text field must not be focused before the tap")

            findNodeWithTag(DECORATION_TAG)
                .touchDown()
                .wait(0.3.seconds)
                .up()
            waitForIdle()

            waitUntil("Tapping the decoration must focus the text field") { isFocused }
            assertEquals(
                TextRange(FIELD_TEXT.length),
                state.selection,
                "Tapping the trailing decoration must place the cursor at the end of the text"
            )
        }

    @Test
    fun tapOverNoninteractiveDecorationShouldSetCursor() =
        runTextFieldTest { _, usingNativeTextInput, textFieldType ->
            val state = TestTextFieldState(
                text = FIELD_TEXT,
                selection = TextRange(0),
                textFieldType = textFieldType
            )
            val focusRequester = FocusRequester()

            setContent {
                DecoratedTextField(
                    state = state,
                    usingNativeTextInput = usingNativeTextInput,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }

            findNodeWithTag(DECORATION_TAG).tap()
            waitForIdle()

            assertEquals(
                TextRange(FIELD_TEXT.length),
                state.selection,
                "Tapping the decoration of a focused text field must place the cursor at the " +
                    "end of the text (usingNativeTextInput=$usingNativeTextInput)"
            )
        }

    @Test
    fun tapOverInteractiveDecorationShouldTriggerButtonWithoutMovingCursor() =
        runTextFieldTest(
            newContextMenuEnabled=true, usingNativeTextInput=true, textFieldType= BasicTextFieldType.V1
        ) { _, usingNativeTextInput, textFieldType ->
            val state = TestTextFieldState(
                text = FIELD_TEXT,
                selection = TextRange(0),
                textFieldType = textFieldType
            )
            var buttonClicks by mutableStateOf(0)
            val focusRequester = FocusRequester()

            setContent {
                DecoratedTextField(
                    state = state,
                    usingNativeTextInput = usingNativeTextInput,
                    modifier = Modifier.focusRequester(focusRequester),
                    trailingContent = {
                        Button(
                            onClick = {
                                println("Tapping the button")
                                buttonClicks++
                            },
                            modifier = Modifier.testTag(BUTTON_TAG)
                        ) {
                            Text("X")
                        }
                    }
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            // delay(2000)
            println("Sel 1 - button clicked: ${state.selection}")

            findNodeWithTag(BUTTON_TAG).tap()
            waitForIdle()

            // delay(2000)
            println("Sel 2 - button clicked: ${state.selection}")

            assertEquals(1, buttonClicks, "Tapping the button decoration must trigger the button")
            assertEquals(
                TextRange(0),
                state.selection,
                "Tapping the button decoration must not move the cursor " +
                    "(usingNativeTextInput=$usingNativeTextInput)"
            )
        }

    @Composable
    private fun DecoratedTextField(
        state: TestTextFieldState,
        usingNativeTextInput: Boolean,
        modifier: Modifier = Modifier,
        trailingContent: @Composable RowScope.() -> Unit = {
            BasicText(" EUR", Modifier.alignByBaseline().testTag(DECORATION_TAG))
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TestTextField(
                state = state,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(FIELD_WIDTH)
                    .then(modifier),
                keyboardOptions = KeyboardOptions(
                    platformImeOptions = PlatformImeOptions {
                        usingNativeTextInput(usingNativeTextInput)
                    }
                ),
                decorationBox = { innerTextField ->
                    Row {
                        Box(
                            Modifier.weight(1f)
                        ) {
                            innerTextField()
                        }
                        trailingContent()
                    }
                },
            )
        }
    }

    companion object {
        private const val FIELD_TEXT = "0123"
        private const val DECORATION_TAG = "decoration"
        private const val BUTTON_TAG = "button"
        private val FIELD_WIDTH = 280.dp
    }
}
