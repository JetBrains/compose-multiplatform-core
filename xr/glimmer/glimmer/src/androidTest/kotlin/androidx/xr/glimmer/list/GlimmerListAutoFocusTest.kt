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
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions.ScrollBy
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.xr.glimmer.Text
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class GlimmerListAutoFocusTest : BaseListTestWithOrientation(Orientation.Vertical) {

    private lateinit var focusManager: FocusManager

    @Test
    fun firstItem_is_initiallyFocused() {
        rule.setAutoFocusContent { FocusableTestList() }

        rule.onListItem(0).assertIsFocused()
    }

    @Test
    fun performScrollToIndex_movesAutoFocus() {
        rule.setAutoFocusContent { FocusableTestList(size = 100) }

        rule.onNodeWithTag(LIST_TEST_TAG).performScrollToIndex(25)
        rule.waitForIdle()

        // TODO: b/433687753 - performScrollToIndex() isn't aligned with the auto-focused item.
        // We brought item-25 to the top, but centered item-27 is focused.
        rule.onListItem(27).assertIsFocused()
    }

    @Test
    fun animateScrollToItem_movesAutoFocus() {
        rule.setAutoFocusContent {
            val state = remember { ListState() }
            FocusableTestList(state = state, size = 100)
            LaunchedEffect(Unit) { state.animateScrollToItem(42) }
        }

        rule.waitForIdle()

        // TODO: b/433687753 - animateScrollToItem() isn't aligned with the auto-focused item.
        // We brought item-42 to the top, but centered item-44 is focused.
        rule.onListItem(44).assertIsFocused()
    }

    @Test
    fun performSemanticsAction_scrollBy_movesAutoFocus() {
        rule.setAutoFocusContent { FocusableTestList(size = 100) }

        val scroll = with(rule.density) { (ItemHeight * 5).toPx() }
        rule.onNodeWithTag(LIST_TEST_TAG).performSemanticsAction(ScrollBy) { it.invoke(0f, scroll) }
        rule.waitForIdle()

        // We brought item-5 to the top, but centered item-7 is focused (screen fits up to 5 items).
        rule.onListItem(7).assertIsFocused()
    }

    @Test
    fun indirectTouch_movesAutoFocus() {
        rule.setAutoFocusContent { FocusableTestList(size = 100) }

        val swipe = with(rule.density) { (ItemHeight * 5).toPx() }
        rule.onNodeWithTag(LIST_TEST_TAG).performIndirectSwipe(swipe)
        rule.waitForIdle()

        // We brought item-5 to the top, but centered item-7 is focused (screen fits up to 5 items).
        rule.onListItem(7).assertIsFocused()
    }

    /**
     * It's hard to detect a bug when the focus line goes beyond last item. The reason is that the
     * focus remains on the last focused item if the focus line is no longer aligned with any
     * visible item (we don't clear the focus if the focus line is above an empty space). So even if
     * there's an overscroll beyond the expected range, it’s not visually obvious — the focus
     * appears correct because it's still on the last item.
     *
     * To reliably reproduce the bug, we need a really fast "swing" — jumping from somewhere in the
     * middle to beyond the end of the list — which reveals that focus didn’t actually move to the
     * correct item. It's easier to do that for short list with very fast "swing" like in this test.
     */
    @Test
    fun lastItem_is_focused_after_fastScrollToBottom() {
        rule.setAutoFocusContent { FocusableTestList(size = 3) }
        val largeScroll = with(rule.density) { 10000.dp.toPx() }

        rule.onNodeWithTag(LIST_TEST_TAG).performSemanticsAction(ScrollBy) {
            it.invoke(0f, largeScroll)
        }
        rule.waitForIdle()

        rule.onListItem(2).assertIsFocused()
    }

    @Test
    fun focusPosition_isReset_afterChangingOrientation() {
        val listOrientation = mutableStateOf(Orientation.Vertical)
        rule.setAutoFocusContent {
            FocusableTestList(size = 5, listOrientation = listOrientation.value)
        }

        // Vertical list, initially "item-0" is focused.
        rule.onListItem(0).assertIsFocused()

        // Vertical scroll to "item-4".
        val scroll = with(rule.density) { 350.dp.toPx() }
        rule.onNodeWithTag(LIST_TEST_TAG).performSemanticsAction(ScrollBy) { it.invoke(0f, scroll) }
        rule.onListItem(3).assertIsFocused()
        rule.waitForIdle()

        // Switch the list to a horizontal orientation.
        listOrientation.value = Orientation.Horizontal
        rule.waitForIdle()

        // Horizontal list, focus is reset, "item-0" is focused.
        rule.onListItem(0).assertIsFocused()
    }

    @Test
    fun mixture_of_focusable_and_nonFocusable_items() {
        rule.setAutoFocusContent {
            FocusableTestList(size = 4) { index ->
                val focusable = (index == 0) || (index == 3)
                FocusableListItem(index = index, focusable = focusable)
            }
        }

        // Center of the item-0 (focusable)
        scrollListBy(ItemHeight / 2)
        rule.onListItem(0).assertIsFocused()

        // Center of the item-1 (non-focusable)
        scrollListBy(ItemHeight)
        rule.onListItem(0).assertIsFocused()

        // Center of the item-2 (non-focusable)
        scrollListBy(ItemHeight)
        rule.onListItem(0).assertIsFocused()

        // Center of the item-3 (focusable)
        scrollListBy(ItemHeight)
        rule.onListItem(3).assertIsFocused()
    }

    @Test
    fun moveFocus_from_thePreviousFocusableElement_to_theList() {
        rule.setAutoFocusContent {
            FocusableItem(text = "Button", modifier = Modifier.testTag("button"))
            FocusableTestList(size = 1)
        }

        // Check initial focus.
        rule.onNodeWithTag("button").assertIsFocused()
        rule.onListItem(0).assertIsNotFocused()

        // Move focus into the list.
        moveFocusForward()
        rule.onNodeWithTag("button").assertIsNotFocused()
        rule.onListItem(0).assertIsFocused()
    }

    @Test
    fun moveFocus_from_theListWithSingleElement_to_theNextFocusableElement() {
        rule.setAutoFocusContent {
            FocusableTestList(size = 1)
            FocusableItem(text = "Button", modifier = Modifier.testTag("button"))
        }

        // Check initial focus.
        rule.onListItem(0).assertIsFocused()
        rule.onNodeWithTag("button").assertIsNotFocused()

        // Move focus out of the list.
        moveFocusForward()
        rule.onListItem(0).assertIsNotFocused()
        rule.onNodeWithTag("button").assertIsFocused()
    }

    @Test
    fun moveFocus_from_theLongList_to_theNextFocusableElement() {
        rule.setAutoFocusContent {
            FocusableTestList(size = 10)
            FocusableItem(text = "Button", modifier = Modifier.testTag("button"))
        }

        // Check initial focus.
        rule.onListItem(0).assertIsFocused()
        rule.onNodeWithTag("button").assertIsNotFocused()

        // Move focus to the last item in the list.
        val scroll = with(rule.density) { (ItemHeight * 10).toPx() }
        rule.onNodeWithTag(LIST_TEST_TAG).performSemanticsAction(ScrollBy) { it.invoke(0f, scroll) }
        rule.waitForIdle()
        rule.onListItem(9).assertIsFocused()

        // Move focus out of the list.
        moveFocusForward()
        rule.onListItem(9).assertIsNotFocused()
        rule.onNodeWithTag("button").assertIsFocused()
    }

    @Test
    fun list_doesNotStealFocus_whenAdded() {
        val addList = mutableStateOf(false)
        rule.setAutoFocusContent {
            if (addList.value) {
                FocusableTestList(size = 1)
            }
            FocusableItem(text = "Button", modifier = Modifier.testTag("button"))
        }

        // Check the button is focused.
        rule.onNodeWithTag("button").assertIsFocused()

        // Bring the list to the screen.
        addList.value = true
        rule.waitForIdle()

        // Check that focus remains on the button.
        rule.onListItem(0).assertIsNotFocused()
        rule.onNodeWithTag("button").assertIsFocused()
    }

    private fun scrollListBy(scroll: Dp) {
        val pixels = with(rule.density) { scroll.toPx() }
        rule.onNodeWithTag(LIST_TEST_TAG).performSemanticsAction(ScrollBy) { it.invoke(0f, pixels) }
        rule.waitForIdle()
    }

    private fun moveFocusForward() {
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
        rule.waitForIdle()
    }

    private fun ComposeContentTestRule.setAutoFocusContent(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val focusRequester = FocusRequester()
        setContent {
            scope = rememberCoroutineScope()
            focusManager = LocalFocusManager.current
            Column(modifier.focusRequester(focusRequester)) { content() }
        }
        // Request initial focus.
        rule.runOnIdle { focusRequester.requestFocus() }
        rule.waitForIdle()
    }

    /**
     *      __________________
     *     |  _____________   | 0
     *     | |    item-0   |  |
     *     | |_____________|  |
     *     |  _____________   | 100
     *     | |    item-1   |  |
     *     | |_____________|  |
     *     |  _____________   | 200
     *     | |    item-2   |  |
     *     | |_____________|  |
     *     |  _____________   | 300
     *     | |    item-3   |  |
     *     | |_____________|  |
     *     |  _____________   | 400
     *     | |    item-4   |  |
     *     | |_____________|  |
     *     |__________________| 500
     *
     * The list can display up to 5 fully visible items at a time.
     */
    @Composable
    private fun FocusableTestList(
        size: Int = 100,
        listOrientation: Orientation = orientation,
        state: ListState = rememberListState(),
        itemContent: @Composable (Int) -> Unit = { FocusableListItem(it) },
    ) {
        TestList(
            state = state,
            itemsCount = size,
            listOrientation = listOrientation,
            modifier = Modifier.requiredSize(ItemWidth * 3, ItemHeight * ItemsPerScreen),
        ) { index ->
            itemContent(index)
        }
    }

    @Composable
    private fun FocusableListItem(index: Int, focusable: Boolean = true) {
        FocusableItem(
            text = index.toString(),
            modifier = Modifier.testTag("item-$index"),
            focusable = focusable,
        )
    }

    @Composable
    private fun FocusableItem(
        text: String,
        modifier: Modifier = Modifier,
        focusable: Boolean = true,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                modifier
                    .requiredSize(ItemWidth, ItemHeight)
                    .background(color = if (isFocused) Color.Red else Color.Green)
                    .border(1.dp, Color.Black)
                    .focusable(focusable, interactionSource),
        ) {
            Text(text = text, fontSize = 30.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }

    fun SemanticsNodeInteractionsProvider.onListItem(index: Int): SemanticsNodeInteraction {
        return onNodeWithTag("item-$index")
    }
}

private val ItemWidth: Dp = 100.dp

private val ItemHeight: Dp = 100.dp

private const val ItemsPerScreen: Int = 5
