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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.filters.MediumTest
import androidx.xr.glimmer.Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
internal class GlimmerListAutoFocusScrollTest(private val testCase: FocusStrategyScrollTestCase) :
    BaseListTestWithOrientation(Orientation.Vertical) {

    @Test
    fun verifyFocusIsSetCorrectlyAtEachScrollStep() = runTest {
        val state = ListState()
        rule.setContent { FocusableTestList(state, testCase) }

        scrollIterate(state, testCase) { iteration, scrollPx ->
            val expectedFocusIndex = testCase.focusedItemIndexForEachStep[iteration]
            val itemTestTag = itemTag(expectedFocusIndex)
            rule.onNodeWithTag(itemTestTag).assert(isFocused()) {
                val scrollDp = with(rule.density) { scrollPx.toDp() }
                "When the user scrolls $scrollDp, [$itemTestTag] should gain focus."
            }
        }
    }

    /** Use this method to generate new numbers for test cases whenever the focus logic changes. */
    private suspend fun generateDebugSampleNumbers(
        state: ListState,
        testCase: FocusStrategyScrollTestCase,
    ): List<Int> {
        val goldenSamples = ArrayList<Int>()

        scrollIterate(state, testCase) { _, _ ->
            val node = rule.onNodeWithTag(LIST_TEST_TAG).fetchSemanticsNode()
            for (child in node.children) {
                val tagProperty =
                    child.config.find { (key, _) -> key == SemanticsProperties.TestTag }
                val focusedProperty =
                    child.config.find { (key, _) -> key == SemanticsProperties.Focused }
                val isFocused = focusedProperty?.value as? Boolean ?: false
                val tag = tagProperty?.value as? String ?: "no test tag"
                if (isFocused) {
                    val index = tag.removePrefix("item-").toInt()
                    goldenSamples.add(index)
                }
            }
        }

        return goldenSamples
    }

    /**
     * Using a [testCase] iterates through the list with the given parameters. Callback
     * [onScrollStep] gets invoked on every scroll step.
     */
    private suspend fun scrollIterate(
        state: ListState,
        testCase: FocusStrategyScrollTestCase,
        onScrollStep: suspend (iteration: Int, totalScrolled: Float) -> Unit,
    ) {
        var iteration = 0
        var totalScrolled = 0f
        val contentSizePx = with(rule.density) { testCase.scrollDistance.toPx() }
        val scrollStepPx = with(rule.density) { testCase.scrollStep.toPx() }

        onScrollStep(iteration, totalScrolled)

        while (totalScrolled < contentSizePx) {
            state.scrollAndWaitForIdle(scrollStepPx)
            totalScrolled += scrollStepPx
            ++iteration
            onScrollStep(iteration, totalScrolled)
        }
    }

    private suspend fun ListState.scrollAndWaitForIdle(delta: Float) {
        val job = rule.runOnIdle { scope.launch { scrollBy(delta) } }
        job.join()
        rule.waitForIdle()
    }

    private fun itemTag(index: Int): String {
        return "item-$index"
    }

    @Composable
    private fun FocusableTestList(state: ListState, testCase: FocusStrategyScrollTestCase) {
        scope = rememberCoroutineScope()
        TestList(
            state = state,
            itemsCount = testCase.itemsCount,
            modifier = Modifier.requiredSize(200.dp, testCase.listSize),
        ) { index ->
            FocusableItem(index, Modifier.requiredSize(200.dp, testCase.itemSize))
        }
    }

    @Composable
    private fun FocusableItem(index: Int, modifier: Modifier = Modifier) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                modifier
                    .testTag(itemTag(index))
                    .background(color = if (isFocused) Color.Red else Color.Green)
                    .border(1.dp, Color.Black)
                    .focusable(true, interactionSource),
        ) {
            Text(
                text = index.toString(),
                fontSize = 30.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    internal data class FocusStrategyScrollTestCase(
        val itemsCount: Int,
        val itemSize: Dp,
        val listSize: Dp,
        val scrollStep: Dp,
        val scrollDistance: Dp,
        val focusedItemIndexForEachStep: List<Int>,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}-{0}")
        internal fun params(): Array<FocusStrategyScrollTestCase> =
            arrayOf(
                // Large list, 2 * threshold < content size.
                FocusStrategyScrollTestCase(
                    itemsCount = 15,
                    itemSize = 100.dp,
                    listSize = 500.dp,
                    scrollStep = 50.dp,
                    scrollDistance = 1000.dp,
                    focusedItemIndexForEachStep =
                        listOf(0, 0, 1, 2, 3, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 10, 11, 12, 13, 14, 14),
                ),
                // Large list (another parameters), 2 * threshold < content size.
                FocusStrategyScrollTestCase(
                    itemsCount = 20,
                    itemSize = 50.dp,
                    listSize = 300.dp,
                    scrollStep = 30.dp,
                    scrollDistance = 700.dp,
                    focusedItemIndexForEachStep =
                        listOf(
                            0,
                            1,
                            2,
                            3,
                            4,
                            5,
                            6,
                            7,
                            7,
                            8,
                            9,
                            9,
                            10,
                            10,
                            11,
                            12,
                            12,
                            13,
                            14,
                            15,
                            16,
                            17,
                            18,
                            19,
                            19,
                        ),
                ),
                // Medium list, 2 * threshold == content size.
                FocusStrategyScrollTestCase(
                    itemsCount = 10,
                    itemSize = 50.dp,
                    listSize = 250.dp,
                    scrollStep = 15.dp,
                    scrollDistance = 250.dp,
                    focusedItemIndexForEachStep =
                        listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6, 7, 7, 8, 8, 9),
                ),
                // Short list, 2 * threshold < content size.
                FocusStrategyScrollTestCase(
                    itemsCount = 10,
                    itemSize = 100.dp,
                    listSize = 500.dp,
                    scrollStep = 10.dp,
                    scrollDistance = 600.dp,
                    focusedItemIndexForEachStep =
                        listOf(
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            1,
                            1,
                            1,
                            1,
                            1,
                            2,
                            2,
                            2,
                            2,
                            2,
                            2,
                            3,
                            3,
                            3,
                            3,
                            3,
                            4,
                            4,
                            4,
                            4,
                            4,
                            4,
                            5,
                            5,
                            5,
                            5,
                            5,
                            6,
                            6,
                            6,
                            6,
                            6,
                            6,
                            7,
                            7,
                            7,
                            7,
                            7,
                            8,
                            8,
                            8,
                            8,
                            8,
                            8,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                            9,
                        ),
                ),
                // List w/o scroll - all content fits into viewport.
                FocusStrategyScrollTestCase(
                    itemsCount = 8,
                    itemSize = 50.dp,
                    listSize = 500.dp,
                    scrollStep = 20.dp,
                    scrollDistance = 400.dp,
                    focusedItemIndexForEachStep =
                        listOf(0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 4, 4, 5, 5, 6, 6, 6, 7, 7, 7),
                ),
            )
    }
}
