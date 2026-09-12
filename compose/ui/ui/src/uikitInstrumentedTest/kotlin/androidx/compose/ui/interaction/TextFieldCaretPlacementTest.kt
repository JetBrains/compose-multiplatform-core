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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.BasicTextFieldType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFieldCaretPlacementTest {

    @Test fun tapCharacter_btf1() = runUIKitInstrumentedTest { checkTapCharacter(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun tapCharacter_btf2() = runUIKitInstrumentedTest { checkTapCharacter(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun tapCharacter_nitiBtf1() = runUIKitInstrumentedTest { checkTapCharacter(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun tapCharacter_nitiBtf2() = runUIKitInstrumentedTest { checkTapCharacter(BasicTextFieldType.V2, nativeInput = true) }

    @Test fun longPressCharacter_btf1() = runUIKitInstrumentedTest { checkLongPressCharacter(BasicTextFieldType.V1, nativeInput = false) }
    @Test fun longPressCharacter_btf2() = runUIKitInstrumentedTest { checkLongPressCharacter(BasicTextFieldType.V2, nativeInput = false) }
    @Test fun longPressCharacter_nitiBtf1() = runUIKitInstrumentedTest { checkLongPressCharacter(BasicTextFieldType.V1, nativeInput = true) }
    @Test fun longPressCharacter_nitiBtf2() = runUIKitInstrumentedTest { checkLongPressCharacter(BasicTextFieldType.V2, nativeInput = true) }
}

private const val FIELD_TAG = "textField"

@Composable
private fun CaptionedField(caption: String?, field: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (caption != null) {
                BasicText(
                    text = caption,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
            field()
        }
    }
}

private fun UIKitInstrumentedTest.setUpField(
    text: String,
    btf: BasicTextFieldType,
    nativeInput: Boolean,
    caption: String? = null,
): () -> TextRange {
    val ime = KeyboardOptions(
        // Disable autocorrect/spell-check so the native iOS replacement-suggestion bubble does not
        // pop over the selection during the test (spellCheckingType defaults to follow this).
        autoCorrectEnabled = false,
        platformImeOptions = PlatformImeOptions { usingNativeTextInput(nativeInput) }
    )
    val focusRequester = FocusRequester()
    val variant = "${if (nativeInput) "NITI" else "Compose"} BTF${if (btf == BasicTextFieldType.V1) "1" else "2"}"
    val label = caption?.let { "$variant\n$it" }
    val selection: () -> TextRange = when (btf) {
        BasicTextFieldType.V1 -> {
            val value = mutableStateOf(TextFieldValue(text))
            setContent {
                CaptionedField(label) {
                    BasicTextField(
                        value = value.value,
                        onValueChange = { value.value = it },
                        keyboardOptions = ime,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            ({ value.value.selection })
        }
        BasicTextFieldType.V2 -> {
            val state = TextFieldState(text)
            setContent {
                CaptionedField(label) {
                    BasicTextField(
                        state = state,
                        keyboardOptions = ime,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(FIELD_TAG),
                    )
                }
            }
            ({ state.selection })
        }
    }
    focusRequester.requestFocus()
    waitForIdle()
    return selection
}

private fun UIKitInstrumentedTest.checkTapCharacter(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField("The quick brown fox", btf, nativeInput)
    findNodeWithTag(FIELD_TAG).tapCharacter(offset = 8)
    waitForIdle()
    val resultSelection = selection()
    assertTrue(resultSelection.collapsed, "expected a collapsed cursor after tap, got $resultSelection")
    assertEquals(9, resultSelection.start, "trailing tap in 'quick' should snap to its end; landed at ${resultSelection.start}")
}

private fun UIKitInstrumentedTest.checkLongPressCharacter(btf: BasicTextFieldType, nativeInput: Boolean) {
    val selection = setUpField("The quick brown fox", btf, nativeInput)
    findNodeWithTag(FIELD_TAG).longPressCharacter(offset = 6)
    waitForIdle()
    assertEquals(TextRange(6), selection(), "long press at offset 6 should leave the caret there")
}
