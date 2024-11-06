/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.compose.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedScope
import androidx.wear.compose.foundation.angularSizeDp
import androidx.wear.compose.foundation.background
import androidx.wear.compose.foundation.curvedBox
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.lazy.inverseLerp
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.radialSize
import androidx.wear.compose.foundation.size
import androidx.wear.compose.foundation.weight
import androidx.wear.compose.material3.tokens.ColorSchemeKeyTokens
import androidx.wear.compose.materialcore.BoundsLimiter
import androidx.wear.compose.materialcore.isLayoutDirectionRtl
import androidx.wear.compose.materialcore.isRoundDevice
import kotlin.math.roundToInt

/**
 * Horizontal page indicator for use with [HorizontalPager], representing the currently active page
 * and the approximate number of pages. Pages are indicated as a Circle shape. The indicator shows
 * up to six pages individually - if there are more than six pages, [HorizontalPageIndicator] shows
 * a smaller indicator to the left and/or right to indicate that more pages are available.
 *
 * This is a full screen component and will occupy the whole screen. However it's not actionable, so
 * it's not expected to interfere with anything on the screen.
 *
 * Here's how different positions 0..10 might be visually represented: "X" is selected item, "O" and
 * "o" full and half size items respectively.
 *
 * O X O O O o - 2nd position out of 10. There are no more items on the left but more on the right.
 *
 * o O O O X o - current page could be 6, 7 or 8 out of 10, as there are more potential pages on the
 * left and on the right.
 *
 * o O O O X O - current page is 9 out of 10, as there no more items on the right
 *
 * [HorizontalPageIndicator] can be linear or curved, depending on the screen shape of the device -
 * for circular screens it will be curved, whilst for square screens it will be linear.
 *
 * Example usage with [HorizontalPager]:
 *
 * @sample androidx.wear.compose.material3.samples.HorizontalPageIndicatorWithPagerSample
 * @param pagerState State of the [HorizontalPager] used to control this indicator
 * @param modifier Modifier to be applied to the [HorizontalPageIndicator]
 * @param selectedColor The color which will be used for a selected indicator item.
 * @param unselectedColor The color which will be used for an unselected indicator item.
 * @param backgroundColor The color which will be used for an indicator background.
 */
@Composable
fun HorizontalPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    selectedColor: Color = PageIndicatorDefaults.selectedColor,
    unselectedColor: Color = PageIndicatorDefaults.unselectedColor,
    backgroundColor: Color = PageIndicatorDefaults.backgroundColor,
) {
    PageIndicatorImpl(
        pagerState = pagerState,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        indicatorSize = PageIndicatorItemSize,
        spacing = PageIndicatorSpacing,
        isHorizontal = true,
    )
}

/**
 * Vertical page indicator for use with [VerticalPager], representing the currently active page and
 * the approximate number of pages. Pages are indicated as a Circle shape. The indicator shows up to
 * six pages individually - if there are more than six pages, [VerticalPageIndicator] shows a
 * smaller indicator to the top and/or bottom to indicate that more pages are available.
 *
 * This is a full screen component and will occupy the whole screen. However it's not actionable, so
 * it's not expected to interfere with anything on the screen.
 *
 * [VerticalPageIndicator] can be linear or curved, depending on the screen shape of the device -
 * for circular screens it will be curved, whilst for square screens it will be linear.
 *
 * Example usage with [VerticalPager]:
 *
 * @sample androidx.wear.compose.material3.samples.VerticalPageIndicatorWithPagerSample
 * @param pagerState State of the [VerticalPager] used to control this indicator
 * @param modifier Modifier to be applied to the [VerticalPageIndicator]
 * @param selectedColor The color which will be used for a selected indicator item.
 * @param unselectedColor The color which will be used for an unselected indicator item.
 * @param backgroundColor The color which will be used for an indicator background.
 */
@Composable
fun VerticalPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    selectedColor: Color = PageIndicatorDefaults.selectedColor,
    unselectedColor: Color = PageIndicatorDefaults.unselectedColor,
    backgroundColor: Color = PageIndicatorDefaults.backgroundColor,
) {
    PageIndicatorImpl(
        pagerState = pagerState,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        indicatorSize = PageIndicatorItemSize,
        spacing = PageIndicatorSpacing,
        isHorizontal = false,
    )
}

/** Contains the default values used by [HorizontalPageIndicator] and [VerticalPageIndicator] */
object PageIndicatorDefaults {

    /**
     * The recommended color to use for the selected indicator item in [VerticalPageIndicator] and
     * [HorizontalPageIndicator].
     */
    val selectedColor: Color
        @ReadOnlyComposable @Composable get() = ColorSchemeKeyTokens.OnBackground.value

    /**
     * The recommended color to use for the unselected indicator item in [VerticalPageIndicator] and
     * [HorizontalPageIndicator].
     */
    val unselectedColor: Color
        @ReadOnlyComposable
        @Composable
        get() = ColorSchemeKeyTokens.OnBackground.value.copy(alpha = 0.3f)

    /**
     * The recommended color to use for the background in [VerticalPageIndicator] and
     * [HorizontalPageIndicator].
     */
    val backgroundColor: Color
        @ReadOnlyComposable
        @Composable
        get() = ColorSchemeKeyTokens.Background.value.copy(alpha = 0.85f)
}

@Composable
internal fun PageIndicatorImpl(
    pagerState: PagerState,
    selectedColor: Color,
    unselectedColor: Color,
    backgroundColor: Color,
    modifier: Modifier,
    indicatorSize: Dp,
    spacing: Dp,
    isHorizontal: Boolean,
) {
    val isScreenRound = isRoundDevice()
    val layoutDirection = LocalLayoutDirection.current
    val edgePadding = PaddingDefaults.edgePadding

    // Converting offsetFraction into range 0..1f
    val currentPageOffsetWithFraction =
        pagerState.currentPage + pagerState.currentPageOffsetFraction

    val isLastPage =
        currentPageOffsetWithFraction.equalsWithTolerance(
            number = pagerState.pageCount - 1f,
            tolerance = 0.001f
        )

    // If it's the last page, then we decrease its index by 1 and put a 1f to the offset
    val selectedPage: Int =
        if (isLastPage) currentPageOffsetWithFraction.toInt() - 1
        else currentPageOffsetWithFraction.toInt()
    val offset = currentPageOffsetWithFraction - selectedPage

    val pagesOnScreen = Integer.min(MaxNumberOfIndicators, pagerState.pageCount)
    val pagesState =
        remember(pagerState.pageCount) {
            PagesState(
                totalPages = pagerState.pageCount,
                pagesOnScreen = pagesOnScreen,
                smallIndicatorSizeFraction = smallIndicatorSizeFraction,
                shrinkThresholdStart = calculateShrinkThresholdStart(spacing, indicatorSize),
                shrinkThresholdEnd = calculateShrinkThresholdEnd(spacing, indicatorSize)
            )
        }

    if (pagesState.totalPages > 1) {
        pagesState.recalculateState(selectedPage, offset)
    }

    val spacerSize = indicatorSize + spacing

    if (isScreenRound) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        val boundsSize: Density.() -> IntSize = {
            val width = (spacerSize.toPx() * pagesOnScreen).roundToInt()
            val height = (indicatorSize * 2).roundToPx().coerceAtLeast(0)
            val size =
                IntSize(
                    width = if (isHorizontal) width else height,
                    height = if (isHorizontal) height else width
                )
            size
        }

        val boundsOffset: Density.() -> IntOffset = {
            val measuredSize = boundsSize()
            if (isHorizontal) {
                // Offset here is the distance between top left corner of the outer container to
                // the top left corner of the indicator. Its placement should look similar to
                // Alignment.BottomCenter.
                IntOffset(
                    x = (containerSize.width - measuredSize.width) / 2 - edgePadding.roundToPx(),
                    y = containerSize.height - measuredSize.height - edgePadding.roundToPx() * 2,
                )
            } else {
                // Offset here is the distance between top left corner of the outer container to
                // the top left corner of the indicator. Its placement should look similar to
                // Alignment.CenterEnd.
                IntOffset(
                    x =
                        if (layoutDirection == LayoutDirection.Ltr) {
                            containerSize.width - measuredSize.width - edgePadding.roundToPx() * 2
                        } else edgePadding.roundToPx(),
                    y = (containerSize.height - measuredSize.height) / 2 - edgePadding.roundToPx(),
                )
            }
        }
        // As we use an extra spacers to the start and end of horizontal indicator ( and higher and
        // lower for vertical), we have to set their size in angular padding to compensate for that.
        val angularPadding = -(spacing + indicatorSize)
        BoundsLimiter(
            offset = boundsOffset,
            size = boundsSize,
            modifier = modifier.padding(edgePadding),
            onSizeChanged = { containerSize = it }
        ) {
            if (pagesState.totalPages == 1) {
                SingleDotCurvedPageIndicator(
                    isHorizontal = isHorizontal,
                    indicatorSize = indicatorSize,
                    layoutDirection = layoutDirection,
                    selectedColor = selectedColor,
                    backgroundColor = backgroundColor,
                )
            } else {
                CurvedPageIndicator(
                    visibleDotIndex = pagesState.visibleDotIndex,
                    pagesOnScreen = pagesOnScreen,
                    indicator = { page ->
                        curvedIndicator(
                            page = page,
                            size = indicatorSize,
                            unselectedColor = unselectedColor,
                            pagesState = pagesState
                        )
                    },
                    spacer = { spacerIndex ->
                        curvedSpacer(spacerSize * pagesState.spacersSizeRatio[spacerIndex])
                    },
                    selectedIndicator = {
                        curvedSelectedIndicator(
                            indicatorSize = indicatorSize,
                            spacing = spacing,
                            selectedColor = selectedColor,
                            progress = offset
                        )
                    },
                    angularPadding = angularPadding,
                    isHorizontal = isHorizontal,
                    layoutDirection = layoutDirection,
                    backgroundColor = backgroundColor
                )
            }
        }
    } else {
        LinearPageIndicator(
            modifier = modifier.padding(vertical = edgePadding),
            visibleDotIndex = pagesState.visibleDotIndex,
            pagesOnScreen = pagesOnScreen,
            indicator = { page ->
                LinearIndicator(
                    page = page,
                    pagesState = pagesState,
                    unselectedColor = unselectedColor,
                    indicatorSize = indicatorSize,
                    spacing = spacing,
                )
            },
            selectedIndicator = {
                LinearSelectedIndicator(
                    indicatorSize = indicatorSize,
                    spacing = spacing,
                    selectedColor = selectedColor,
                    progress = offset
                )
            },
            spacerStart = { LinearSpacer(spacerSize * pagesState.spacersSizeRatio.first()) },
            spacerEnd = { LinearSpacer(spacerSize * pagesState.spacersSizeRatio.last()) },
            isHorizontal = isHorizontal,
            layoutDirection = layoutDirection,
            background = {
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .size(
                                width =
                                    (pagesOnScreen * indicatorSize.value +
                                            (pagesOnScreen - 1) * spacing.value)
                                        .dp + BackgroundRadius * 2,
                                height = indicatorSize + BackgroundRadius * 2
                            )
                            .background(color = backgroundColor, shape = RoundedCornerShape(50.dp))
                )
            },
        )
    }
}

// TODO(b/369535289) Fix a visual issue with linear indicator when there are more than 6 pages.
@Composable
private fun LinearPageIndicator(
    modifier: Modifier,
    visibleDotIndex: Int,
    pagesOnScreen: Int,
    indicator: @Composable (Int) -> Unit,
    selectedIndicator: @Composable () -> Unit,
    spacerStart: @Composable () -> Unit,
    spacerEnd: @Composable () -> Unit,
    isHorizontal: Boolean,
    layoutDirection: LayoutDirection,
    background: @Composable BoxScope.() -> Unit
) {
    val width = LocalConfiguration.current.screenWidthDp
    val height = LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier =
                modifier.let {
                    if (isHorizontal) it.size(width = width.dp, height = height.dp)
                    // Flip width and height so that the indicator will fit into rectangular screen,
                    // rotate it -90 degrees, and flip it vertically
                    else
                        it.size(width = height.dp, height = width.dp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                rotationZ =
                                    if (layoutDirection == LayoutDirection.Ltr) -90f else 90f
                                scaleX = -1f
                                scaleY = 1f
                            }
                }
        ) {
            background()
            Row(
                modifier =
                    Modifier.padding(bottom = BackgroundRadius).align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                // drawing 1 extra spacer for transition
                spacerStart()
                for (page in 0 until visibleDotIndex) {
                    indicator(page)
                }
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        indicator(visibleDotIndex)
                        indicator(visibleDotIndex + 1)
                    }
                    Box { selectedIndicator() }
                }
                for (page in visibleDotIndex + 2..pagesOnScreen) {
                    indicator(page)
                }
                spacerEnd()
            }
        }
    }
}

@Composable
private fun LinearSelectedIndicator(
    indicatorSize: Dp,
    spacing: Dp,
    selectedColor: Color,
    progress: Float
) {
    val horizontalPadding = spacing / 2
    val isRtl = isLayoutDirectionRtl()
    Spacer(
        modifier =
            Modifier.drawWithCache {
                // Adding 2px to fully cover edges of non-selected indicators
                val strokeWidth = indicatorSize.toPx() + 2
                val startX = horizontalPadding.toPx() + strokeWidth / 2
                val endX = this.size.width - horizontalPadding.toPx() - strokeWidth / 2
                val drawWidth = endX - startX

                val startSpacerWeight = (progress * 2 - 1).coerceAtLeast(0f)
                val endSpacerWeight = (1 - progress * 2).coerceAtLeast(0f)

                // Adding +1 or -1 for cases when start and end have the same coordinates -
                // otherwise on APIs <= 26 line will not be drawn
                val additionalPixel = if (isRtl) -1 else 1

                val start =
                    Offset(
                        startX +
                            drawWidth * (if (isRtl) startSpacerWeight else endSpacerWeight) +
                            additionalPixel,
                        this.size.height / 2
                    )
                val end =
                    Offset(
                        endX - drawWidth * (if (isRtl) endSpacerWeight else startSpacerWeight),
                        this.size.height / 2
                    )
                onDrawBehind {
                    drawLine(
                        color = selectedColor,
                        start = start,
                        end = end,
                        cap = StrokeCap.Round,
                        strokeWidth = strokeWidth
                    )
                }
            }
    )
}

@Composable
private fun LinearIndicator(
    page: Int,
    pagesState: PagesState,
    unselectedColor: Color,
    indicatorSize: Dp,
    spacing: Dp,
) {
    Spacer(
        modifier =
            Modifier.padding(horizontal = spacing / 2).size(indicatorSize).drawWithCache {
                val strokeWidth = indicatorSize.toPx() * pagesState.indicatorsSizeRatio[page]
                val start = Offset(strokeWidth / 2 + 1, this.size.height / 2)
                val end = Offset(strokeWidth / 2, this.size.height / 2)
                onDrawBehind {
                    drawLine(
                        color = unselectedColor,
                        start = start,
                        end = end,
                        cap = StrokeCap.Round,
                        alpha = pagesState.indicatorsAlpha[page],
                        strokeWidth = strokeWidth
                    )
                }
            }
    )
}

@Composable
private fun LinearSpacer(leftSpacerSize: Dp) {
    Spacer(Modifier.size(leftSpacerSize, 0.dp))
}

@Composable
private fun CurvedPageIndicator(
    visibleDotIndex: Int,
    pagesOnScreen: Int,
    indicator: CurvedScope.(Int) -> Unit,
    spacer: CurvedScope.(Int) -> Unit,
    selectedIndicator: CurvedScope.() -> Unit,
    isHorizontal: Boolean,
    layoutDirection: LayoutDirection,
    angularPadding: Dp,
    backgroundColor: Color,
) {
    val anchor =
        if (isHorizontal) HorizontalPagerAnchor
        else {
            if (layoutDirection == LayoutDirection.Ltr) VerticalPagerAnchor
            else VerticalPagerRtlAnchor
        }
    val angularDirection =
        if (isHorizontal) CurvedDirection.Angular.Reversed else CurvedDirection.Angular.Normal

    CurvedLayout(modifier = Modifier, anchor = anchor, angularDirection = angularDirection) {
        curvedRow(
            modifier =
                CurvedModifier.background(backgroundColor, cap = StrokeCap.Round)
                    .padding(radial = BackgroundRadius, angular = angularPadding)
        ) {
            curvedRow(radialAlignment = CurvedAlignment.Radial.Center) {
                for (page in 0 until visibleDotIndex) {
                    spacer(page)
                    indicator(page)
                }
                curvedBox(
                    radialAlignment = CurvedAlignment.Radial.Center,
                    angularAlignment = CurvedAlignment.Angular.Center,
                ) {
                    curvedRow(radialAlignment = CurvedAlignment.Radial.Center) {
                        spacer(visibleDotIndex)
                        indicator(visibleDotIndex)
                        spacer(visibleDotIndex + 1)
                        indicator(visibleDotIndex + 1)
                        spacer(visibleDotIndex + 2)
                    }
                    selectedIndicator()
                }
                for (page in visibleDotIndex + 2..pagesOnScreen) {
                    indicator(page)
                    spacer(page + 1)
                }
            }
        }
    }
}

@Composable
private fun SingleDotCurvedPageIndicator(
    isHorizontal: Boolean,
    indicatorSize: Dp,
    layoutDirection: LayoutDirection,
    selectedColor: Color,
    backgroundColor: Color,
) {
    val anchor =
        if (isHorizontal) HorizontalPagerAnchor
        else {
            if (layoutDirection == LayoutDirection.Ltr) VerticalPagerAnchor
            else VerticalPagerRtlAnchor
        }
    val angularDirection =
        if (isHorizontal) CurvedDirection.Angular.Reversed else CurvedDirection.Angular.Normal

    CurvedLayout(modifier = Modifier, anchor = anchor, angularDirection = angularDirection) {
        curvedRow(
            modifier =
                CurvedModifier.background(backgroundColor, cap = StrokeCap.Round)
                    .padding(radial = BackgroundRadius)
        ) {
            curvedBox(
                modifier =
                    CurvedModifier
                        // Ideally we want sweepDegrees to be = 0f, because the circular shape is
                        // drawn
                        // by the Round StrokeCap.
                        // But it can't have 0f value due to limitations of underlying Canvas.
                        // Values below 0.2f also give some artifacts b/291753164
                        .size(0.2f, indicatorSize)
                        .background(color = selectedColor, cap = StrokeCap.Round)
            ) {}
        }
    }
}

private fun CurvedScope.curvedSelectedIndicator(
    indicatorSize: Dp,
    spacing: Dp,
    selectedColor: Color,
    progress: Float
) {

    val startSpacerWeight = (1 - progress * 2).coerceAtLeast(0f)
    val endSpacerWeight = (progress * 2 - 1).coerceAtLeast(0f)
    val blurbWeight = (1 - startSpacerWeight - endSpacerWeight).coerceAtLeast(0.01f)

    // Add 0.5dp to cover the sweepDegrees of unselected indicators
    curvedRow(CurvedModifier.angularSizeDp(spacing + indicatorSize + 0.5.dp)) {
        if (endSpacerWeight > 0f) {
            curvedRow(CurvedModifier.weight(endSpacerWeight)) {}
        }
        curvedRow(
            CurvedModifier.background(selectedColor, cap = StrokeCap.Round)
                .weight(blurbWeight)
                // Adding 0.3dp to fully cover edges of non-selected indicators
                .radialSize(indicatorSize + 0.3.dp)
        ) {}
        if (startSpacerWeight > 0f) {
            curvedRow(CurvedModifier.weight(startSpacerWeight)) {}
        }
    }
}

private fun CurvedScope.curvedIndicator(
    page: Int,
    unselectedColor: Color,
    pagesState: PagesState,
    size: Dp
) {
    curvedBox(
        CurvedModifier
            // Ideally we want sweepDegrees to be = 0f, because the circular shape is drawn
            // by the Round StrokeCap.
            // But it can't have 0f value due to limitations of underlying Canvas.
            // Values below 0.2f also give some artifacts b/291753164
            .size(0.2f, size * pagesState.indicatorsSizeRatio[page])
            .background(
                color =
                    unselectedColor.copy(
                        alpha = unselectedColor.alpha * pagesState.indicatorsAlpha[page]
                    ),
                cap = StrokeCap.Round
            )
    ) {}
}

private fun CurvedScope.curvedSpacer(size: Dp) {
    curvedBox(CurvedModifier.angularSizeDp(size).radialSize(0.dp)) {}
}

/**
 * Represents an internal state of pageIndicator. This state is responsible for keeping and
 * recalculating alpha and size parameters of each indicator, spacers between them, and selected
 * indicators.
 */
private class PagesState(
    val totalPages: Int,
    val pagesOnScreen: Int,
    val smallIndicatorSizeFraction: Float,
    val shrinkThresholdStart: Float,
    val shrinkThresholdEnd: Float
) {
    private val dotsCount = pagesOnScreen + 1
    private val spacersCount = pagesOnScreen + 2

    private var smoothProgress = 0f
    // An offset in pages, basically meaning how many pages are hidden to the left.
    private var hiddenPagesToTheLeft = 0

    // Current visible position on the screen.
    var visibleDotIndex = 0
        private set

    // Sizes and alphas of all indicators on the screen. These parameters depend on the currently
    // selected page, and how many pages are at the front and at the back of the selected page.
    val indicatorsAlpha = FloatArray(dotsCount)
    val indicatorsSizeRatio = FloatArray(dotsCount)

    // Sizes of the spacers between dots
    val spacersSizeRatio = FloatArray(spacersCount)

    // Main function responsible for recalculation of all parameters based on [selectedPage] and
    // [offset] parameters
    fun recalculateState(selectedPage: Int, offset: Float) {
        val pageWithOffset = selectedPage + offset
        // Calculating offsetInPages relating to the [selectedPage].

        // For example, for [selectedPage] = 4 we will see this picture :
        // O O O O X o. [offsetInPages] will be 0.
        // But when [selectedPage] will be incremented to 5, it will be seen as
        // o O O O X o, with [offsetInPages] = 1
        if (selectedPage > hiddenPagesToTheLeft + pagesOnScreen - 2) {
            // Set an offset as a difference between current page and pages on the screen,
            // except if this is not the last page - then offsetInPages is not changed
            hiddenPagesToTheLeft =
                (selectedPage - (pagesOnScreen - 2)).coerceAtMost(totalPages - pagesOnScreen)
        } else if (pageWithOffset <= hiddenPagesToTheLeft) {
            hiddenPagesToTheLeft = (selectedPage - 1).coerceAtLeast(0)
        }

        // Condition for scrolling to the right. A smooth scroll to the right is only triggered
        // when we have more than 2 pages to the right, and currently we're on the right edge.
        // For example -> o O O O X o -> a small "o" shows that there're more pages to the right
        val scrolledToTheRight =
            pageWithOffset > hiddenPagesToTheLeft + pagesOnScreen - 2 &&
                pageWithOffset < totalPages - 2

        // Condition for scrolling to the left. A smooth scroll to the left is only triggered
        // when we have more than 2 pages to the left, and currently we're on the left edge.
        // For example -> o X O O O o -> a small "o" shows that there're more pages to the left
        val scrolledToTheLeft = pageWithOffset > 1 && pageWithOffset < hiddenPagesToTheLeft + 1

        smoothProgress = if (scrolledToTheLeft || scrolledToTheRight) offset else 0f

        // Calculating alphas of indicators
        for (i in indicatorsAlpha.indices) {
            indicatorsAlpha[i] =
                when (i) {
                    0 -> 1 - smoothProgress
                    dotsCount - 1 -> smoothProgress
                    else -> 1f
                }
        }

        // Calculating spacer sizes between indicators
        for (i in spacersSizeRatio.indices) {
            spacersSizeRatio[i] =
                when (i) {
                    0 -> 1 - smoothProgress
                    spacersCount - 1 -> smoothProgress
                    else -> 1f
                }
        }

        // Calculating indicator sizes
        for (i in indicatorsSizeRatio.indices) {
            indicatorsSizeRatio[i] =
                when (i) {
                    // Depending on offsetInPages we'll either show a shrinked first indicator, or
                    // full-size indicator
                    0 -> {
                        if (
                            hiddenPagesToTheLeft == 0 ||
                                hiddenPagesToTheLeft == 1 && scrolledToTheLeft
                        ) {
                            1 - smoothProgress
                        } else {
                            smallIndicatorSizeFraction * (1 - smoothProgress)
                        }
                    }
                    1 -> 1 - (1 - smallIndicatorSizeFraction) * smoothProgress
                    dotsCount - 2 -> {
                        if (scrolledToTheRight || scrolledToTheLeft) {
                            lerp(smallIndicatorSizeFraction, 1f, smoothProgress)
                        } else if (hiddenPagesToTheLeft < totalPages - pagesOnScreen)
                            smallIndicatorSizeFraction
                        else 1f
                    }
                    // Depending on offsetInPages and other parameters,the last indicator will be
                    // a fraction of a shrinked or full-size indicator.
                    dotsCount - 1 -> {
                        if (
                            hiddenPagesToTheLeft == totalPages - pagesOnScreen - 1 &&
                                scrolledToTheRight ||
                                hiddenPagesToTheLeft == totalPages - pagesOnScreen &&
                                    scrolledToTheLeft
                        ) {
                            smoothProgress
                        } else {
                            smallIndicatorSizeFraction * smoothProgress
                        }
                    }
                    else -> 1f
                }
        }

        // A visibleDot represents a currently selected page on the screen
        // As we scroll to the left, we add an invisible indicator to the left, shifting all other
        // indicators to the right. The shift is only possible when a visibleDot = 1,
        // thus we have to leave it at 1 as we always add a positive offset
        visibleDotIndex =
            (if (scrolledToTheLeft) 1 else selectedPage - hiddenPagesToTheLeft).coerceAtLeast(0)

        calculateAdjacentDotParameters(
            shrinkThresholdStart,
            shrinkThresholdEnd,
            visibleDotIndex,
            offset
        )
    }

    /**
     * This function calculates a size and alpha parameters of adjacent indicators to selected
     * indicator. It also modifies spacer sizes for properly placing adjacent indicators.
     */
    private fun calculateAdjacentDotParameters(
        shrinkThresholdStart: Float,
        shrinkThresholdEnd: Float,
        visibleDotIndex: Int,
        offset: Float
    ) {
        val shrinkFractionPrev =
            inverseLerp(1 - shrinkThresholdStart, 1 - shrinkThresholdEnd, offset)
        val shrinkFractionNext = inverseLerp(shrinkThresholdStart, shrinkThresholdEnd, offset)

        // We change the size of the current and next visible indicator.
        indicatorsSizeRatio[visibleDotIndex] *= (1 - shrinkFractionPrev)
        indicatorsSizeRatio[visibleDotIndex + 1] *= (1 - shrinkFractionNext)

        // We have one more spacer than indicators, so spacers[visibleDotIndex] represents a spacer
        // before selected indicator, and spacers[visibleDotIndex + 2] after selected indicator
        spacersSizeRatio[visibleDotIndex] = 1 - shrinkFractionPrev / 3
        spacersSizeRatio[visibleDotIndex + 1] = 1 + shrinkFractionNext / 3 + shrinkFractionPrev / 3
        spacersSizeRatio[visibleDotIndex + 2] = 1 - shrinkFractionNext / 3

        indicatorsAlpha[visibleDotIndex] *=
            inverseLerp(0f, 0.5f, indicatorsSizeRatio[visibleDotIndex])
        indicatorsAlpha[visibleDotIndex + 1] *=
            inverseLerp(0f, 0.5f, indicatorsSizeRatio[visibleDotIndex + 1])
    }
}

private fun calculateShrinkThresholdStart(spacing: Dp, indicatorSize: Dp): Float =
    spacing / (spacing + indicatorSize) / 4

private fun calculateShrinkThresholdEnd(spacing: Dp, indicatorSize: Dp): Float =
    (spacing / 2 + indicatorSize) / (spacing + indicatorSize) / 2

private const val smallIndicatorSizeFraction = 0.66f
private const val MaxNumberOfIndicators = 6

// 0 degrees equals to 3 o'clock position, at the right of the screen
private val VerticalPagerAnchor = 0f
// 180 degrees equals to 9 o'clock position, at the left of the screen
private val VerticalPagerRtlAnchor = 180f
// 90 degrees equals to 6 o'clock position, at the bottom of the screen
private val HorizontalPagerAnchor = 90f
/** The default size of the indicator */
internal val PageIndicatorItemSize = 6.dp
/** The default spacing between the indicators */
internal val PageIndicatorSpacing = 4.dp
internal val BackgroundRadius = 3.dp
