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

package androidx.compose.ui.input.specs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.events.beforeInput
import androidx.compose.ui.events.compositionEnd
import androidx.compose.ui.events.compositionStart
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.events.mobileKeyDown
import androidx.compose.ui.events.mobileKeyUp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

internal interface InputTestSpec : TextFieldTestSpec {

    // delay in web tests called directly will be completely ignored
    private suspend fun waitFor(millis: Long) {
        withContext(Dispatchers.Default) { delay(millis) }
    }

    // sends keydown / input sequence of events like in Chrome in normal mode
    private fun standardKeyboardSequence(vararg keys: String): Array<Event> {
        return keys.flatMap {  key ->
            buildList {
                add(keyEvent(key))
                // we treat anything of size > 0 as a character that does not have type representation
                if (key.length == 1) {
                    add(beforeInput(inputType = "insertText", data = key))
                }
                add(keyEvent(key, type = "keyup"))
            }
        }.toTypedArray()
    }

    private fun standardKeyboardSequence(str: String): Array<Event> = standardKeyboardSequence(*str.toCharArray().map { it.toString() }.toTypedArray())
    private fun sendStandardKeyboardSequence(str: String) = sendToHtmlInput(*standardKeyboardSequence(str))

    // type character in composite mode and trigger composition, Chrome behaviour
    private fun standardComposingSequence(typedKey: String, triggeredKey: String): List<Event> {
        return listOf(
            keyEvent(typedKey),
            beforeInput("insertCompositionText", typedKey, isComposing = true),
            compositionEnd(triggeredKey),
            keyEvent(typedKey, type = "keyup")
        )
    }

    private fun standardTriggerComposingSequence(triggerKey: String, typedKey: String, triggeredKey: String): List<Event> {
        return listOf(
            keyEvent(triggerKey),
            compositionStart(),
            beforeInput("insertCompositionText", triggerKey),
            keyEvent(triggerKey, type = "keyup", isComposing = true),
            *standardComposingSequence(typedKey, triggeredKey).toTypedArray()
        )
    }

    @Test
    fun positionInput() = runApplicationTest {
        val focusRequester = FocusRequester()
        val inputHolder = createTestInputState()

        var leftState by mutableStateOf(0.dp)
        var topState by mutableStateOf(0.dp)

        createComposeWindow {
            Box(modifier = Modifier.padding(horizontal = leftState, vertical = topState)) {
                inputHolder.createBasicTextField(focusRequester)
            }
        }

        focusRequester.requestFocus()
        waitForHtmlInput()

        sendStandardKeyboardSequence("abc")

        inputHolder.awaitAndAssertTextEquals("abc")

        val clientRectInitial = currentHtmlInput().getBoundingClientRect()

        leftState = 50.dp
        focusRequester.requestFocus()
        awaitIdle()

        val clientRectUpdated = currentHtmlInput().getBoundingClientRect()

        assertEquals(50.0, clientRectUpdated.left - clientRectInitial.left, "left position updated")

        focusRequester.requestFocus()
        awaitIdle()

        // intentionally huge, will never grow over viewport nevertheless
        topState = 10000000.dp

        focusRequester.requestFocus()
        awaitIdle()

        var clientRectSticky= currentHtmlInput().getBoundingClientRect()
        val expectedTopValue = window.innerHeight - clientRectSticky.height

        // TODO: In Firefox there's a 0.5 delta - may be this can be accounted precisely somehow
        val topDelta = clientRectSticky.top - expectedTopValue
        val deltaThreshold = 1.01
        assertTrue(topDelta.absoluteValue < deltaThreshold, "top position sticky $topDelta")

        // intentionally huge, will never grow over viewport nevertheless
        leftState = 10000000.dp

        focusRequester.requestFocus()
        awaitIdle()

        clientRectSticky= currentHtmlInput().getBoundingClientRect()
        val expectedLeftValue = window.innerWidth - clientRectSticky.width
        val leftDelta = clientRectSticky.left - expectedLeftValue
        assertTrue(leftDelta.absoluteValue < deltaThreshold, "left position sticky $leftDelta")
    }


    @Test
    fun regularInput() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendStandardKeyboardSequence("step1")
        textFieldValue.awaitAndAssertTextEquals("step1")

        sendToHtmlInput(
            keyEvent("Backspace", code = "Backspace"),
            keyEvent("X"),
            beforeInput(inputType = "insertText", data = "X"),
        )

        textFieldValue.awaitAndAssertTextEquals(
            "stepX",
            "Backspace should delete last symbol typed"
        )
    }

    @Test
    fun compositeInput() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        val backingTextField = getShadowRoot().querySelector("textarea")
        assertIs<HTMLTextAreaElement>(backingTextField)

        sendToHtmlInput(
        *standardTriggerComposingSequence("a", "1", "啊").toTypedArray()
        )

        textFieldValue.awaitAndAssertTextEquals("啊")

        sendToHtmlInput(
            keyEvent("x"),
            beforeInput(inputType = "insertText", data = "x"),
            keyEvent("x", type = "keyup")
        )

        textFieldValue.awaitAndAssertTextEquals("啊x")
    }

    @Test
    fun compositeInputWebkit() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        val keyEvent = keyEvent("1")

        // We can not change timestamp for js events, so we just add some delay to enforce it
        waitFor(50)

        sendToHtmlInput(
            compositionStart(),
            keyEvent("a", isComposing = true),
            keyEvent("a", type = "keyup", isComposing = true),
            beforeInput("deleteCompositionText", null),
            beforeInput("insertFromComposition", "啊"),
            compositionEnd("啊"),
            keyEvent,
            keyEvent("1", type = "keyup"),
        )

        textFieldValue.awaitAndAssertTextEquals("啊")

        // We can not change timestamp for js events, so we just add some delay to enforce it
        waitFor(100)

        sendToHtmlInput(
            keyEvent("b"),
            beforeInput(inputType = "insertText", data = "b"),
            keyEvent("b", type = "keyup")
        )

        textFieldValue.awaitAndAssertTextEquals("啊b")
    }

    @Test
    fun mobileInput() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendToHtmlInput(
            mobileKeyDown(),
            compositionStart(),
            beforeInput("insertCompositionText", "a"),
            mobileKeyUp(),
            mobileKeyDown(),
            beforeInput("insertCompositionText", "ab"),
            mobileKeyUp(),
            mobileKeyDown(),
            beforeInput("insertCompositionText", "abc"),
            mobileKeyUp()
        )

        textFieldValue.awaitAndAssertTextEquals("abc")
    }

    @Ignore
    @Test
    fun repeatedAccent() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            keyEvent("b"),
            beforeInput("insertText", "b"),
            keyEvent("b", type = "keyup"),
            keyEvent("c"),
            beforeInput("insertText", "c"),
            keyEvent("c", type = "keyup")
        )

        // TODO: this does not behave as desktop, ideally we should have "abc" here
        textFieldValue.awaitAndAssertTextEquals(
            "bc",
            "Repeat mode should be resolved as Accent Dialogue"
        )

        sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            keyEvent("b"),
            beforeInput("insertText", "b"),
            keyEvent("b", type = "keyup"),
            keyEvent("c"),
            beforeInput("insertText", "c"),
            keyEvent("c", type = "keyup")
        )

    }

    @Test
    fun repeatedDefault() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendToHtmlInput(
            keyEvent("a"),
            beforeInput("insertText", "a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("b"),
            beforeInput(inputType = "insertText", data = "b"),
            keyEvent("c"),
            beforeInput(inputType = "insertText", data = "c"),
        )


        textFieldValue.awaitAndAssertTextMatches( Regex("a+bc"), "Repeat mode should be resolved as Default")
    }

    @Test
    fun repeatedAccentMenuPressed() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            keyEvent("1"),
            beforeInput(inputType = "insertText", data = "à"),
            keyEvent("1", type = "keyup"),
        )

        textFieldValue.awaitAndAssertTextEquals("à", "Choose symbol from Accent Menu")
    }

    @Test
    fun repeatedAccentMenuIgnoreNonTyped() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        sendToHtmlInput(
            keyEvent("ArrowLeft", code = "ArrowLeft"),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", type = "keyup"),
            keyEvent("a"),
            beforeInput(inputType = "insertText", data = "a"),
            keyEvent("a", type = "keyup"),
            keyEvent("b"),
            beforeInput(inputType = "insertText", data = "b"),
            keyEvent("b", type = "keyup"),
            keyEvent("c"),
            beforeInput(inputType = "insertText", data = "c"),
            keyEvent("c", type = "keyup"),
        )

        textFieldValue.awaitAndAssertTextEquals("abc", "XXX")
    }

    @Test
    fun repeatedAccentMenuClicked() = runApplicationTest {
        val textFieldValue =  createApplicationWithHolder()

        sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            beforeInput(inputType = "insertText", data = "æ"),
        )

        textFieldValue.awaitAndAssertTextEquals("æ", "Choose symbol from Accent Menu")
    }


    @Test
    fun keyboardEventPassedToTextField() = runApplicationTest {
        val focusRequester1 = FocusRequester()
        val focusRequester2 = FocusRequester()

        val inputHolder1 = createTestInputState()
        val inputHolder2 = createTestInputState()

        createComposeWindow {
            inputHolder1.createBasicTextField(focusRequester1)
            inputHolder2.createBasicTextField(focusRequester2)
        }

        focusRequester1.requestFocus()
        waitForHtmlInput()

        sendStandardKeyboardSequence("step1")
        inputHolder1.awaitAndAssertTextEquals("step1")

        focusRequester2.requestFocus()
        waitForHtmlInput()

        sendStandardKeyboardSequence("step2")
        inputHolder2.awaitAndAssertTextEquals("step2")
    }
}