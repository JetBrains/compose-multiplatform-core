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

package androidx.xr.glimmer.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.testutils.assertIsEqualTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.setGlimmerThemeContent
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class ListStateTest(orientation: Orientation) : BaseListTestWithOrientation(orientation) {

    @Test
    fun scrollToItemOnState() {
        val state = ListState()
        rule.setGlimmerThemeContent { TestList(state = state) { Text("Item ($it)") } }

        // Make sure the far items are not laid out.
        val targetIndex = Int.MAX_VALUE / 2
        rule.onNodeWithText("Item ($targetIndex)").assertDoesNotExist()

        // Scroll to the far index and check that the item become visible.
        rule.runOnUiThread { runBlocking { state.scrollToItem(targetIndex) } }
        rule.onNodeWithText("Item ($targetIndex)").assertIsDisplayed()
    }

    @Test
    fun dispatchRawDeltaOnState() {
        val state = ListState()
        val sizeInDp = 100.dp
        val sizeInPx = with(rule.density) { sizeInDp.toPx() }

        rule.setGlimmerThemeContent {
            TestList(state = state) {
                Text(text = "Item ($it)", modifier = Modifier.size(sizeInDp))
            }
        }

        rule.runOnUiThread { state.dispatchRawDelta(300 * sizeInPx) }
        rule.onNodeWithText("Item (300)").assertIsDisplayed()
    }

    @Test
    fun scrollByOnState() {
        val state = ListState()
        rule.setGlimmerThemeContent {
            TestList(
                state = state,
                modifier = Modifier.size(100.dp).background(Color.Black),
                itemContent = { index ->
                    Box(Modifier.size(20.dp).background(Color.Red).testTag("item-tag-$index"))
                },
            )
        }

        // Scroll the first three elements.
        val scrollInPx = with(rule.density) { 50.dp.toPx() }
        rule.runOnUiThread { runBlocking { state.scrollBy(scrollInPx) } }

        // Check that the 4th element is almost first in the list.
        val childRect = rule.onNodeWithTag("item-tag-3").getBoundsInRoot()
        val offset: Dp = if (vertical) childRect.top else childRect.left
        offset.assertIsEqualTo(expected = 10.dp, tolerance = 1.dp)
    }

    @Test
    fun saveAndRestoreState() {
        val allowingScope = SaverScope { true }
        val original = ListState(firstVisibleItemIndex = 42, firstVisibleItemScrollOffset = 100)

        val saved = with(ListState.Saver) { allowingScope.save(original) }
        val restored = ListState.Saver.restore(requireNotNull(saved))

        assertThat(restored?.firstVisibleItemIndex).isEqualTo(original.firstVisibleItemIndex)
        assertThat(restored?.firstVisibleItemScrollOffset)
            .isEqualTo(original.firstVisibleItemScrollOffset)
    }

    @Test
    fun respectCanScrollForwardAndCanScrollBackward() {
        val state = ListState(firstVisibleItemIndex = 10)
        rule.setGlimmerThemeContent { TestList(state = state) { Text("Item ($it)") } }
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isTrue()

        rule.runOnIdle { runBlocking { state.scrollToItem(0) } }

        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isFalse()

        rule.runOnIdle { runBlocking { state.scrollToItem(Int.MAX_VALUE - 1) } }

        assertThat(state.canScrollForward).isFalse()
        assertThat(state.canScrollBackward).isTrue()
    }

    @Test
    fun accumulatedPart_isApplied() = runTest {
        val state = ListState()
        rule.setContentAndSaveScope {
            TestList(
                state = state,
                modifier = Modifier.size(100.dp),
                itemContent = { Box(Modifier.size(20.dp).testTag("item-tag-$it")) },
            )
        }
        // Set up the accumulated value. We expect it will be used in the next measure pass.
        // The values inside the list state have an opposite sign, so it's negative.
        val accumulatedValue = with(rule.density) { -60.dp.toPx() }
        state.applyMeasureResult(
            result = state.layoutInfo as GlimmerListMeasureResult,
            consumedScroll = 0f,
            accumulatedScroll = accumulatedValue,
        )

        // Scroll by a single pixel - we expect that accumulated value will be added up.
        state.scrollByAndWaitForIdle(1.dp)

        // Check that 4th item is the first one.
        Truth.assertThat(state.firstVisibleItemIndex).isEqualTo(3)
    }

    @Test
    fun scrolling_and_nonScrolling_measurePasses_workTogether_correctly() = runTest {
        val state = ListState()
        rule.setContentAndSaveScope {
            TestList(
                state = state,
                modifier = Modifier.size(100.dp).background(Color.Black),
                itemContent = { Box(Modifier.size(20.dp).testTag("item-tag-$it")) { Text("$it") } },
            )
        }

        // Scrolling measure pass (item-2).
        state.scrollByAndWaitForIdle(41.dp)
        Truth.assertThat(state.firstVisibleItemIndex).isEqualTo(2)

        // Non-scrolling measure pass (item-0).
        scope.launch { state.animateScrollToItem(0) }
        rule.waitForIdle()
        Truth.assertThat(state.firstVisibleItemIndex).isEqualTo(0)

        // Scrolling measure pass (item-1).
        state.scrollByAndWaitForIdle(21.dp)
        Truth.assertThat(state.firstVisibleItemIndex).isEqualTo(1)
    }

    private suspend fun ListState.scrollByAndWaitForIdle(value: Dp) {
        val valuePx = with(rule.density) { value.toPx() }
        val job = rule.runOnIdle { scope.launch { scrollBy(valuePx) } }
        job.join()
        rule.waitForIdle()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() = arrayOf(Orientation.Vertical, Orientation.Horizontal)
    }
}
