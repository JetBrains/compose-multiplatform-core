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

package androidx.compose.ui.input

import androidx.compose.ui.events.beforeInputWithTargetRange
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.input.specs.TextFieldTestSpec
import androidx.compose.ui.text.TextRange
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.clipboard.ClipboardEvent

/**
 * Tests for https://youtrack.jetbrains.com/issue/CMP-10592 - backspace/cut of a selection should
 * also remove the preceding whitespace, and should remove the preceding newline when the previous
 * line is empty ("smart delete").
 *
 * The issue is reported for iOS, but the deletion of a selected range is handled by common text
 * code, so the same behaviour is observable on Web. The `@Ignore`d tests below encode the expected
 * behaviour, while [SmartDeleteWhitespaceTestSpec.currentlyKeepsPrecedingWhitespace] documents the
 * current one.
 */
internal interface SmartDeleteWhitespaceTestSpec : TextFieldTestSpec {

    fun sendBackspace(selection: TextRange) {
        sendToHtmlInput(
            keyEvent(key = "Backspace", code = "Backspace", type = "keydown"),
            beforeInputWithTargetRange(
                inputType = "deleteContentBackward",
                data = null,
                startOffset = selection.min,
                endOffset = selection.max
            ),
            keyEvent(key = "Backspace", code = "Backspace", type = "keyup")
        )
    }

    @Ignore // CMP-10592: the preceding whitespace is not removed
    @Test
    fun backspaceRemovesPrecedingWhitespace() = runApplicationTest {
        val selection = TextRange(6, 11)
        val textFieldValue = createApplicationWithHolder("Hello world again", selection)

        sendBackspace(selection)

        textFieldValue.awaitAndAssertTextEquals(
            "Hello again",
            "the whitespace preceding the deleted selection should be removed as well"
        )
        assertEquals(TextRange(5), textFieldValue.selection)
    }

    @Ignore // CMP-10592: the preceding whitespace is not removed
    @Test
    fun cutRemovesPrecedingWhitespace() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello world again", TextRange(6, 11))
        awaitIdle()

        val cutEvent = clipboardEvent(type = "cut")
        sendToHtmlInput(cutEvent)
        awaitIdle()

        assertEquals("world", cutEvent.clipboardData!!.getData("text/plain"))
        textFieldValue.awaitAndAssertTextEquals(
            "Hello again",
            "the whitespace preceding the cut selection should be removed as well"
        )
    }

    @Ignore // CMP-10592: the preceding newline is not removed and the caret does not jump up
    @Test
    fun backspaceRemovesPrecedingNewLineWhenPreviousLineIsEmpty() = runApplicationTest {
        // "first" / "" / "last" - the line being emptied is preceded by an empty one
        val selection = TextRange(7, 11)
        val textFieldValue = createApplicationWithHolder("first\n\nlast", selection)

        sendBackspace(selection)

        textFieldValue.awaitAndAssertTextEquals(
            "first\n",
            "the newline preceding the emptied line should be removed as well"
        )
        assertEquals(
            TextRange(6),
            textFieldValue.selection,
            "the caret should jump to the previous (empty) line"
        )
    }

    // CMP-10592: this part already behaves as expected - a non-empty previous line is kept intact
    @Test
    fun backspaceKeepsPrecedingNewLineWhenPreviousLineIsNotEmpty() = runApplicationTest {
        val selection = TextRange(6, 10)
        val textFieldValue = createApplicationWithHolder("first\nlast", selection)

        sendBackspace(selection)

        textFieldValue.awaitAndAssertTextEquals(
            "first\n",
            "the newline must be kept when the previous line has content"
        )
        assertEquals(TextRange(6), textFieldValue.selection)
    }

    /**
     * Documents the current (incorrect) behaviour described in CMP-10592: only the selected range
     * is deleted, so a double whitespace is left behind.
     *
     * This test is expected to fail as soon as CMP-10592 is fixed - at that point it should be
     * removed and the assertions above should be un-ignored.
     */
    @Test
    fun currentlyKeepsPrecedingWhitespace() = runApplicationTest {
        val selection = TextRange(6, 11)
        val textFieldValue = createApplicationWithHolder("Hello world again", selection)

        sendBackspace(selection)

        textFieldValue.awaitAndAssertTextEquals(
            "Hello  again",
            "CMP-10592: the preceding whitespace is kept, producing a double whitespace"
        )
    }

    /**
     * Documents the current (incorrect) behaviour of the newline case described in CMP-10592.
     *
     * See [currentlyKeepsPrecedingWhitespace] for the removal policy of this test.
     */
    @Test
    fun currentlyKeepsPrecedingNewLine() = runApplicationTest {
        val selection = TextRange(7, 11)
        val textFieldValue = createApplicationWithHolder("first\n\nlast", selection)

        sendBackspace(selection)

        textFieldValue.awaitAndAssertTextEquals(
            "first\n\n",
            "CMP-10592: the newline preceding the emptied line is kept"
        )
        assertEquals(
            TextRange(7),
            textFieldValue.selection,
            "CMP-10592: the caret does not jump to the previous line"
        )
    }
}

// The default API doesn't work correctly on FF :(, so we do it manually
private fun clipboardEvent(type: String): ClipboardEvent = js("""
        new ClipboardEvent(type, { 'clipboardData': new DataTransfer() })
    """)

internal class SmartDeleteWhitespaceWithValueTests : SmartDeleteWhitespaceTestSpec,
    BasicTextFieldWithValue

internal class SmartDeleteWhitespaceWithStateTests : SmartDeleteWhitespaceTestSpec,
    BasicTextFieldWithState
