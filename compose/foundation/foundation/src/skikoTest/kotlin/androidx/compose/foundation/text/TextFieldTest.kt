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

package androidx.compose.foundation.text

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TextFieldTest {
    @Test
    fun selectionWithVisualTransformation() = runComposeUiTest {
        val rawText = "abcde"
        val transformedText = "bcde"

        val visualTransformation = object : VisualTransformation {
            val offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = (offset - 1).coerceAtLeast(0)
                override fun transformedToOriginal(offset: Int) = offset + 1
            }
            override fun filter(text: AnnotatedString) = TransformedText(
                text = AnnotatedString(transformedText),
                offsetMapping = offsetMapping,
            )
        }

        var selection by mutableStateOf(TextRange(0, 0))
        setContent {
            val focusRequester = remember { FocusRequester() }
            BasicTextField(
                value = TextFieldValue(rawText, selection = selection),
                onValueChange = {},
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .testTag("textField"),
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        onNodeWithTag("textField").assertTextEquals(transformedText)

        // Try all valid selections; just check there's no crash
        for (i in 0 .. rawText.length) {
            for (j in i .. rawText.length) {
                selection = TextRange(i, j)
                waitForIdle()
            }
        }
    }
}