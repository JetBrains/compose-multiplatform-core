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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.uikit.EndEdgePanGestureBehavior
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft

internal class SwipeBackInHostingViewTest : SwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class SwipeBackInHostingViewControllerTest : SwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class SwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testSwipeBackDoesNotDispatchHorizontalDragToComposeLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        swipeFromLeftEdge().up()

        waitForIdle()

        assertEquals(
            expected = 0f, actual = dragDistance,
            message = "left edge swipe back should not dispatch horizontal drag deltas to Compose in LTR"
        )
    }

    @Test
    fun testSwipeBackDoesNotDispatchHorizontalDragToComposeRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        swipeFromRightEdge().up()

        waitForIdle()

        assertEquals(
            expected = 0f, actual = dragDistance,
            message = "right edge swipe back should not dispatch horizontal drag deltas to Compose in RTL"
        )
    }

    @Test
    fun testBackSwipeCompletesLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromLeftEdge().hold()

        waitUntil("back swipe should be in progress") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_LEFT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "left edge swipe back should report EDGE_LEFT in LTR"
        )

        swipeBack.up()

        waitUntil("left edge back swipe should complete in LTR") {
            backCompletedCount == 1
        }
    }

    @Test
    fun testSwipeBackCompletesRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromRightEdge().hold()

        assertTrue(transitionState is InProgress, message = "right edge swipe back should be in progress in RTL")

        assertEquals(
            expected = NavigationEvent.EDGE_RIGHT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "right edge swipe back should report EDGE_RIGHT in RTL"
        )

        swipeBack.up()

        waitForIdle()

        assertEquals(1, backCompletedCount, message = "right edge swipe back should complete in RTL")
    }

    @Test
    fun testSwipeFromRightEdgeNotCompletesSwipeBackInLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromRightEdge().hold()

        assertFalse(transitionState is InProgress, message = "right edge swipe back should not be in progress in LTR")

        swipeBack.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "right edge swipe back should not complete in LTR")
    }

    @Test
    fun testSwipeFromLeftEdgeNotCompletesSwipeBackInRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromLeftEdge().hold()

        assertFalse(transitionState is InProgress, message = "left edge swipe back should not be in progress in RTL")

        swipeBack.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "left edge swipe back should not complete in RTL")
    }

    @Test
    fun testSwipeFromRightEdgeDispatchesHorizontalDragToComposeInLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        swipeFromRightEdge().hold()

        waitForIdle()

        assertTrue(dragDistance < 0f, message = "right edge swipe should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeFromLeftEdgeDispatchesHorizontalDragToComposeInRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        swipeFromLeftEdge().hold()

        waitForIdle()

        assertTrue(dragDistance > 0f, message = "left edge swipe should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeLeftDispatchesHorizontalDragInLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        findNodeWithTag(DRAG_SURFACE).swipeLeft().up()

        waitForIdle()

        assertTrue(dragDistance < 0f, message = "swipe left should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeRightDispatchesHorizontalDragInLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        findNodeWithTag(DRAG_SURFACE).swipeRight().up()

        waitForIdle()

        assertTrue(dragDistance > 0f, message = "swipe right should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeLeftDispatchesHorizontalDragInRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        findNodeWithTag(DRAG_SURFACE).swipeLeft().up()

        waitForIdle()

        assertTrue(dragDistance < 0f, message = "swipe left should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeRightDispatchesHorizontalDragInRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onDragDistanceChanged = { dragDistance = it }
            )
        }

        findNodeWithTag(DRAG_SURFACE).swipeRight().up()

        waitForIdle()

        assertTrue(dragDistance > 0f, message = "swipe right should dispatch horizontal drag deltas to Compose")
    }

    @Test
    fun testSwipeRightNotCompletesSwipeBackInLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeRight = findNodeWithTag(DRAG_SURFACE).swipeRight().hold()

        assertFalse(transitionState is InProgress, message = "swipe right should not be in progress in LTR")

        swipeRight.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "swipe right should not complete in LTR")
    }

    @Test
    fun testSwipeRightNotCompletesSwipeBackInRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeRight = findNodeWithTag(DRAG_SURFACE).swipeRight().hold()

        assertFalse(transitionState is InProgress, message = "swipe right should not be in progress in RTL")

        swipeRight.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "swipe right should not complete in RTL")
    }

    @Test
    fun testSwipeLeftNotCompletesSwipeBackInLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeRight = findNodeWithTag(DRAG_SURFACE).swipeLeft().hold()

        assertFalse(transitionState is InProgress, message = "swipe left should not be in progress in LTR")

        swipeRight.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "swipe left should not complete in LTR")
    }

    @Test
    fun testSwipeLeftNotCompletesSwipeBackInRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeRight = findNodeWithTag(DRAG_SURFACE).swipeLeft().hold()

        assertFalse(transitionState is InProgress, message = "swipe left should not be in progress in RTL")

        swipeRight.up()

        waitForIdle()

        assertEquals(0, backCompletedCount, message = "swipe left should not complete in RTL")
    }

    @Test
    fun testSwipeFromLeftEdgeCompletesSwipeBackInRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(
            configure = { endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Back },
            layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft
        ) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromLeftEdge().hold()

        assertTrue(transitionState is InProgress, message = "left edge swipe back should be in progress in RTL")

        swipeBack.up()

        waitForIdle()

        assertEquals(
            1, backCompletedCount,
            message = "left edge swipe back should complete in RTL with EndEdgePanGestureBehavior.Back"
        )
    }

    @Test
    fun testSwipeFromRightEdgeCompletesSwipeBackInLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(
            configure = { endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Back },
            layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight
        ) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val swipeBack = swipeFromRightEdge().hold()

        assertTrue(transitionState is InProgress, message = "right edge swipe back should be in progress in LTR")

        swipeBack.up()

        waitForIdle()

        assertEquals(
            1, backCompletedCount,
            message = "right edge swipe back should complete in LTR with EndEdgePanGestureBehavior.Back"
        )
    }

    @Test
    fun testSwipeFromRightAndLeftEdgeCompletesSwipeBackInLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(
            configure = { endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Back },
            layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight
        ) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        swipeFromRightEdge().up()

        waitForIdle()

        assertEquals(
            1, backCompletedCount,
            message = "right edge swipe back should complete in LTR with EndEdgePanGestureBehavior.Back"
        )

        assertTrue(transitionState is NavigationEventTransitionState.Idle, message = "swipe back should be idle")

        swipeFromLeftEdge().up()

        waitForIdle()

        assertEquals(
             2, backCompletedCount,
            message = "left edge swipe back should complete in LTR with EndEdgePanGestureBehavior.Back"
        )
    }

    @Test
    fun testSwipeFromRightAndLeftEdgeCompletesSwipeBackInRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(
            configure = { endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Back },
            layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft
        ) {
            SwipeBackTestContent(
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        swipeFromRightEdge().up()

        waitForIdle()

        assertEquals(
            1, backCompletedCount,
            message = "right edge swipe back should complete in RTL with EndEdgePanGestureBehavior.Back"
        )

        assertTrue(transitionState is NavigationEventTransitionState.Idle, message = "swipe back should be idle")

        swipeFromLeftEdge().up()

        waitForIdle()

        assertEquals(
            2, backCompletedCount,
            message = "left edge swipe back should complete in RTL with EndEdgePanGestureBehavior.Back"
        )
    }

    @Test
    fun testChangingLtrToRtlChangesSwipeBackEdge() = runUIKitInstrumentedTest {
        var backCompletedCount = -1
        var composeLayoutDirection: LayoutDirection? = null

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            val currentLayoutDirection = LocalLayoutDirection.current

            SideEffect {
                composeLayoutDirection = currentLayoutDirection
            }

            SwipeBackTestContent(
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        swipeFromLeftEdge().up()

        waitForIdle()

        assertEquals(
             1, backCompletedCount,
            message = "left edge swipe back should complete in LTR"
        )

        setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft)

        waitUntil { composeLayoutDirection == LayoutDirection.Rtl }

        swipeFromRightEdge().up()

        waitForIdle()

        assertEquals(
            2, backCompletedCount,
            message = "right edge swipe back should complete in RTL"
        )
    }

    @Test
    fun testChangingRtlToLtrChangesSwipeBackEdge() = runUIKitInstrumentedTest {
        var backCompletedCount = -1
        var composeLayoutDirection: LayoutDirection? = null

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            val currentLayoutDirection = LocalLayoutDirection.current

            SideEffect {
                composeLayoutDirection = currentLayoutDirection
            }

            SwipeBackTestContent(
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        swipeFromRightEdge().up()

        waitForIdle()

        assertEquals(
            1, backCompletedCount,
            message = "right edge swipe back should complete in RTL"
        )

        setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight)

        waitUntil { composeLayoutDirection == LayoutDirection.Ltr }

        swipeFromLeftEdge().up()

        waitForIdle()

        assertEquals(
            2, backCompletedCount,
            message = "left edge swipe back should complete in LTR"
        )
    }

}

@Composable
internal fun SwipeBackTestContent(
    onDragDistanceChanged: (Float) -> Unit = {},
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit = {},
    onBackCompletedCountChanged: (Int) -> Unit = {},
    onComposeLayoutDirectionChanged: (LayoutDirection) -> Unit = {}
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var backCompletedCount by remember { mutableIntStateOf(0) }
    val navigationEventState = rememberNavigationEventState(
        currentInfo = NavigationEventInfo.None,
        backInfo = listOf<NavigationEventInfo>(NavigationEventInfo.None)
    )

    val composeLayoutDirection = LocalLayoutDirection.current
    SideEffect {
        onComposeLayoutDirectionChanged(composeLayoutDirection)
    }

    onDragDistanceChanged(dragDistance)
    onTransitionStateChanged(navigationEventState.transitionState)
    onBackCompletedCountChanged(backCompletedCount)

    NavigationBackHandler(
        state = navigationEventState,
        onBackCompleted = {
            backCompletedCount += 1
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DRAG_SURFACE)
            .draggable(
                state = rememberDraggableState { delta ->
                    dragDistance += delta
                },
                orientation = Orientation.Horizontal,
            )
    )
}

private const val DRAG_SURFACE = "dragSurface"
