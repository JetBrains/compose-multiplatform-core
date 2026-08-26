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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.test.utils.findFirstDescendant
import androidx.compose.ui.test.utils.isLoupeView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.endEditing

class TextFieldMultiTapSelectionTest {

    // Each field on both text input paths: the Compose one and the native one.
    private val tfOptions = listOf(false, true).flatMap { nativeInput ->
        BasicTextFieldType.entries.map { TextFieldFactory(it, nativeInput) }
    }

    @Test
    fun double_tap_selects_word() = runUIKitInstrumentedTest(params = tfOptions) { textFieldOption ->
        textFieldOption.setup(this, TEXT, TAG)
        findNodeWithTag(TAG).multiTapCharacter(offset = WORD_OFFSET, count = 2)
        waitForIdle()
        assertEquals(
            WORD_RANGE,
            textFieldOption.selection,
            "[${textFieldOption.name}] Expected double tap inside '$WORD' to select it, but got: ${textFieldOption.selection}"
        )
        if (textFieldOption.nativeInput) dropNativeSelection()
    }

    @Test
    fun triple_tap_selects_all_text() = runUIKitInstrumentedTest(params = tfOptions) { textFieldOption ->
        textFieldOption.setup(this, TEXT, TAG)
        findNodeWithTag(TAG).multiTapCharacter(offset = WORD_OFFSET, count = 3)
        waitForIdle()
        assertEquals(
            TextRange(0, TEXT.length),
            textFieldOption.selection,
            "[${textFieldOption.name}] Expected all text to be selected after triple tap, but got: ${textFieldOption.selection}"
        )
        if (textFieldOption.nativeInput) dropNativeSelection()
    }

    @Test
    fun multitap_does_not_show_magnifier() = runUIKitInstrumentedTest(params = tfOptions) { textFieldOption ->
        textFieldOption.setup(this, TEXT, TAG)
        findNodeWithTag(TAG).multiTapCharacter(offset = WORD_OFFSET, count = 2) // double tap is enough
        delay(200)
        assertEquals(
            findFirstDescendant { it.isLoupeView },
            null,
            "[${textFieldOption.name}] Magnifier should not appear during multi-tap selection"
        )
        if (textFieldOption.nativeInput) dropNativeSelection()
    }

    @Test
    fun BTF2_triple_tap_then_double_tap_selects_word() = runUIKitInstrumentedTest(params = listOf(TextFieldFactory(BasicTextFieldType.V2, nativeInput = false))) { textFieldOption ->
        textFieldOption.setup(this, TEXT, TAG)

        findNodeWithTag(TAG).multiTapCharacter(offset = WORD_OFFSET, count = 3)
        waitForIdle()
        assertEquals(
            TextRange(0, TEXT.length),
            textFieldOption.selection,
            "BTF2: triple tap should select all text, but got: ${textFieldOption.selection}"
        )

        // After triple tap selects all, a subsequent double tap should re-select only a word.
        // This exercises the clearSelection fix that allows selection to be updated by repeated taps.
        delay(400)
        findNodeWithTag(TAG).multiTapCharacter(offset = WORD_OFFSET, count = 2)
        waitForIdle()

        assertEquals(
            WORD_RANGE,
            textFieldOption.selection,
            "BTF2: Expected only '$WORD' to be selected after double tap, but got: ${textFieldOption.selection}"
        )
    }

    /** Ends the native editing session, so its selection does not outlive the test. */
    private fun UIKitInstrumentedTest.dropNativeSelection() {
        viewController.view.endEditing(force = true)
        // TODO: CMP-10641
        delay(500)
    }

    companion object {
        private const val TAG = "textField"
        private const val TEXT = "The quick brown fox"
        /** The word the tests tap on, and the selection a double tap on it should produce. */
        private const val WORD = "quick"
        private val WORD_RANGE = TextRange(TEXT.indexOf(WORD), TEXT.indexOf(WORD) + WORD.length)
        /** The middle of [WORD], so that a tap lands inside it rather than on either edge. */
        private val WORD_OFFSET = WORD_RANGE.min + WORD.length / 2
    }
}

private class TextFieldFactory(val type: BasicTextFieldType, val nativeInput: Boolean) {
    private var _selection: (() -> TextRange)? = null

    val selection: TextRange get() = _selection!!()

    val name: String get() {
        val field = when (type) {
            BasicTextFieldType.V1 -> "BasicTextField(value)"
            BasicTextFieldType.V2 -> "BasicTextField(state)"
        }
        return if (nativeInput) "$field, native input" else field
    }

    fun setup(test: UIKitInstrumentedTest, text: String, tag: String) {
        val focusRequester = FocusRequester()
        val keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            platformImeOptions = PlatformImeOptions { usingNativeTextInput(nativeInput) }
        )
        val modifier = Modifier
            .focusRequester(focusRequester)
            .testTag(tag)
            .padding(16.dp)

        _selection = when (type) {
            BasicTextFieldType.V1 -> {
                val valueState = mutableStateOf(TextFieldValue(text))
                test.setContent {
                    Box(Modifier.fillMaxSize()) {
                        BasicTextField(
                            value = valueState.value,
                            onValueChange = { valueState.value = it },
                            keyboardOptions = keyboardOptions,
                            modifier = Modifier.align(Alignment.Center).then(modifier)
                        )
                    }
                }
                ({ valueState.value.selection })
            }
            BasicTextFieldType.V2 -> {
                val state = TextFieldState(text)
                test.setContent {
                    Box(Modifier.fillMaxSize()) {
                        BasicTextField(
                            state = state,
                            keyboardOptions = keyboardOptions,
                            modifier = Modifier.align(Alignment.Center).then(modifier)
                        )
                    }
                }
                ({ state.selection })
            }
        }
        // Focused programmatically, so no focus-tap is fed to UIKit's multi-tap recognizer.
        focusRequester.requestFocus()
        test.waitForIdle()
    }
}
