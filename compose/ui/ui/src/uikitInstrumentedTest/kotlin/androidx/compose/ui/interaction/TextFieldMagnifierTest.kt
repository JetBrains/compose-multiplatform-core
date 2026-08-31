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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.NativeTextInputContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.findFirstDescendant
import androidx.compose.ui.test.utils.isLoupeView
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.uikit.LocalNativeTextInputContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalComposeUiApi::class)
class TextFieldMagnifierTest {

    private val params = listOf(
        TextFieldMagnifierParam(useNativeTextInput = false) { fr -> BTF(fr, false) },
        TextFieldMagnifierParam(useNativeTextInput = false) { fr -> BFT2(fr, false) },
        TextFieldMagnifierParam(useNativeTextInput = true) { fr -> BTF(fr, true) },
        TextFieldMagnifierParam(useNativeTextInput = true) { fr -> BFT2(fr, true) },
    )

    private val nonNativeParams = params.filterNot { it.useNativeTextInput }

    @Test
    fun testMagnifierShownOnTouchAndHold() = runUIKitInstrumentedTest(
        params = params
    ) { factory ->
        val focusRequester = FocusRequester()
        var nativeTextInputContext: NativeTextInputContext? = null

        setContent {
            val currentNativeTextInputContext = LocalNativeTextInputContext.current
            SideEffect {
                nativeTextInputContext = currentNativeTextInputContext
            }
            Column {
                Box(Modifier.height(200.dp).fillMaxWidth())
                factory(focusRequester)
            }
        }

        focusRequester.requestFocus()

        waitForIdle()

        assertEquals(
            expected = factory.useNativeTextInput,
            actual = nativeTextInputContext?.usingNativeTextInput(),
            message = "Text input mode should be ${factory.textInputModeName}"
        )

        findNodeWithTag("textField").touchDown()

        waitUntil {
            findFirstDescendant { it.isLoupeView } != null
        }
    }

    @Test
    fun testMagnifierHidesOnLift() = runUIKitInstrumentedTest(
        params = params
    ) { factory ->
        val focusRequester = FocusRequester()
        var nativeTextInputContext: NativeTextInputContext? = null

        setContent {
            val currentNativeTextInputContext = LocalNativeTextInputContext.current
            SideEffect {
                nativeTextInputContext = currentNativeTextInputContext
            }
            Column {
                Box(Modifier.height(200.dp).fillMaxWidth())
                factory(focusRequester)
            }
        }

        focusRequester.requestFocus()

        waitForIdle()

        assertEquals(
            expected = factory.useNativeTextInput,
            actual = nativeTextInputContext?.usingNativeTextInput(),
            message = "Text input mode should be ${factory.textInputModeName}"
        )

        val touch = findNodeWithTag("textField").touchDown()

        waitUntil {
            findFirstDescendant { it.isLoupeView } != null
        }

        touch.up()

        waitUntil {
            findFirstDescendant { it.isLoupeView } == null
        }
    }

    @Test
    fun testMagnifierHidesOnDragOutsideTextField() = runUIKitInstrumentedTest(
        params = nonNativeParams // magnifier is not visible but its object still remains after dragging outside text field with NITI
    ) { factory ->
        val focusRequester = FocusRequester()

        setContent {
            Column {
                Box(Modifier.height(200.dp).fillMaxWidth())
                factory(focusRequester)
            }
        }

        focusRequester.requestFocus()

        waitForIdle()

        val touch = findNodeWithTag("textField").touchDown()

        waitUntil {
            findFirstDescendant { it.isLoupeView } != null
        }

        touch.dragBy(dy = 100.dp, duration = 0.1.seconds)

        waitUntil {
            findFirstDescendant { it.isLoupeView } == null
        }
    }

    private val textValue = "TEXT"
    private fun keyboardOptions(useNativeTextInput: Boolean) = KeyboardOptions(
        platformImeOptions = PlatformImeOptions {
            usingNativeTextInput(useNativeTextInput)
        }
    )

    private fun modifier(focusRequester: FocusRequester): Modifier = Modifier
        .testTag("textField")
        .height(40.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)

    @Composable
    private fun BTF(
        focusRequester: FocusRequester,
        useNativeTextInput: Boolean
    ) = BasicTextField(
        textValue,
        onValueChange = {},
        keyboardOptions = keyboardOptions(useNativeTextInput),
        modifier = modifier(focusRequester)
    )

    @Composable
    private fun BFT2(
        focusRequester: FocusRequester,
        useNativeTextInput: Boolean
    ) {
        val state = remember { TextFieldState(textValue) }
        BasicTextField(
            state,
            keyboardOptions = keyboardOptions(useNativeTextInput),
            modifier = modifier(focusRequester)
        )
    }
}

private class TextFieldMagnifierParam(
    val useNativeTextInput: Boolean,
    private val content: @Composable (FocusRequester) -> Unit
) {
    val textInputModeName = if (useNativeTextInput) "NITI" else "non-NITI"

    @Composable
    operator fun invoke(focusRequester: FocusRequester) {
        content(focusRequester)
    }
}
