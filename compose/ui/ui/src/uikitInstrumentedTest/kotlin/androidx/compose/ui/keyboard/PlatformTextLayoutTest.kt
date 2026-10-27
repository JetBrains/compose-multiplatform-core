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

package androidx.compose.ui.keyboard

import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextLayout
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findAllUITextInputViews
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UITextInputProtocol

/**
 * The input method reads the geometry of the text through
 * [PlatformTextInputMethodRequest.textLayout], so a text field can describe a layout that is not
 * a `TextLayoutResult`. Here the layout of a regular text field is replaced by one whose caret is
 * moved down, the way a text field with space between its paragraphs would report it.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
internal class PlatformTextLayoutTest {

    @Test
    fun testCaretRectComesFromTheTextLayoutOfTheRequest() {
        val shiftPx = 60f
        var plainTop = 0.0
        var shiftedTop = 0.0
        var density = 1f
        runUIKitInstrumentedTest {
            density = this.density.density
            plainTop = caretTop(position = 3, shiftPx = null)
        }
        runUIKitInstrumentedTest { shiftedTop = caretTop(position = 3, shiftPx = shiftPx) }

        assertEquals(plainTop + shiftPx / density, shiftedTop, absoluteTolerance = 0.5)
    }

    @Test
    fun testTextLayoutDefaultsToTheTextLayoutResult() = runUIKitInstrumentedTest {
        var request: PlatformTextInputMethodRequest? = null
        setFocusedTextField { original -> original.also { request = it } }

        val captured = assertNotNull(request)
        val result = assertNotNull(captured.textLayoutResult())
        val layout = assertNotNull(captured.textLayout())
        assertEquals(result.size, layout.size)
        assertEquals(result.layoutInput.text.length, layout.textLength)
        assertEquals(result.lineCount, layout.lineCount)
        assertEquals(result.getCursorRect(3), layout.getCursorRect(3))
        assertEquals(result.getLineRight(0), layout.getLineRight(0))
    }

    private fun UIKitInstrumentedTest.caretTop(position: Long, shiftPx: Float?): Double {
        setFocusedTextField { original ->
            if (shiftPx == null) original else original.withCaretMovedDown(shiftPx)
        }
        val input = assertNotNull(findAllUITextInputViews().firstOrNull() as? UITextInputProtocol)
        val textPosition = assertNotNull(input.positionFromPosition(input.beginningOfDocument, position))
        return input.caretRectForPosition(textPosition).useContents { origin.y }
    }

    private fun UIKitInstrumentedTest.setFocusedTextField(
        transform: (PlatformTextInputMethodRequest) -> PlatformTextInputMethodRequest
    ) {
        val focusRequester = FocusRequester()
        setContent {
            InterceptPlatformTextInput(
                interceptor = { request, nextHandler -> nextHandler.startInputMethod(transform(request)) },
                content = {
                    TextField(
                        value = "Hello",
                        onValueChange = {},
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                },
            )
        }
        waitForIdle()
        assertNotNull(findAllUITextInputViews().firstOrNull())
    }

    private fun PlatformTextInputMethodRequest.withCaretMovedDown(
        shiftPx: Float
    ): PlatformTextInputMethodRequest {
        val original = this
        return object : PlatformTextInputMethodRequest by original {
            override val textLayout: () -> PlatformTextLayout? = {
                original.textLayout()?.let { layout ->
                    object : PlatformTextLayout by layout {
                        override fun getCursorRect(offset: Int): Rect =
                            layout.getCursorRect(offset).translate(0f, shiftPx)
                    }
                }
            }
        }
    }
}
