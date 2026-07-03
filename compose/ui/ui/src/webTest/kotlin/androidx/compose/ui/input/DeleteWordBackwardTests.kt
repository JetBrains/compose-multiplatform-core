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
import kotlin.test.Test

class DeleteWordBackwardTests : TextFieldTestSpec, BasicTextFieldWithValue {
    
    private fun sendDeleteWordBackward(startOffset: Int, endOffset: Int) {
        sendToHtmlInput(
            keyEvent(
                key = "Backspace",
                code = "Backspace",
                type = "keydown",
                repeat = true,
            ),
            beforeInputWithTargetRange(
                inputType = "deleteWordBackward",
                data = null,
                startOffset = startOffset,
                endOffset = endOffset
            )
        )
    }

    @Test
    fun deletePrevWordMiddle() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder("here 🐩 we go again", initialSelection = TextRange(14, 14))

        awaitAnimationFrame()

        sendDeleteWordBackward(11, 14)
        textFieldValue.awaitAndAssertTextEquals("here 🐩 we again")

        sendDeleteWordBackward(8, 11)
        textFieldValue.awaitAndAssertTextEquals("here 🐩 again")
    }


    @Test
    fun deletePrevWordEmpty() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder(
            ""
        )

        sendDeleteWordBackward(0, 0)
        textFieldValue.awaitAndAssertTextEquals("")
    }

    @Test
    fun deletePrevWordCompoundEmoji() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder(
            "compound emoji: 🧑‍🧑‍🧒‍🧒"
        )

        sendDeleteWordBackward(16, 27)
        textFieldValue.awaitAndAssertTextEquals("compound emoji: ")
    }

    @Test
    fun deletePrevWordSplitFamilyEmoji() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder(
            "compound emoji: 🧑🧑👧👶"
        )

        sendDeleteWordBackward(16, 24)
        textFieldValue.awaitAndAssertTextEquals("compound emoji: ")
    }

    @Test
    fun deletePrevWordUnicode() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder(
            "천천히 말해 주세요"
        )

        awaitIdle()

        sendDeleteWordBackward(6, 10)
        textFieldValue.awaitAndAssertTextEquals("천천히 말해")

        sendDeleteWordBackward(3, 6)
        textFieldValue.awaitAndAssertTextEquals("천천히")
    }
}