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

package androidx.compose.ui.test

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import kotlin.test.Test

/**
 * Tests the text-actions functionality of the test framework.
 */
@OptIn(ExperimentalTestApi::class)
class TextActionsTest {

    @Composable
    fun TestTextField(text: String) {
        TextField(
            state = rememberTextFieldState(text),
            modifier = Modifier.testTag("tag")
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun testPerformTextClearance() = runComposeUiTest {
        setContent {
            TestTextField("hello")
        }

        onNodeWithTag("tag").apply {
            assertTextEquals("hello")
            performTextClearance()
            assertTextEquals("")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testPerformTextReplacement() = runComposeUiTest {
        setContent {
            TestTextField("hello")
        }

        onNodeWithTag("tag").apply {
            assertTextEquals("hello")
            performTextReplacement("compose")
            assertTextEquals("compose")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testPerformTextInput() = runComposeUiTest {
        setContent {
            TestTextField("hello")
        }

        onNodeWithTag("tag").apply {
            assertTextEquals("hello")
            performTextInput(" compose")
            assertTextEquals("hello compose")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testPerformTextInputSelection() = runComposeUiTest  {
        setContent {
            TestTextField("hello")
        }

        onNodeWithTag("tag").apply {
            assertTextEquals("hello")
            performTextInputSelection(TextRange(5))
            performTextInput(" compose")
            assertTextEquals("hello compose")
        }
    }
}