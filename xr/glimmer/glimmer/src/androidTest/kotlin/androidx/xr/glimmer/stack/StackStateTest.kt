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

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.glimmer.Text
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StackStateTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun initialState_propertiesReturnDefaultValues() {
        val state = StackState()

        assertThat(state.topItem).isEqualTo(0)
        assertThat(state.topItemOffsetFraction).isEqualTo(0f)
        assertThat(state.isScrollInProgress).isFalse()
        assertThat(state.canScrollForward).isFalse()
        assertThat(state.canScrollBackward).isFalse()
        assertThat(state.lastScrolledForward).isFalse()
        assertThat(state.lastScrolledBackward).isFalse()
    }

    @Test
    fun negativeInitialTopItem_throws() {
        assertThrows(IllegalArgumentException::class.java) { StackState(initialTopItem = -1) }
    }

    @Test
    fun dispatchRawDelta_whenNotAttachedToStack_returnsZeroConsumedDelta() {
        val state = StackState()

        val consumed = state.dispatchRawDelta(100f)

        assertThat(consumed).isEqualTo(0f)
    }

    @Test
    fun scrollToItem_whenNotAttachedToStack_completesWithoutError() = runTest {
        val state = StackState()

        state.scrollToItem(5)
        // Test succeeds if no exception is thrown.
    }

    @Test
    fun animateScrollToItem_whenNotAttachedToStack_completesWithoutError() = runTest {
        val state = StackState()

        state.animateScrollToItem(5)
        // Test succeeds if no exception is thrown.
    }

    @Test
    fun scroll_whenNotAttachedToStack_completesWithoutErrorAndDoesNotExecuteBlock() {
        val state = StackState()

        var blockExecuted = false
        runBlocking {
            state.scroll(MutatePriority.Default) {
                blockExecuted = true
                scrollBy(100f)
            }
        }

        assertThat(blockExecuted).isFalse()
    }

    @Test
    fun scrollToItem_displaysTargetItem() = runTest {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        state.scrollToItem(3)

        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
    }

    @Test
    fun animateScrollToItem_displaysTargetItem() {
        lateinit var scope: CoroutineScope
        val state = StackState()
        rule.setContent {
            scope = rememberCoroutineScope()
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        scope.launch { state.animateScrollToItem(3) }

        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
    }

    @Test
    fun scroll_forwardAlmostItemHeight_displaysNextItem() = runTest {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        state.scroll(MutatePriority.Default) { with(rule.density) { scrollBy(90.dp.toPx()) } }

        rule.onNodeWithText("Item 1").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(1)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(-0.1f)
    }

    @Test
    fun scroll_backwardAlmostItemHeight_displaysPreviousItem() = runTest {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        state.scroll(MutatePriority.Default) {
            with(rule.density) {
                scrollBy(100.dp.toPx()) // Scroll to "Item 1"
                scrollBy(-90.dp.toPx()) // Scroll to "Item 0"
            }
        }

        rule.onNodeWithText("Item 0").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(0)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(0.1f)
    }

    @Test
    fun dispatchRawDelta_forwardWithinSameItem_updatesOffsetFraction() {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        rule.runOnIdle {
            with(rule.density) {
                state.dispatchRawDelta(10.dp.toPx()) // 1/10th of the item height
            }
        }

        rule.onNodeWithText("Item 0").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(0)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(0.1f)
    }

    @Test
    fun dispatchRawDelta_backwardWithinSameItem_updatesOffsetFraction() {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack"), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }

        rule.onNodeWithTag("stack").performScrollToNode(hasText("Item 3"))
        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
        assertThat(state.topItemOffsetFraction).isEqualTo(0f)

        rule.runOnIdle {
            with(rule.density) {
                state.dispatchRawDelta(-10.dp.toPx()) // 1/10th of the item height
            }
        }

        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(-0.1f)
    }

    @Test
    fun isScrollInProgress_isTrueDuringSwipe() {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack"), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag("stack").performTouchInput { swipeUp() }

        assertThat(state.isScrollInProgress).isTrue()

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertThat(state.isScrollInProgress).isFalse()
        assertThat(state.topItem).isEqualTo(1)
    }

    @Test
    fun canScrollForwardAndBackward_areCorrectAtAndBetweenBoundaries() = runTest {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }
        rule.waitForIdle()

        // At the beginning
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isFalse()

        state.scrollToItem(2)

        // In the middle
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isTrue()

        state.scrollToItem(4)

        // At the end
        assertThat(state.canScrollForward).isFalse()
        assertThat(state.canScrollBackward).isTrue()
    }

    @Test
    fun lastScrolledForwardAndBackward_areUpdatedAfterSwipe() {
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp).testTag("stack"), state = state) {
                items(5) { index -> Box(modifier = Modifier.fillMaxSize()) { Text("Item $index") } }
            }
        }
        rule.waitForIdle()

        assertThat(state.lastScrolledBackward).isFalse()
        assertThat(state.lastScrolledForward).isFalse()

        rule.onNodeWithTag("stack").performTouchInput { swipeUp() }
        rule.waitForIdle()

        assertThat(state.lastScrolledBackward).isFalse()
        assertThat(state.lastScrolledForward).isTrue()

        rule.onNodeWithTag("stack").performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertThat(state.lastScrolledBackward).isTrue()
        assertThat(state.lastScrolledForward).isFalse()
    }

    @Test
    fun saveAndRestoreState_restoresTopItem() {
        val allowingScope = SaverScope { true }
        val original = StackState(initialTopItem = 5)

        val saved = with(StackState.Saver) { allowingScope.save(original) }!!
        val restored = StackState.Saver.restore(saved)!!

        assertThat(restored.topItem).isEqualTo(5)
    }
}
