/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.interaction.swipeback

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft

internal class HorizontalScrollSwipeBackInHostingViewTest : HorizontalScrollSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class HorizontalScrollSwipeBackInHostingViewControllerTest : HorizontalScrollSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class HorizontalScrollSwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testEdgeBackSwipeOverHorizontalScrollDoesNotScrollComposeContentLtr() = runUIKitInstrumentedTest {
        var scrollOffset = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            HorizontalScrollBackGestureContent(
                onScrollOffsetChanged = { scrollOffset = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromLeftEdge().hold()
        waitUntil("Back swipe over horizontal scroll content should start") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_LEFT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "Back swipe over horizontal scroll content should report the expected edge"
        )
        assertEquals(
            expected = 0f,
            actual = scrollOffset,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe should not scroll horizontal Compose content"
        )

        backSwipe.up()

        waitUntil("Back swipe over horizontal scroll content should complete") {
            backCompletedCount == 1
        }
    }

    @Test
    fun testEdgeBackSwipeOverHorizontalScrollDoesNotScrollComposeContentRtl() = runUIKitInstrumentedTest {
        var scrollOffset = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            HorizontalScrollBackGestureContent(
                onScrollOffsetChanged = { scrollOffset = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromRightEdge().hold()
        waitUntil("Back swipe over horizontal scroll content should start") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_RIGHT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "Back swipe over horizontal scroll content should report the expected edge"
        )
        assertEquals(
            expected = 0f,
            actual = scrollOffset,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe should not scroll horizontal Compose content"
        )

        backSwipe.up()

        waitUntil("Back swipe over horizontal scroll content should complete") {
            backCompletedCount == 1
        }
    }

    @Test
    fun testInnerSwipeOverHorizontalScrollScrollsComposeContentWithoutStartingBack() = runUIKitInstrumentedTest {
        var scrollOffset = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent {
            HorizontalScrollBackGestureContent(
                onScrollOffsetChanged = { scrollOffset = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        findNodeWithTag(SCROLL_SURFACE).swipeLeft()

        waitUntil("Inner swipe should scroll horizontal Compose content") {
            scrollOffset > 0f
        }

        assertFalse(
            transitionState is InProgress,
            "Inner swipe over horizontal scroll content should not start back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Inner swipe over horizontal scroll content should not complete back navigation"
        )
    }

}

@Composable
private fun HorizontalScrollBackGestureContent(
    onScrollOffsetChanged: (Float) -> Unit,
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit,
    onBackCompletedCountChanged: (Int) -> Unit,
) {
    var scrollOffset by remember { mutableFloatStateOf(0f) }

    onScrollOffsetChanged(scrollOffset)

    BackGestureHost(
        onTransitionStateChanged = onTransitionStateChanged,
        onBackCompletedCountChanged = onBackCompletedCountChanged
    ) {
        val scrollState = rememberScrollState()

        scrollOffset = scrollState.value.toFloat()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag(SCROLL_SURFACE)
                .horizontalScroll(scrollState)
        ) {
            repeat(10) {
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 160.dp)
                        .background(if (it % 2 == 0) Color.Red else Color.Blue)
                )
            }
        }
    }
}

@Composable
private fun BackGestureHost(
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit,
    onBackCompletedCountChanged: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    var backCompletedCount by remember { mutableIntStateOf(0) }
    val navigationEventState = rememberNavigationEventState<NavigationEventInfo>(
        currentInfo = NavigationEventInfo.None,
        backInfo = listOf(NavigationEventInfo.None)
    )

    onTransitionStateChanged(navigationEventState.transitionState)
    onBackCompletedCountChanged(backCompletedCount)

    NavigationBackHandler(
        state = navigationEventState,
        onBackCompleted = {
            backCompletedCount += 1
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

private const val SCROLL_SURFACE = "scrollSurface"
