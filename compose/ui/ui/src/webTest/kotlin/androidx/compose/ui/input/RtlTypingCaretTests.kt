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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for https://youtrack.jetbrains.com/issue/CMP-10601 - while typing with an RTL keyboard the
 * caret and the just typed whitespace/punctuation symbols jump to the right (i.e. to the wrong
 * side) of the sentence, and get placed correctly only after the next symbol is typed.
 *
 * The issue is reported for iOS. The text model and the logical caret offsets are computed by
 * common text code, so these tests assert that on Web the typed whitespace and punctuation are
 * appended in the logical order and the caret keeps following the last typed symbol. The visual
 * (bidi) placement itself is not observable from a test, so a failure here would mean the text
 * model is broken as well, while a pass documents that CMP-10601 is limited to the iOS text input
 * integration.
 */
internal interface RtlTypingCaretTestSpec : TextFieldTestSpec {

    @Test
    fun typingWhitespaceKeepsLogicalOrderAndCaret() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("")

        sendStandardKeyboardSequence("مرحبا")
        textFieldValue.awaitAndAssertTextEquals("مرحبا")
        assertEquals(TextRange(5), textFieldValue.selection)

        // step 4 of the issue: type a whitespace, then one more symbol
        sendStandardKeyboardSequence(" ")
        textFieldValue.awaitAndAssertTextEquals(
            "مرحبا ",
            "the typed whitespace should be appended after the RTL text"
        )
        assertEquals(
            TextRange(6),
            textFieldValue.selection,
            "CMP-10601: the caret should stay after the typed whitespace"
        )

        sendStandardKeyboardSequence("ب")
        textFieldValue.awaitAndAssertTextEquals("مرحبا ب")
        assertEquals(TextRange(7), textFieldValue.selection)
    }

    @Test
    fun typingPunctuationKeepsLogicalOrderAndCaret() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("")

        sendStandardKeyboardSequence("مرحبا")
        awaitIdle()

        // step 5 of the issue: the period (produced by a double whitespace on iOS) is typed
        // explicitly here, since the double-space shortcut doesn't exist on Web
        sendStandardKeyboardSequence(".")
        textFieldValue.awaitAndAssertTextEquals(
            "مرحبا.",
            "the typed period should be appended after the RTL text"
        )
        assertEquals(
            TextRange(6),
            textFieldValue.selection,
            "CMP-10601: the caret should stay after the typed period"
        )

        sendStandardKeyboardSequence(" ب")
        textFieldValue.awaitAndAssertTextEquals("مرحبا. ب")
        assertEquals(TextRange(8), textFieldValue.selection)
    }

    @Test
    fun typingOnAnEmptyLineOfMultilineTextKeepsLogicalOrder() = runApplicationTest {
        // step 2 of the issue: the caret is placed on a line that doesn't contain any text
        val textFieldValue = createApplicationWithHolder("مرحبا\n", TextRange(6))

        sendStandardKeyboardSequence("ب ت")

        textFieldValue.awaitAndAssertTextEquals(
            "مرحبا\nب ت",
            "typing on an empty line should not reorder the previously typed lines"
        )
        assertEquals(TextRange(9), textFieldValue.selection)
    }
}

internal class RtlTypingCaretWithValueTests : RtlTypingCaretTestSpec, BasicTextFieldWithValue

internal class RtlTypingCaretWithStateTests : RtlTypingCaretTestSpec, BasicTextFieldWithState
