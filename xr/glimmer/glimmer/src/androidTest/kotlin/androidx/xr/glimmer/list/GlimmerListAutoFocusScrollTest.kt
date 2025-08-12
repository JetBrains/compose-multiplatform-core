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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
        val density = Density(1f)
        rule.setContentAndSaveScope {
            CompositionLocalProvider(LocalDensity provides density) {
                FocusableTestList(state, testCase)
            }
        }

        runTestCase(state, density, testCase)
    }

    private suspend fun runTestCase(
        state: ListState,
        density: Density,
        testCase: FocusStrategyScrollTestCase,
    ) {
        var prevScrollPx = 0f
        for ((targetScroll, expectedFocusTag) in testCase.expectedFocusedItemForScrollOffset) {
            // Calculates the diff for the next scroll position.
            val targetScrollPx = with(density) { targetScroll.toPx() }
            val diffScrollPx = targetScrollPx - prevScrollPx
            prevScrollPx = targetScrollPx

            // Scrolls the list to the target position.
            state.scrollAndWaitForIdle(diffScrollPx)

            // Checks if expected items is focused.
            rule.onNodeWithTag(expectedFocusTag).assert(isFocused()) {
                val debugListTree = rule.onNodeWithTag(LIST_TEST_TAG).printToString()
                buildString {
                    append("When the user scrolls $targetScroll, ")
                    appendLine("item with tag [$expectedFocusTag] should gain focus.")
                    appendLine("Debug list tree: $debugListTree")
                }
            }
        }
    }

    /**
     * Use this method to generate new numbers for test cases whenever the focus logic changes. It
     * iterates through the list with given [scrollStep] and on each step records which item was
     * focused to generate data for [FocusStrategyScrollTestCase.expectedFocusedItemForScrollOffset]
     * field.
     */
    private suspend fun generateDebugSampleNumbers(
        state: ListState,
        density: Density,
        scrollStep: Dp,
        scrollDistance: Dp,
    ): Map<Dp, String> {
        val focusedItemsByScrollOffset = mutableMapOf<Dp, String>()

        val scrollDistancePx = with(density) { scrollDistance.toPx() }
        val scrollStepPx = with(density) { scrollStep.toPx() }
        var totalScrolledPx = 0f

        while (totalScrolledPx < scrollDistancePx) {
            state.scrollAndWaitForIdle(scrollStepPx)
            totalScrolledPx += scrollStepPx

            val focusedTag = rule.onNodeWithTag(LIST_TEST_TAG).findFocusedChildTag()
            val totalScrolledDp = with(density) { totalScrolledPx.toDp() }
            focusedItemsByScrollOffset[totalScrolledDp] = requireNotNull(focusedTag)
        }

        return focusedItemsByScrollOffset
    }

    private suspend fun ListState.scrollAndWaitForIdle(delta: Float) {
        val job = rule.runOnIdle { scope.launch { scrollBy(delta) } }
        job.join()
        rule.waitForIdle()
    }

    @Composable
    private fun FocusableTestList(state: ListState, testCase: FocusStrategyScrollTestCase) {
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
                    .testTag("item-$index")
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
        // Defines which item should be focused based on the scroll offset.
        val expectedFocusedItemForScrollOffset: Map<Dp, String>,
    ) {
        override fun toString(): String {
            return buildString {
                append("FocusStrategyScrollTestCase(")
                append("itemsCount=$itemsCount, ")
                append("itemSize=$itemSize, ")
                append("listSize=$listSize)")
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}-{0}")
        internal fun params(): Array<FocusStrategyScrollTestCase> =
            arrayOf(
                // Large list, 2 * threshold < available scroll.
                FocusStrategyScrollTestCase(
                    itemsCount = 15,
                    itemSize = 100.dp,
                    listSize = 500.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            45.0.dp to "item-0",
                            90.0.dp to "item-1",
                            135.0.dp to "item-2",
                            180.0.dp to "item-3",
                            225.0.dp to "item-4",
                            270.0.dp to "item-4",
                            315.0.dp to "item-5",
                            360.0.dp to "item-6",
                            405.0.dp to "item-6",
                            445.0.dp to "item-6",
                            495.0.dp to "item-7",
                            540.0.dp to "item-7",
                            585.0.dp to "item-8",
                            630.0.dp to "item-8",
                            675.0.dp to "item-9",
                            720.0.dp to "item-10",
                            765.0.dp to "item-10",
                            810.0.dp to "item-11",
                            855.0.dp to "item-12",
                            900.0.dp to "item-13",
                            945.0.dp to "item-14",
                            990.0.dp to "item-14",
                            1035.0.dp to "item-14",
                        ),
                ),
                // Large list, 2 * threshold < available scroll.
                FocusStrategyScrollTestCase(
                    itemsCount = 20,
                    itemSize = 50.dp,
                    listSize = 300.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            30.0.dp to "item-1",
                            60.0.dp to "item-2",
                            90.0.dp to "item-3",
                            120.0.dp to "item-4",
                            150.0.dp to "item-5",
                            180.0.dp to "item-6",
                            210.0.dp to "item-7",
                            240.0.dp to "item-7",
                            270.0.dp to "item-8",
                            305.0.dp to "item-9",
                            330.0.dp to "item-9",
                            360.0.dp to "item-10",
                            390.0.dp to "item-10",
                            420.0.dp to "item-11",
                            455.0.dp to "item-12",
                            480.0.dp to "item-12",
                            510.0.dp to "item-13",
                            540.0.dp to "item-14",
                            570.0.dp to "item-15",
                            600.0.dp to "item-16",
                            630.0.dp to "item-17",
                            660.0.dp to "item-18",
                            690.0.dp to "item-19",
                            720.0.dp to "item-19",
                        ),
                ),
                // Medium list, 2 * threshold == available scroll.
                FocusStrategyScrollTestCase(
                    itemsCount = 11,
                    itemSize = 60.dp,
                    listSize = 300.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            25.0.dp to "item-0",
                            50.0.dp to "item-1",
                            80.0.dp to "item-2",
                            105.0.dp to "item-3",
                            125.0.dp to "item-3",
                            155.0.dp to "item-4",
                            175.0.dp to "item-5",
                            205.0.dp to "item-6",
                            225.0.dp to "item-6",
                            250.0.dp to "item-7",
                            275.0.dp to "item-8",
                            300.0.dp to "item-9",
                            320.0.dp to "item-9",
                            350.0.dp to "item-10",
                            360.0.dp to "item-10",
                        ),
                ),
                // Short list, 2 * threshold > available scroll.
                FocusStrategyScrollTestCase(
                    itemsCount = 10,
                    itemSize = 50.dp,
                    listSize = 250.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            15.0.dp to "item-0",
                            30.0.dp to "item-1",
                            45.0.dp to "item-1",
                            60.0.dp to "item-2",
                            75.0.dp to "item-2",
                            90.0.dp to "item-3",
                            105.0.dp to "item-3",
                            120.0.dp to "item-4",
                            130.0.dp to "item-4",
                            150.0.dp to "item-5",
                            170.0.dp to "item-6",
                            180.0.dp to "item-6",
                            195.0.dp to "item-7",
                            210.0.dp to "item-7",
                            225.0.dp to "item-8",
                            240.0.dp to "item-8",
                            250.0.dp to "item-9",
                        ),
                ),
                // Short list, 2 * threshold > available scroll.
                FocusStrategyScrollTestCase(
                    itemsCount = 10,
                    itemSize = 100.dp,
                    listSize = 500.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            10.0.dp to "item-0",
                            20.0.dp to "item-0",
                            30.0.dp to "item-0",
                            40.0.dp to "item-0",
                            50.0.dp to "item-0",
                            60.0.dp to "item-1",
                            70.0.dp to "item-1",
                            80.0.dp to "item-1",
                            90.0.dp to "item-1",
                            100.0.dp to "item-1",
                            115.0.dp to "item-2",
                            120.0.dp to "item-2",
                            130.0.dp to "item-2",
                            140.0.dp to "item-2",
                            150.0.dp to "item-2",
                            160.0.dp to "item-2",
                            171.0.dp to "item-3",
                            180.0.dp to "item-3",
                            190.0.dp to "item-3",
                            200.0.dp to "item-3",
                            210.0.dp to "item-3",
                            225.0.dp to "item-4",
                            230.0.dp to "item-4",
                            240.0.dp to "item-4",
                            250.0.dp to "item-4",
                            260.0.dp to "item-4",
                            265.0.dp to "item-4",
                            280.0.dp to "item-5",
                            290.0.dp to "item-5",
                            300.0.dp to "item-5",
                            310.0.dp to "item-5",
                            320.0.dp to "item-5",
                            335.0.dp to "item-6",
                            340.0.dp to "item-6",
                            350.0.dp to "item-6",
                            360.0.dp to "item-6",
                            370.0.dp to "item-6",
                            375.0.dp to "item-6",
                            390.0.dp to "item-7",
                            400.0.dp to "item-7",
                            410.0.dp to "item-7",
                            420.0.dp to "item-7",
                            430.0.dp to "item-7",
                            440.0.dp to "item-8",
                            450.0.dp to "item-8",
                            460.0.dp to "item-8",
                            470.0.dp to "item-8",
                            480.0.dp to "item-8",
                            485.0.dp to "item-8",
                            505.0.dp to "item-9",
                            510.0.dp to "item-9",
                            520.0.dp to "item-9",
                            530.0.dp to "item-9",
                            540.0.dp to "item-9",
                            550.0.dp to "item-9",
                            560.0.dp to "item-9",
                            575.0.dp to "item-9",
                        ),
                ),
                // List w/o scroll - all content fits into viewport.
                FocusStrategyScrollTestCase(
                    itemsCount = 8,
                    itemSize = 50.dp,
                    listSize = 500.dp,
                    expectedFocusedItemForScrollOffset =
                        mapOf(
                            20.0.dp to "item-0",
                            40.0.dp to "item-0",
                            60.0.dp to "item-1",
                            80.0.dp to "item-1",
                            105.0.dp to "item-2",
                            120.0.dp to "item-2",
                            140.0.dp to "item-2",
                            160.0.dp to "item-3",
                            180.0.dp to "item-3",
                            205.0.dp to "item-4",
                            220.0.dp to "item-4",
                            240.0.dp to "item-4",
                            260.0.dp to "item-5",
                            280.0.dp to "item-5",
                            305.0.dp to "item-6",
                            320.0.dp to "item-6",
                            340.0.dp to "item-6",
                            360.0.dp to "item-7",
                            380.0.dp to "item-7",
                            400.0.dp to "item-7",
                        ),
                ),
            )
    }
}

private fun SemanticsNodeInteraction.findFocusedChildTag(): String? {
    val semanticNode = fetchSemanticsNode()
    for (child in semanticNode.children) {
        val tagProperty = child.config.find { (key, _) -> key == SemanticsProperties.TestTag }
        val focusedProperty = child.config.find { (key, _) -> key == SemanticsProperties.Focused }
        val isFocused = focusedProperty?.value as? Boolean ?: false
        val tag = tagProperty?.value as? String ?: "no test tag"
        if (isFocused) return tag
    }
    return null
}

private fun Map<Dp, String>.toTestCase(): String {
    return buildString {
        appendLine("expectedFocusedItemForScrollOffset = mapOf(")
        for ((dp, item) in this@toTestCase) {
            appendLine("\t$dp to \"$item\",")
        }
        appendLine("),")
    }
}
