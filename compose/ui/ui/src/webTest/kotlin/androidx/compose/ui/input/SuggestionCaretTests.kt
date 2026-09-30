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
import androidx.compose.ui.input.specs.TextFieldTestSpec
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for https://youtrack.jetbrains.com/issue/CMP-9981 - the caret jumps to a new line (and
 * gets a wrong position) when a keyboard suggestion is applied to a word placed at the end of a
 * line of a multi-line text.
 *
 * The issue is reported for iOS, but committing a suggestion is expressed on Web as a
 * `beforeinput` event with the `insertReplacementText` input type over the range of the replaced
 * word, and the resulting selection is computed by common text code. These tests assert that the
 * caret stays right after the replaced word.
 */
internal interface SuggestionCaretTestSpec : TextFieldTestSpec {

    fun sendSuggestion(replacement: String, startOffset: Int, endOffset: Int) {
        sendToHtmlInput(
            beforeInputWithTargetRange(
                inputType = "insertReplacementText",
                data = replacement,
                startOffset = startOffset,
                endOffset = endOffset
            )
        )
    }

    @Test
    fun suggestionAtEndOfLineKeepsCaretOnTheSameLine() = runApplicationTest {
        // the caret is at the end of the first line, right after the word being replaced
        val textFieldValue = createApplicationWithHolder("hello wrld\nsecond line", TextRange(10))

        sendSuggestion(replacement = "world", startOffset = 6, endOffset = 10)

        textFieldValue.awaitAndAssertTextEquals(
            "hello world\nsecond line",
            "the suggestion should replace the word preceding the caret"
        )
        assertEquals(
            TextRange(11),
            textFieldValue.selection,
            "CMP-9981: the caret should stay right after the replaced word"
        )
    }

    @Test
    fun suggestionInTheMiddleOfLineKeepsCaretAfterTheWord() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("hello wrld again", TextRange(10))

        sendSuggestion(replacement = "world", startOffset = 6, endOffset = 10)

        textFieldValue.awaitAndAssertTextEquals("hello world again")
        assertEquals(TextRange(11), textFieldValue.selection)
    }

    @Test
    fun suggestionOnLastLineKeepsCaretAfterTheWord() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("first\nhello wrld", TextRange(16))

        sendSuggestion(replacement = "world", startOffset = 12, endOffset = 16)

        textFieldValue.awaitAndAssertTextEquals("first\nhello world")
        assertEquals(TextRange(17), textFieldValue.selection)
    }
}

internal class SuggestionCaretWithValueTests : SuggestionCaretTestSpec, BasicTextFieldWithValue

internal class SuggestionCaretWithStateTests : SuggestionCaretTestSpec, BasicTextFieldWithState
