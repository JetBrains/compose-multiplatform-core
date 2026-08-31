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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

internal class PopupSwipeBackInHostingViewTest : PopupSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class PopupSwipeBackInHostingViewControllerTest : PopupSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class PopupSwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testEdgeBackSwipeOverPopupDoesNotDispatchHorizontalDragToComposeLtr() = runComposeContainerTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            PopupBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromLeftEdge().hold()
        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Edge swipe over Popup should not start root back navigation"
        )
        assertEquals(
            expected = 0f,
            actual = dragDistance,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe over Popup should not dispatch horizontal drag deltas to Compose"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Back gesture over Popup should not complete before release"
        )

        backSwipe.up()
        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Releasing edge swipe over Popup should still not start root back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Edge swipe over Popup should not complete root back navigation"
        )
    }

    @Test
    fun testEdgeBackSwipeOverPopupDoesNotDispatchHorizontalDragToComposeRtl() = runComposeContainerTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            PopupBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromRightEdge().hold()
        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Edge swipe over Popup should not start root back navigation"
        )
        assertEquals(
            expected = 0f,
            actual = dragDistance,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe over Popup should not dispatch horizontal drag deltas to Compose"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Back gesture over Popup should not complete before release"
        )

        backSwipe.up()
        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Releasing edge swipe over Popup should still not start root back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Edge swipe over Popup should not complete root back navigation"
        )
    }

    @Test
    fun testInnerSwipeOverPopupDispatchesHorizontalDragWithoutStartingBackLtr() = runComposeContainerTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            PopupBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        findNodeWithTag(OVERLAY_SURFACE).swipeRight()

        waitUntil("Inner swipe should dispatch drag deltas over Popup") {
            dragDistance > 0f
        }

        assertFalse(
            transitionState is InProgress,
            "Inner swipe over Popup should not start back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Inner swipe over Popup should not complete back navigation"
        )
    }

    @Test
    fun testInnerSwipeOverPopupDispatchesHorizontalDragWithoutStartingBackRtl() = runComposeContainerTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            PopupBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        findNodeWithTag(OVERLAY_SURFACE).swipeLeft()

        waitUntil("Inner swipe should dispatch drag deltas over Popup") {
            dragDistance < 0f
        }

        assertFalse(
            transitionState is InProgress,
            "Inner swipe over Popup should not start back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Inner swipe over Popup should not complete back navigation"
        )
    }

    private fun runComposeContainerTest(testBlock: UIKitInstrumentedTest.() -> Unit) {
        runUIKitInstrumentedTest(testBlock)
    }
}

@Composable
private fun PopupBackGestureContent(
    onDragDistanceChanged: (Float) -> Unit,
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit,
    onBackCompletedCountChanged: (Int) -> Unit,
) {
    BackGestureHost(
        onTransitionStateChanged = onTransitionStateChanged,
        onBackCompletedCountChanged = onBackCompletedCountChanged
    ) {
        Popup(
            properties = PopupProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                focusable = true,
                usePlatformDefaultWidth = false,
                usePlatformInsets = false
            )
        ) {
            DraggableSurface(onDragDistanceChanged = onDragDistanceChanged)
        }
    }
}

@Composable
private fun DraggableSurface(
    onDragDistanceChanged: (Float) -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }

    onDragDistanceChanged(dragDistance)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OVERLAY_SURFACE)
            .draggable(
                state = rememberDraggableState { delta ->
                    dragDistance += delta
                },
                orientation = Orientation.Horizontal,
            )
    )
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

private const val OVERLAY_SURFACE = "overlaySurface"
