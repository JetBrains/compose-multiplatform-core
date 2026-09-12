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

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test


/**
 * Tests the node-finding (e.g. [onNodeWithTag]) functionality of the testing framework.
 */
@OptIn(ExperimentalTestApi::class)
class MatchersTest {

    @Suppress("DEPRECATION")
    @Test
    fun testNodeWithTag() = runComposeUiTest {
        setContent {
            Box(
                Modifier.testTag("tag")
            ) {
                Button(onClick = {}) {
                    Text("Hello", Modifier.testTag("text1"))
                    Text("Compose", Modifier.testTag("text2"))
                }
            }
        }

        onNodeWithTag("tag").assertExists()
        onNodeWithTag("mark").assertDoesNotExist()
        onNodeWithTag("text1", useUnmergedTree = false).assertDoesNotExist()
        onNodeWithTag("text1", useUnmergedTree = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun testAllNodesWithTag() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("tag"))
            Text("text", Modifier.testTag("tag"))
        }

        onAllNodesWithTag("tag").assertCountEquals(2)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testNodeWithText() = runComposeUiTest {
        setContent {
            Text("text")
            Button(onClick = {}) {
                Text("Hello", Modifier.testTag("text1"))
                Text("Compose", Modifier.testTag("text2"))
            }
        }

        onNodeWithText("text").assertExists()
        onNodeWithText("Text").assertDoesNotExist()
        onNodeWithText("Text", ignoreCase = true).assertExists()
        onNodeWithText("Hello").assertHasClickAction()  // Button
        onNodeWithText("Hello", useUnmergedTree = true).assertHasNoClickAction() // Text
        onNodeWithText("tex").assertDoesNotExist()
        onNodeWithText("tex", substring = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun testAllNodesWithText() = runComposeUiTest {
        setContent {
            Text("text")
            Text("text")
            Text("long text")
            Text("Text")
        }

        onAllNodesWithText("text").assertCountEquals(2)
        onAllNodesWithText("text", substring = true).assertCountEquals(3)
        onAllNodesWithText("text", ignoreCase = true).assertCountEquals(3)
        onAllNodesWithText("text", ignoreCase = true, substring = true).assertCountEquals(4)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testNodeWithContentDescription() = runComposeUiTest {
        setContent {
            Box(Modifier.semantics { contentDescription = "desc" })
            Button(onClick = {}) {
                Box(Modifier.semantics { contentDescription = "Hello" })
                Box(Modifier.semantics { contentDescription = "Compose" })
            }
        }

        onNodeWithContentDescription("desc").assertExists()
        onNodeWithContentDescription("Desc").assertDoesNotExist()
        onNodeWithContentDescription("Desc", ignoreCase = true).assertExists()
        onNodeWithContentDescription("Hello").assertHasClickAction()  // Button
        onNodeWithContentDescription("Hello", useUnmergedTree = true)  // Text
            .assertHasNoClickAction()
        onNodeWithContentDescription("des").assertDoesNotExist()
        onNodeWithContentDescription("desc", substring = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun testAllNodesWithContentDescription() = runComposeUiTest {
        setContent {
            Box(Modifier.semantics { contentDescription = "desc" })
            Box(Modifier.semantics { contentDescription = "desc" })
            Box(Modifier.semantics { contentDescription = "long desc" })
            Box(Modifier.semantics { contentDescription = "Desc" })
        }

        onAllNodesWithContentDescription("desc").assertCountEquals(2)
        onAllNodesWithContentDescription("desc", substring = true).assertCountEquals(3)
        onAllNodesWithContentDescription("desc", ignoreCase = true).assertCountEquals(3)
        onAllNodesWithContentDescription("desc", substring = true, ignoreCase = true)
            .assertCountEquals(4)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testOnNode() = runComposeUiTest {
        setContent {
            Text("Hello", Modifier.semantics { contentDescription = "text" })
            Text("Compose")
        }

        fun expectText(text: String) = SemanticsMatcher.expectValue(
            key = SemanticsProperties.Text,
            expectedValue = listOf(AnnotatedString(text))
        )

        fun expectContentDescription(desc: String) = SemanticsMatcher.expectValue(
            key = SemanticsProperties.ContentDescription,
            expectedValue = listOf(desc)
        )

        onNode(
            matcher = expectText("Hello").and(expectContentDescription("text"))
        ).assertExists()
        onNode(
            matcher = expectText("Compose").and(expectContentDescription("text"))
        ).assertDoesNotExist()
    }
}
