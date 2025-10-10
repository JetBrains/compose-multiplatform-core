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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.performIndirectSwipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StackStateTest {

    @get:Rule val rule = createComposeRule(StandardTestDispatcher())

    private val focusRequester = FocusRequester()

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
        assertThat(state.layoutInfoInternal.viewportSize).isEqualTo(IntSize.Zero)
        assertThat(state.layoutInfoInternal.maxItemSize).isEqualTo(IntSize.Zero)
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
            VerticalStack(state = state) { items(5) { index -> StackItem("Item $index") } }
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
            VerticalStack(state = state) { items(5) { index -> StackItem("Item $index") } }
        }

        scope.launch { state.animateScrollToItem(3) }

        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
    }

    @Test
    fun scroll_forwardAlmostItemHeight_displaysNextItem() = runTest {
        var itemHeight = 0
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }

        state.scroll(MutatePriority.Default) {
            scrollBy(itemHeight * 0.9f) // Scroll to "Item 1" (9/10th of the item height)
        }

        rule.onNodeWithText("Item 1").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(1)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(-0.1f)
    }

    @Test
    fun scroll_backwardAlmostItemHeight_displaysPreviousItem() = runTest {
        var itemHeight = 0
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }

        state.scroll(MutatePriority.Default) {
            scrollBy(itemHeight.toFloat()) // Scroll to "Item 1"
            scrollBy(-(itemHeight * 0.9f)) // Scroll to "Item 0" (9/10th of the item height)
        }

        rule.onNodeWithText("Item 0").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(0)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(0.1f)
    }

    @Test
    fun dispatchRawDelta_forwardWithinSameItem_updatesOffsetFraction() {
        var itemHeight = 0
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }

        rule.runOnIdle { state.dispatchRawDelta(itemHeight * 0.1f) }

        rule.onNodeWithText("Item 0").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(0)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(0.1f)
    }

    @Test
    fun dispatchRawDelta_backwardWithinSameItem_updatesOffsetFraction() = runTest {
        var itemHeight = 0
        val state = StackState()
        rule.setContent {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }
        state.scrollToItem(3)
        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
        assertThat(state.topItemOffsetFraction).isEqualTo(0f)

        rule.runOnIdle { state.dispatchRawDelta(-(itemHeight * 0.1f)) }

        rule.onNodeWithText("Item 3").assertIsDisplayed()
        assertThat(state.topItem).isEqualTo(3)
        assertThat(state.topItemOffsetFraction).isWithin(0.01f).of(-0.1f)
    }

    @Test
    fun isScrollInProgress_isTrueDuringSwipe() {
        var itemHeight = 0
        val state = StackState()
        rule.setContentWithInitialFocus {
            VerticalStack(state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }
        rule.mainClock.autoAdvance = false

        performIndirectSwipe(itemHeight)

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
            VerticalStack(state = state) { items(5) { index -> StackItem("Item $index") } }
        }
        rule.waitForIdle()

        // At the beginning
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isFalse()

        state.scrollToItem(2)

        rule.waitForIdle()

        // In the middle
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isTrue()

        state.scrollToItem(4)

        rule.waitForIdle()

        // At the end
        assertThat(state.canScrollForward).isFalse()
        assertThat(state.canScrollBackward).isTrue()
    }

    @Test
    fun lastScrolledForwardAndBackward_areUpdatedAfterSwipe() {
        var itemHeight = 0
        val state = StackState()
        rule.setContentWithInitialFocus {
            VerticalStack(state = state) {
                items(5) { index -> StackItem("Item $index") { itemHeight = it } }
            }
        }
        rule.waitForIdle()

        assertThat(state.lastScrolledBackward).isFalse()
        assertThat(state.lastScrolledForward).isFalse()

        performIndirectSwipe(itemHeight)
        rule.waitForIdle()

        assertThat(state.lastScrolledBackward).isFalse()
        assertThat(state.lastScrolledForward).isTrue()

        // TODO(b/413429531): remove once VerticalStack supports moving focus automatically.
        requestFocus()
        performIndirectSwipe(-itemHeight)
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

    @Test
    fun layoutInfo_sizesAreCorrect() {
        val state = StackState()
        rule.setContentWithInitialFocus {
            VerticalStack(modifier = Modifier.size(100.dp), state = state) {
                items(5) { index -> StackItem("Item $index") }
            }
        }
        rule.waitForIdle()

        with(rule.density) {
            assertThat(state.layoutInfoInternal.viewportSize)
                .isEqualTo(IntSize(width = 100.dp.roundToPx(), height = 100.dp.roundToPx()))
            assertThat(state.layoutInfoInternal.maxItemSize)
                .isEqualTo(
                    IntSize(
                        width = 100.dp.roundToPx(),
                        height = 100.dp.roundToPx() - RevealAreaSize.roundToPx(),
                    )
                )
        }
    }

    @Composable
    private fun StackItem(text: String, onHeightChanged: (Int) -> Unit = {}) {
        Box(Modifier.onSizeChanged { onHeightChanged(it.height) }.fillMaxSize().focusTarget()) {
            Text(text)
        }
    }

    private fun ComposeContentTestRule.setContentWithInitialFocus(content: @Composable () -> Unit) {
        setContent { Box(Modifier.focusRequester(focusRequester)) { content() } }
        requestFocus()
    }

    private fun performIndirectSwipe(distancePx: Int) {
        rule.onRoot().performIndirectSwipe(rule, distancePx.toFloat())
    }

    private fun requestFocus() {
        rule.runOnIdle { focusRequester.requestFocus() }
        rule.waitForIdle()
    }
}
