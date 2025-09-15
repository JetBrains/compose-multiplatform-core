/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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

package androidx.compose.foundation

import androidx.compose.foundation.ComposeFoundationFlags.isScrollMinConstraintsFixEnabled
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ScrollNodeMeasureTest(private val config: Config) {
    data class Config(val orientation: Orientation, val isScrollMinConstraintsFixEnabled: Boolean)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Config> =
            listOf(
                Config(Orientation.Vertical, false),
                Config(Orientation.Horizontal, false),
                Config(Orientation.Vertical, true),
                Config(Orientation.Horizontal, true),
            )
    }

    @get:Rule val rule = createComposeRule()

    private val isVertical
        get() = config.orientation == Orientation.Vertical

    @OptIn(ExperimentalFoundationApi::class)
    @BeforeTest
    fun setup() {
        isScrollMinConstraintsFixEnabled = config.isScrollMinConstraintsFixEnabled
    }

    @OptIn(ExperimentalFoundationApi::class)
    @AfterTest
    fun teardown() {
        isScrollMinConstraintsFixEnabled = true
    }

    @Test
    fun fixedConstraints_contentLargerThanContainer() {
        val containerSize = 100
        val contentSize = 200
        val scrollState = ScrollState(0)

        val result =
            with(rule.density) {
                setupScrollNodeTest(
                    scrollState = scrollState,
                    containerModifier = Modifier.size(containerSize.toDp()),
                    contentModifier =
                        Modifier.mainAxisSize(max = contentSize.toDp(), min = contentSize.toDp())
                            .crossAxisSize(max = containerSize.toDp(), min = containerSize.toDp()),
                )
            }

        rule.runOnIdle {
            // Assert on container size
            result.assertContainerSize(mainAxisSize = containerSize, crossAxisSize = containerSize)

            // Assert on child constraints
            result.assertChildConstraints(
                minMainAxis = if (config.isScrollMinConstraintsFixEnabled) 0 else containerSize,
                maxMainAxis = Constraints.Infinity,
                minCrossAxis = containerSize,
                maxCrossAxis = containerSize,
            )

            // Assert on content size
            result.assertContentSize(mainAxisSize = contentSize, crossAxisSize = containerSize)

            // Assert on scroll state, which is updated by the measure function
            assertThat(scrollState.maxValue).isEqualTo(contentSize - containerSize)
            assertThat(scrollState.viewportSize).isEqualTo(containerSize)
        }
    }

    @Test
    fun fixedConstraints_contentSmallerThanContainer() {
        val containerSize = 200
        val contentSize = 100
        val scrollState = ScrollState(0)

        val result =
            with(rule.density) {
                setupScrollNodeTest(
                    scrollState = scrollState,
                    containerModifier = Modifier.size(containerSize.toDp()),
                    contentModifier =
                        Modifier.mainAxisSize(max = contentSize.toDp(), min = contentSize.toDp())
                            .crossAxisSize(max = containerSize.toDp(), min = containerSize.toDp()),
                )
            }

        rule.runOnIdle {
            // Assert on container size
            result.assertContainerSize(mainAxisSize = containerSize, crossAxisSize = containerSize)

            // Assert on child constraints
            result.assertChildConstraints(
                minMainAxis = if (config.isScrollMinConstraintsFixEnabled) 0 else containerSize,
                maxMainAxis = Constraints.Infinity,
                minCrossAxis = containerSize,
                maxCrossAxis = containerSize,
            )

            // Assert on content size
            result.assertContentSize(
                mainAxisSize =
                    if (config.isScrollMinConstraintsFixEnabled) contentSize else containerSize,
                crossAxisSize = containerSize,
            )

            // Assert on scroll state, which is updated by the measure function
            assertThat(scrollState.maxValue).isEqualTo(0) // Not scrollable
            assertThat(scrollState.viewportSize).isEqualTo(containerSize)
        }
    }

    @Test
    fun boundedConstraintsOnMainAxis_contentSmallerThanMinConstraint() {
        val containerMinSize = 100
        val containerMaxSize = 200
        val containerCrossAxisSize = 300
        val contentSize = 50
        val scrollState = ScrollState(0)

        val result =
            with(rule.density) {
                setupScrollNodeTest(
                    scrollState = scrollState,
                    containerModifier =
                        Modifier.mainAxisSize(
                                max = containerMaxSize.toDp(),
                                min = containerMinSize.toDp(),
                            )
                            .crossAxisSize(
                                max = containerCrossAxisSize.toDp(),
                                min = containerCrossAxisSize.toDp(),
                            ),
                    contentModifier = Modifier.size(contentSize.toDp()),
                )
            }

        rule.runOnIdle {
            // Assert on container size
            result.assertContainerSize(
                mainAxisSize = containerMinSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on child constraints
            result.assertChildConstraints(
                minMainAxis = if (config.isScrollMinConstraintsFixEnabled) 0 else containerMinSize,
                maxMainAxis = Constraints.Infinity,
                minCrossAxis = containerCrossAxisSize,
                maxCrossAxis = containerCrossAxisSize,
            )

            // Assert on content size
            result.assertContentSize(
                mainAxisSize =
                    if (config.isScrollMinConstraintsFixEnabled) contentSize else containerMinSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on scroll state, which is updated by the measure function
            assertThat(scrollState.maxValue).isEqualTo(0) // Not scrollable
            assertThat(scrollState.viewportSize).isEqualTo(containerMinSize)
        }
    }

    @Test
    fun boundedConstraintsOnMainAxis_contentSizeWithinBounds() {
        val containerMinSize = 100
        val containerMaxSize = 200
        val containerCrossAxisSize = 300
        val contentSize = 150
        val scrollState = ScrollState(0)

        val result =
            with(rule.density) {
                setupScrollNodeTest(
                    scrollState = scrollState,
                    containerModifier =
                        Modifier.mainAxisSize(
                                max = containerMaxSize.toDp(),
                                min = containerMinSize.toDp(),
                            )
                            .crossAxisSize(
                                max = containerCrossAxisSize.toDp(),
                                min = containerCrossAxisSize.toDp(),
                            ),
                    contentModifier = Modifier.size(contentSize.toDp()),
                )
            }

        rule.runOnIdle {
            // Assert on container size
            result.assertContainerSize(
                mainAxisSize = contentSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on child constraints
            result.assertChildConstraints(
                minMainAxis = if (config.isScrollMinConstraintsFixEnabled) 0 else containerMinSize,
                maxMainAxis = Constraints.Infinity,
                minCrossAxis = containerCrossAxisSize,
                maxCrossAxis = containerCrossAxisSize,
            )

            // Assert on content size
            result.assertContentSize(
                mainAxisSize = contentSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on scroll state, which is updated by the measure function
            assertThat(scrollState.maxValue).isEqualTo(0) // Not scrollable
            assertThat(scrollState.viewportSize).isEqualTo(contentSize)
        }
    }

    @Test
    fun boundedConstraintsOnMainAxis_contentLargerThanMaxConstraint() {
        val containerMinSize = 100
        val containerMaxSize = 200
        val containerCrossAxisSize = 300
        val contentSize = 250
        val scrollState = ScrollState(0)

        val result =
            with(rule.density) {
                setupScrollNodeTest(
                    scrollState = scrollState,
                    containerModifier =
                        Modifier.mainAxisSize(
                                max = containerMaxSize.toDp(),
                                min = containerMinSize.toDp(),
                            )
                            .crossAxisSize(
                                max = containerCrossAxisSize.toDp(),
                                min = containerCrossAxisSize.toDp(),
                            ),
                    contentModifier = Modifier.size(contentSize.toDp()),
                )
            }

        rule.runOnIdle {
            // Assert on container size
            result.assertContainerSize(
                mainAxisSize = containerMaxSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on child constraints
            result.assertChildConstraints(
                minMainAxis = if (config.isScrollMinConstraintsFixEnabled) 0 else containerMinSize,
                maxMainAxis = Constraints.Infinity,
                minCrossAxis = containerCrossAxisSize,
                maxCrossAxis = containerCrossAxisSize,
            )

            // Assert on content size
            result.assertContentSize(
                mainAxisSize = contentSize,
                crossAxisSize = containerCrossAxisSize,
            )

            // Assert on scroll state, which is updated by the measure function
            assertThat(scrollState.maxValue).isEqualTo(contentSize - containerMaxSize)
            assertThat(scrollState.viewportSize).isEqualTo(containerMaxSize)
        }
    }

    // Helper functions
    private fun Modifier.mainAxisSize(max: Dp, min: Dp = 0.dp): Modifier =
        if (isVertical) {
            this.sizeIn(minHeight = min, maxHeight = max)
        } else {
            this.sizeIn(minWidth = min, maxWidth = max)
        }

    private fun Modifier.crossAxisSize(max: Dp, min: Dp = 0.dp): Modifier =
        if (isVertical) {
            this.sizeIn(minWidth = min, maxWidth = max)
        } else {
            this.sizeIn(minHeight = min, maxHeight = max)
        }

    @Composable
    private fun Modifier.scrollWithOrientation(scrollState: ScrollState? = null): Modifier {
        val state = scrollState ?: rememberScrollState()
        return if (isVertical) {
            this.verticalScroll(state)
        } else {
            this.horizontalScroll(state)
        }
    }

    private data class ScrollNodeTestResult(
        val containerSize: IntSize,
        val contentSize: IntSize,
        val childConstraints: Constraints,
    )

    private fun setupScrollNodeTest(
        scrollState: ScrollState,
        containerModifier: Modifier,
        contentModifier: Modifier,
    ): ScrollNodeTestResult {
        var containerLayoutSize: IntSize? = null
        var contentLayoutSize: IntSize? = null
        var childConstraints: Constraints? = null

        rule.setContent {
            Box(
                containerModifier
                    .onSizeChanged { containerLayoutSize = it }
                    .scrollWithOrientation(scrollState),
                propagateMinConstraints = true,
            ) {
                Box(
                    Modifier.layout { measurable, constraints ->
                            childConstraints = constraints
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .then(contentModifier)
                        .onSizeChanged { contentLayoutSize = it }
                )
            }
        }

        rule.waitForIdle()

        return ScrollNodeTestResult(
            containerSize = assertNotNull(containerLayoutSize),
            contentSize = assertNotNull(contentLayoutSize),
            childConstraints = assertNotNull(childConstraints),
        )
    }

    private fun ScrollNodeTestResult.assertContainerSize(mainAxisSize: Int, crossAxisSize: Int) {
        if (isVertical) {
            assertThat(containerSize).isEqualTo(IntSize(crossAxisSize, mainAxisSize))
        } else {
            assertThat(containerSize).isEqualTo(IntSize(mainAxisSize, crossAxisSize))
        }
    }

    private fun ScrollNodeTestResult.assertContentSize(mainAxisSize: Int, crossAxisSize: Int) {
        if (isVertical) {
            assertThat(contentSize).isEqualTo(IntSize(crossAxisSize, mainAxisSize))
        } else {
            assertThat(contentSize).isEqualTo(IntSize(mainAxisSize, crossAxisSize))
        }
    }

    private fun ScrollNodeTestResult.assertChildConstraints(
        minMainAxis: Int,
        maxMainAxis: Int,
        minCrossAxis: Int,
        maxCrossAxis: Int,
    ) {
        if (isVertical) {
            assertThat(childConstraints.minHeight).isEqualTo(minMainAxis)
            assertThat(childConstraints.maxHeight).isEqualTo(maxMainAxis)
            assertThat(childConstraints.minWidth).isEqualTo(minCrossAxis)
            assertThat(childConstraints.maxWidth).isEqualTo(maxCrossAxis)
        } else {
            assertThat(childConstraints.minWidth).isEqualTo(minMainAxis)
            assertThat(childConstraints.maxWidth).isEqualTo(maxMainAxis)
            assertThat(childConstraints.minHeight).isEqualTo(minCrossAxis)
            assertThat(childConstraints.maxHeight).isEqualTo(maxCrossAxis)
        }
    }
}
