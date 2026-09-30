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

import androidx.compose.ui.input.specs.TextFieldTestSpec
import androidx.compose.ui.text.TextRange
import kotlin.test.Ignore
import kotlin.test.Test
import org.w3c.dom.clipboard.ClipboardEvent

/**
 * Tests for https://youtrack.jetbrains.com/issue/CMP-10593 - pasting should add the required
 * spaces and newlines around the pasted text ("smart paste"):
 * - pasting after an existing text on the same line separates it with a whitespace, unless the
 *   pasted text itself starts with a whitespace or a newline;
 * - pasting on a new line adds an additional newline before the pasted text (and a whitespace
 *   after it when the line already has content);
 * - replacing a part of a word inserts the surrounding whitespaces as well.
 *
 * The issue is reported for iOS, but paste is handled by common text code, so exactly the same
 * behaviour is observable on Web. The `@Ignore`d tests below encode the expected behaviour, while
 * the `currently*` ones document the current behaviour and are expected to fail (and to be
 * removed) once CMP-10593 is fixed.
 */
internal interface SmartPasteWhitespaceTestSpec : TextFieldTestSpec {

    fun sendPaste(text: String) {
        sendToHtmlInput(
            clipboardEvent(type = "paste").also {
                it.clipboardData!!.setData("text/plain", text)
            }
        )
    }

    @Ignore // CMP-10593: no whitespace is inserted before the pasted text
    @Test
    fun pasteAfterTextAddsWhitespace() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(5))

        sendPaste("world")

        textFieldValue.awaitAndAssertTextEquals(
            "Hello world",
            "pasting after an existing text should be separated with a whitespace"
        )
    }

    // CMP-10593: pasting a text that already starts with a whitespace must not add another one
    @Test
    fun pasteOfTextStartingWithWhitespaceKeepsSingleWhitespace() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(5))

        sendPaste(" world")

        textFieldValue.awaitAndAssertTextEquals(
            "Hello world",
            "no extra whitespace should be added when the pasted text starts with one"
        )
    }

    // CMP-10593: pasting a text that starts with a newline must not add a whitespace
    @Test
    fun pasteOfTextStartingWithNewLineKeepsNewLine() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(5))

        sendPaste("\nworld")

        textFieldValue.awaitAndAssertTextEquals(
            "Hello\nworld",
            "no extra whitespace should be added when the pasted text starts with a newline"
        )
    }

    @Ignore // CMP-10593: no additional newline is inserted before the pasted text
    @Test
    fun pasteOnNewLineAddsNewLine() = runApplicationTest {
        // the caret is placed on the (empty) second line
        val textFieldValue = createApplicationWithHolder("first\n", TextRange(6))

        sendPaste("last")

        textFieldValue.awaitAndAssertTextEquals(
            "first\n\nlast",
            "pasting on a new line should be preceded by an additional newline"
        )
    }

    @Ignore // CMP-10593: neither the newline before nor the whitespace after are inserted
    @Test
    fun pasteOnNewLineWithFollowingTextAddsNewLineAndWhitespace() = runApplicationTest {
        // the caret is at the beginning of the second line, which already has content
        val textFieldValue = createApplicationWithHolder("first\ntail", TextRange(6))

        sendPaste("mid")

        textFieldValue.awaitAndAssertTextEquals(
            "first\n\nmid tail",
            "a newline should be added before and a whitespace after the pasted text"
        )
    }

    @Ignore // CMP-10593: the surrounding whitespaces are not inserted
    @Test
    fun pasteReplacingPartOfWordAddsWhitespaces() = runApplicationTest {
        // "ell" of "Hello" is selected and replaced by the pasted text
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(1, 4))

        sendPaste("XYZ")

        textFieldValue.awaitAndAssertTextEquals(
            "H XYZ o",
            "replacing a part of a word should insert the surrounding whitespaces"
        )
    }

    /**
     * Documents the current (incorrect) behaviour described in CMP-10593: the pasted text is glued
     * to the preceding text.
     *
     * This test is expected to fail as soon as CMP-10593 is fixed - at that point it should be
     * removed and the assertions above should be un-ignored.
     */
    @Test
    fun currentlyPastesWithoutWhitespace() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(5))

        sendPaste("world")

        textFieldValue.awaitAndAssertTextEquals(
            "Helloworld",
            "CMP-10593: the pasted text is not separated with a whitespace"
        )
    }

    /**
     * Documents the current (incorrect) behaviour of the newline case described in CMP-10593.
     *
     * See [currentlyPastesWithoutWhitespace] for the removal policy of this test.
     */
    @Test
    fun currentlyPastesOnNewLineWithoutExtraNewLine() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("first\n", TextRange(6))

        sendPaste("last")

        textFieldValue.awaitAndAssertTextEquals(
            "first\nlast",
            "CMP-10593: no additional newline is added before the pasted text"
        )
    }

    /**
     * Documents the current (incorrect) behaviour of the in-word replacement described in
     * CMP-10593.
     *
     * See [currentlyPastesWithoutWhitespace] for the removal policy of this test.
     */
    @Test
    fun currentlyReplacesPartOfWordWithoutWhitespaces() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("Hello", TextRange(1, 4))

        sendPaste("XYZ")

        textFieldValue.awaitAndAssertTextEquals(
            "HXYZo",
            "CMP-10593: no surrounding whitespaces are inserted"
        )
    }
}

// The default API doesn't work correctly on FF :(, so we do it manually
private fun clipboardEvent(type: String): ClipboardEvent = js("""
        new ClipboardEvent(type, { 'clipboardData': new DataTransfer() })
    """)

internal class SmartPasteWhitespaceWithValueTests : SmartPasteWhitespaceTestSpec,
    BasicTextFieldWithValue

internal class SmartPasteWhitespaceWithStateTests : SmartPasteWhitespaceTestSpec,
    BasicTextFieldWithState
