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

package androidx.xr.glimmer.stack

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.glimmer.Text
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerticalStackTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun zeroItems_displaysNothing() {
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.testTag("stack")) {
                // No items
            }
        }

        assertThat(composeTestRule.onNodeWithTag("stack").getBoundsInRoot().size)
            .isEqualTo(DpSize.Zero)
    }

    @Test
    fun singleItem_displaysItem() {
        composeTestRule.setContent { VerticalStack { item { Text("Single Item") } } }

        composeTestRule.onNodeWithText("Single Item").assertIsDisplayed()
    }

    @Test
    fun multipleItems_displaysFirstItem() {
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp)) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        composeTestRule.onNodeWithText("Item 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 1").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Item 5").assertDoesNotExist()
    }

    @Test
    fun swipeUp_displaysOnlyNextItem() {
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack")) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }
        composeTestRule.onNodeWithText("Item 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 1").assertIsNotDisplayed()

        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }

        composeTestRule.onNodeWithText("Item 0").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Item 1").assertIsDisplayed()
    }

    @Test
    fun scrollToEndAndBack_displaysItemsInCorrectOrder() {
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack")) {
                items(3) { index -> Text("Item $index") }
            }
        }

        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed() // Reached the end

        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("Item 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("Item 0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("Item 0").assertIsDisplayed() // Reached the beginning
    }

    @Test
    fun mixedDsl_displaysItemsInCorrectOrder() {
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack")) {
                item { Text("First") }
                items(3) { Text("Middle $it") }
                items(listOf(3, 4, 5)) { Text("Middle $it") }
                item { Text("Last") }
            }
        }
        composeTestRule.onNodeWithText("First").assertIsDisplayed()
        composeTestRule.onNodeWithText("Middle 0").assertIsNotDisplayed()

        repeat(6) { index ->
            composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }

            composeTestRule.onNodeWithText("Middle $index").assertIsDisplayed()
            composeTestRule.onNodeWithText("Middle ${index + 1}").assertIsNotDisplayed()
        }

        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Last").assertIsDisplayed()
    }

    @Test
    fun contentStateChange_updatesItems() {
        var itemCount by mutableStateOf(3)
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack")) {
                items(itemCount) { index -> Text("Item $index") }
            }
        }
        composeTestRule.onNodeWithTag("stack").performScrollToNode(hasText("Item 2"))
        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed() // Reached the end

        composeTestRule.runOnIdle { itemCount++ }

        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Item 3").assertIsDisplayed() // Reached the end
    }

    @Test
    fun reorderItems_withKeys_preservesScrollPosition() {
        var items by mutableStateOf(listOf("A", "B", "C", "D"))
        composeTestRule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack")) {
                item(key = "First", content = { Text("First") })
                items(items, key = { it }) { Text(it) }
                items(1, key = { "Last" }) { Text("Last") }
            }
        }
        composeTestRule.onNodeWithTag("stack").performScrollToNode(hasText("B"))
        composeTestRule.onNodeWithText("B").assertIsDisplayed()

        composeTestRule.runOnIdle { items = listOf("A", "C", "B", "D") }

        composeTestRule.onNodeWithText("B").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("D").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithText("Last").assertIsDisplayed()

        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("D").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("B").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("C").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        composeTestRule.onNodeWithText("First").assertIsDisplayed()
    }
}
