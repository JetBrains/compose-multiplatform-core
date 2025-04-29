package androidx.compose.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
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
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

class MouseTextInputTests: OnCanvasTests {
    @Test
    fun canSelectUsingMouse() = runTest {
        val syncChannel = Channel<TextRange?>(
            1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        val focusRequester = FocusRequester()

        var textFieldWidth = 0

        createComposeWindow {
            val textState = remember { TextFieldState("qwerty 1234567") }

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