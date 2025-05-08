/*
 * Copyright 2024 The Android Open Source Project
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.events.InputEvent
import androidx.compose.ui.events.InputEventInit
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.sendFromScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.CompositionEventInit
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

private class InputChannel(
    private val channel: Channel<String> = Channel<String>(
        1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
) {
    lateinit  var htmlInput: HTMLTextAreaElement
    suspend fun receive() = channel.receive()
}

class TextInputTests : OnCanvasTests  {

    private fun InputChannel.sendToHtmlInput(vararg events: Event) {
        dispatchEvents(htmlInput, *events)
    }

    private fun createTextFieldWithChannel(): InputChannel {
        val textInputChannel = Channel<String>(
            1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val inputChannel = InputChannel(textInputChannel)

        val (firstFocusRequester) = FocusRequester.createRefs()

        createComposeWindow {
            BasicTextField(
                value = "",
                onValueChange = { value ->
                    textInputChannel.sendFromScope(value)
                },
                modifier = Modifier.focusRequester(firstFocusRequester)
            )

            LaunchedEffect(Unit) {
                firstFocusRequester.requestFocus()
                inputChannel.htmlInput = document.querySelector("textarea") as HTMLTextAreaElement
            }
        }

        return inputChannel
    }

    @Test
    fun regularInput() = runTest {
        val textInputChannel = createTextFieldWithChannel()

        yield()

        textInputChannel.sendToHtmlInput(
            keyEvent("s"),
            keyEvent("t"),
            keyEvent("e"),
            keyEvent("p"),
            keyEvent("1")
        )

        assertEquals("step1", textInputChannel.receive())

        textInputChannel.sendToHtmlInput(
            keyEvent("Backspace", code = "Backspace"),
            keyEvent("X"),
        )

        assertEquals("stepX", textInputChannel.receive(), "Backspace should delete last symbol typed")
    }

    @Test
    fun compositeInput() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        val backingTextField = document.querySelector("textarea")
        assertIs<HTMLTextAreaElement>(backingTextField)

        textInputChannel.sendToHtmlInput(
            keyEvent("a"),
            compositionStart(),
            beforeInput("insertCompositionText", "a"),
            keyEvent("a", type = "keyup"),
            keyEvent("1", code = "Digit1", isComposing = true),
            beforeInput("insertCompositionText", "啊"),
            compositionEnd("啊"),
            keyEvent("1", code = "Digit1", type = "keyup"),
        )

        textInputChannel.sendToHtmlInput(
            keyEvent("X"),
            keyEvent("X", type = "keyup")
        )

        assertEquals("啊X", textInputChannel.receive())
    }

    @Ignore // TODO: https://youtrack.jetbrains.com/issue/CMP-7978
    @Test
    fun compositeInputWebkit() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        val keyEvent = keyEvent("1", code = "Digit1")

        delay(50)

        textInputChannel.sendToHtmlInput(
            compositionStart(),
            keyEvent("a", isComposing = true),
            keyEvent("a", type = "keyup", isComposing = true),
            beforeInput("deleteCompositionText", null),
            beforeInput("insertFromComposition", "啊"),
            compositionEnd("啊"),
            keyEvent,
            keyEvent("1", type="keyup", code = "Digit1"),
        )

        delay(50)

        textInputChannel.sendToHtmlInput(
            keyEvent("b"),
            keyEvent("b", type = "keyup")
        )

        assertEquals("啊b", textInputChannel.receive())
    }

    @Test
    fun mobileInput() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
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

        assertEquals("abc", textInputChannel.receive())
    }

    @Ignore
    @Test
    fun repeatedAccent() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
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
        assertEquals("bc", textInputChannel.receive(), "Repeat mode should be resolved as Accent Dialogue")

        textInputChannel.sendToHtmlInput(
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
    fun repeatedDefault() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            beforeInput("insertText", "a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("b"),
            keyEvent("c")
        )

        assertTrue(Regex("a+bc").matches(textInputChannel.receive()), "Repeat mode should be resolved as Default")
    }

    @Test
    fun repeatedAccentMenuPressed() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            keyEvent("1", code = "Digit1"),
            beforeInput(inputType = "insertText", data = "à"),
            keyEvent("1", code = "Digit1", type = "keyup"),
        )

        assertEquals("à", textInputChannel.receive(), "Choose symbol from Accent Menu")
    }

    @Test
    fun repeatedAccentMenuIgnoreNonTyped() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
            keyEvent("ArrowLeft", code = "ArrowLeft"),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", repeat = true),
            keyEvent("ArrowLeft", code = "ArrowLeft", type = "keyup"),
            keyEvent("a"),
            keyEvent("a", type = "keyup"),
            keyEvent("b"),
            keyEvent("b", type = "keyup"),
            keyEvent("c"),
            keyEvent("c", type = "keyup"),
        )

        assertEquals("abc", textInputChannel.receive(), "XXX")
    }

    fun repeatedAccentMenuClicked() = runTest {
        val textInputChannel = createTextFieldWithChannel()
        yield()

        textInputChannel.sendToHtmlInput(
            keyEvent("a"),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", repeat = true),
            keyEvent("a", type = "keyup"),
            beforeInput(inputType = "insertText", data = "æ"),
        )

        assertEquals("æ", textInputChannel.receive(), "Choose symbol from Accent Menu")
    }



    @Test
    fun keyboardEventPassedToTextField() = runTest {

        val textInputChannel = Channel<String>(
            1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        val (firstFocusRequester, secondFocusRequester) = FocusRequester.createRefs()

        createComposeWindow {
            TextField(
                value = "",
                onValueChange = { value ->
                    textInputChannel.sendFromScope(value)
                },
                modifier = Modifier.focusRequester(firstFocusRequester)
            )

            TextField(
                value = "",
                onValueChange = { value ->
                    textInputChannel.sendFromScope(value)
                },
                modifier = Modifier.focusRequester(secondFocusRequester)
            )

            SideEffect {
                firstFocusRequester.requestFocus()
            }
        }

        yield()

        val backingTextField = document.querySelector("textarea")
        assertIs<HTMLTextAreaElement>(backingTextField)

        dispatchEvents(
            backingTextField,
            keyEvent("s"),
            keyEvent("t"),
            keyEvent("e"),
            keyEvent("p"),
            keyEvent("1")
        )


        assertEquals("step1", textInputChannel.receive())

        secondFocusRequester.requestFocus()

        dispatchEvents(
            backingTextField,
            keyEvent("s"),
            keyEvent("t"),
            keyEvent("e"),
            keyEvent("p"),
            keyEvent("2")
        )

        assertEquals("step2", textInputChannel.receive())
    }

    @Test
    fun canSelectUsingMouse() = runTest {
        val syncChannel = Channel<TextRange?>(
            1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        var textFieldWidth = 0

        createComposeWindow {
            val textState = remember { TextFieldState("qwerty 1234567") }
            val focusRequester = remember { FocusRequester() }

            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                Column {
                    TextField(state = textState, modifier = Modifier.focusRequester(focusRequester).onGloballyPositioned {
                        textFieldWidth = it.size.width
                    })

                    LaunchedEffect(textState.selection) {
                        focusRequester.requestFocus()
                        syncChannel.send(textState.selection)
                    }
                }
            }

            SideEffect {
                focusRequester.requestFocus()
            }
        }

        yield()

        var selection = syncChannel.receive()
        assertEquals(TextRange(14, 14), selection)
        assertTrue(textFieldWidth > 0, "TextField width should be positive")

        val canvas = getCanvas()
        canvas.dispatchEvent(MouseEvent("mouseenter"))
        yield()
        canvas.dispatchEvent(MouseEvent("mousedown", MouseEventInit(clientX = 8, clientY = 20, buttons = 1, button = 1)))
        yield()
        canvas.dispatchEvent(MouseEvent("mouseup", MouseEventInit(clientX = 8, clientY = 20, buttons = 0, button = 1)))

        selection = syncChannel.receive()
        assertEquals(TextRange(0, 0), selection)

        val textArea = document.querySelector("textarea")
        assertIs<HTMLTextAreaElement>(textArea)

        val textAreaRect = textArea.getBoundingClientRect()
        // Do a manual hit-test
        val elementsAtPos = document.elementsFromPoint(
            textAreaRect.left + textAreaRect.width / 2 ,
            textAreaRect.top + textAreaRect.height / 2
        )

        // We expect the canvas to be on the top despite the coordinates match the textarea.
        // So it will be the first to process all the point inputs
        assertEquals(canvas, elementsAtPos[0], "First element under mouse supposed to be canvas")
        assertTrue(elementsAtPos.toList().any { it == textArea }) // such a weird check to make the test common for js and wasm
        withContext(Dispatchers.Default) {
            delay(250) // to separate the mouse events
        }


        // Try to select the text using mouse:
        val startX = textAreaRect.left.toInt() + 1
        val startY = textAreaRect.top.toInt() + 8
        val endX = startX + textFieldWidth
        canvas.dispatchEvent(MouseEvent("mousemove", MouseEventInit(clientX = startX, clientY = startY, buttons = 1, button = 1)))
        canvas.dispatchEvent(MouseEvent("mousedown", MouseEventInit(clientX = startX, clientY = startY, buttons = 1, button = 1)))
        canvas.dispatchEvent(MouseEvent("mousemove", MouseEventInit(clientX = endX, clientY = startY, buttons = 1, button = 1)))
        canvas.dispatchEvent(MouseEvent("mouseup", MouseEventInit(clientX = endX, clientY = startY, buttons = 0, button = 1)))

        selection = syncChannel.receive()
        assertEquals(TextRange(0, 14), selection)
    }
}


private fun compositionStart(data: String = "")  = CompositionEvent("compositionstart", CompositionEventInit(data = data))
private fun compositionEnd(data: String) = CompositionEvent("compositionend", CompositionEventInit(data = data))
private fun beforeInput(inputType: String, data: String?) = InputEvent("beforeinput", InputEventInit(inputType = inputType, data = data))

private fun mobileKeyDown() = keyEvent(type = "keydown", key = "Unidentified", code = "")
private fun mobileKeyUp() = keyEvent(type = "keydown", key = "Unidentified", code = "")