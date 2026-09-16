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
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

internal class DialogSwipeBackInHostingViewTest : DialogSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class DialogSwipeBackInHostingViewControllerTest : DialogSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class DialogSwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testEdgeBackSwipeOverDialogDoesNotDispatchHorizontalDragToComposeLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            DialogBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromLeftEdge().hold()

        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Edge swipe over Dialog should not start root back navigation"
        )
        assertEquals(
            expected = 0f,
            actual = dragDistance,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe over Dialog should not dispatch horizontal drag deltas to Compose"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Back gesture over Dialog should not complete before release"
        )

        backSwipe.up()

        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Releasing edge swipe over Dialog should still not start root back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Edge swipe over Dialog should not complete root back navigation"
        )
    }

    @Test
    fun testEdgeBackSwipeOverDialogDoesNotDispatchHorizontalDragToComposeRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            DialogBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        val backSwipe = swipeFromRightEdge().hold()

        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Edge swipe over Dialog should not start root back navigation"
        )
        assertEquals(
            expected = 0f,
            actual = dragDistance,
            absoluteTolerance = 0.01f,
            message = "Edge back swipe over Dialog should not dispatch horizontal drag deltas to Compose"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Back gesture over Dialog should not complete before release"
        )

        backSwipe.up()

        waitForIdle()

        assertFalse(
            transitionState is InProgress,
            "Releasing edge swipe over Dialog should still not start root back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Edge swipe over Dialog should not complete root back navigation"
        )
    }

    @Test
    fun testInnerSwipeOverDialogDispatchesHorizontalDragWithoutStartingBackLtr() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            DialogBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        findNodeWithTag(OVERLAY_SURFACE).swipeRight()

        waitUntil("Inner swipe should dispatch drag deltas over Dialog") {
            dragDistance > 0f
        }

        assertFalse(
            transitionState is InProgress,
            "Inner swipe over Dialog should not start back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Inner swipe over Dialog should not complete back navigation"
        )
    }

    @Test
    fun testInnerSwipeOverDialogDispatchesHorizontalDragWithoutStartingBackRtl() = runUIKitInstrumentedTest {
        var dragDistance = Float.NaN
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            DialogBackGestureContent(
                onDragDistanceChanged = { dragDistance = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        findNodeWithTag(OVERLAY_SURFACE).swipeLeft()

        waitUntil("Inner swipe should dispatch drag deltas over Dialog") {
            dragDistance < 0f
        }

        assertFalse(
            transitionState is InProgress,
            "Inner swipe over Dialog should not start back navigation"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "Inner swipe over Dialog should not complete back navigation"
        )
    }
}

@Composable
private fun DialogBackGestureContent(
    onDragDistanceChanged: (Float) -> Unit,
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit,
    onBackCompletedCountChanged: (Int) -> Unit,
) {
    BackGestureHost(
        onTransitionStateChanged = onTransitionStateChanged,
        onBackCompletedCountChanged = onBackCompletedCountChanged
    ) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
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
            .background(Color.Red)
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
