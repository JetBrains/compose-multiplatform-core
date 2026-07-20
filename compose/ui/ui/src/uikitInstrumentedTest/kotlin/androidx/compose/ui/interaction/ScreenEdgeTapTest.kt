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

package androidx.compose.ui.interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft

internal class ScreenEdgeTapInHostingViewTest : ScreenEdgeTapTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class ScreenEdgeTapInHostingViewControllerTest : ScreenEdgeTapTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class ScreenEdgeTapTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testRepeatedTapsFromLeftEdgeDispatchClicksToComposeInLtr() = runUIKitInstrumentedTest {
        runRepeatedEdgeTapTest(
            initialLayoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight,
            edge = Edge.Left,
            expectedMessagePrefix = "left edge taps should reach Compose in LTR"
        )
    }

    @Test
    fun testRepeatedTapsFromRightEdgeDispatchClicksToComposeInLtr() = runUIKitInstrumentedTest {
        runRepeatedEdgeTapTest(
            initialLayoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight,
            edge = Edge.Right,
            expectedMessagePrefix = "right edge taps should reach Compose in LTR"
        )
    }

    @Test
    fun testRepeatedTapsFromLeftEdgeDispatchClicksToComposeInRtl() = runUIKitInstrumentedTest {
        runRepeatedEdgeTapTest(
            initialLayoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft,
            edge = Edge.Left,
            expectedMessagePrefix = "left edge taps should reach Compose in RTL"
        )
    }

    @Test
    fun testRepeatedTapsFromRightEdgeDispatchClicksToComposeInRtl() = runUIKitInstrumentedTest {
        runRepeatedEdgeTapTest(
            initialLayoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft,
            edge = Edge.Right,
            expectedMessagePrefix = "right edge taps should reach Compose in RTL"
        )
    }

    @Test
    fun testChangingLtrToRtlStillDispatchesRepeatedEdgeTapsToCompose() = runUIKitInstrumentedTest {
        var tapCount = -1
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1
        var composeLayoutDirection: LayoutDirection? = null

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            TapTestContent(
                onTapCountChanged = { tapCount = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it },
                onComposeLayoutDirectionChanged = { composeLayoutDirection = it }
            )
        }

        waitUntil("tap surface should be ready in LTR") {
            tapCount == 0 && backCompletedCount == 0 && composeLayoutDirection == LayoutDirection.Ltr
        }

        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Left,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "left edge taps should reach Compose before switching to RTL"
        )
        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Right,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "right edge taps should reach Compose before switching to RTL"
        )

        setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft)

        waitUntil("compose layout direction should switch to RTL") {
            composeLayoutDirection == LayoutDirection.Rtl
        }

        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Left,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "left edge taps should still reach Compose after switching to RTL"
        )
        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Right,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "right edge taps should still reach Compose after switching to RTL"
        )
    }

    @Test
    fun testChangingRtlToLtrStillDispatchesRepeatedEdgeTapsToCompose() = runUIKitInstrumentedTest {
        var tapCount = -1
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1
        var composeLayoutDirection: LayoutDirection? = null

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            TapTestContent(
                onTapCountChanged = { tapCount = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it },
                onComposeLayoutDirectionChanged = { composeLayoutDirection = it }
            )
        }

        waitUntil("tap surface should be ready in RTL") {
            tapCount == 0 && backCompletedCount == 0 && composeLayoutDirection == LayoutDirection.Rtl
        }

        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Left,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "left edge taps should reach Compose before switching to LTR"
        )
        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Right,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "right edge taps should reach Compose before switching to LTR"
        )

        setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight)

        waitUntil("compose layout direction should switch to LTR") {
            composeLayoutDirection == LayoutDirection.Ltr
        }

        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Left,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "left edge taps should still reach Compose after switching to LTR"
        )
        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = Edge.Right,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = "right edge taps should still reach Compose after switching to LTR"
        )
    }

    private fun UIKitInstrumentedTest.runRepeatedEdgeTapTest(
        initialLayoutDirection: Long,
        edge: Edge,
        expectedMessagePrefix: String
    ) {
        var tapCount = -1
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setContent(layoutDirection = initialLayoutDirection) {
            TapTestContent(
                onTapCountChanged = { tapCount = it },
                onTransitionStateChanged = { transitionState = it },
                onBackCompletedCountChanged = { backCompletedCount = it }
            )
        }

        waitUntil("tap surface should be ready") {
            tapCount == 0 && backCompletedCount == 0
        }

        assertRepeatedEdgeTapsTriggerComposeClicks(
            edge = edge,
            startingTapCount = tapCount,
            currentTapCount = { tapCount },
            currentTransitionState = { transitionState },
            currentBackCompletedCount = { backCompletedCount },
            messagePrefix = expectedMessagePrefix
        )
    }

    private fun UIKitInstrumentedTest.assertRepeatedEdgeTapsTriggerComposeClicks(
        edge: Edge,
        startingTapCount: Int,
        currentTapCount: () -> Int,
        currentTransitionState: () -> NavigationEventTransitionState,
        currentBackCompletedCount: () -> Int,
        messagePrefix: String
    ) {
        val tapPositions = edgeTapPositions(edge)
        tapPositions.forEach { position ->
            tapFromEdge(position)
        }

        val expectedTapCount = startingTapCount + tapPositions.size
        waitUntil(
            "$messagePrefix: " +
                "expected $expectedTapCount clicks got ${currentTapCount()} clicks"
        ) { currentTapCount() == expectedTapCount }
        assertEquals(
            expectedTapCount,
            currentTapCount(),
            message = "$messagePrefix: all edge taps should be delivered to Compose"
        )
        assertTrue(
            currentTransitionState() is NavigationEventTransitionState.Idle,
            message = "$messagePrefix: taps should not start back navigation"
        )
        assertEquals(
            0,
            currentBackCompletedCount(),
            message = "$messagePrefix: taps should not complete back navigation"
        )
    }

    private fun UIKitInstrumentedTest.edgeTapPositions(edge: Edge): List<DpOffset> {
        val verticalFractions = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        return verticalFractions.map { fraction ->
            val y = screenBounds.top + (screenBounds.bottom - screenBounds.top) * fraction
            when (edge) {
                Edge.Left -> DpOffset(screenBounds.left, y)
                Edge.Right -> DpOffset(screenBounds.right - 1.dp, y)
            }
        }
    }

    private fun UIKitInstrumentedTest.tapFromEdge(position: DpOffset) {
        touchDown(position, fromEdge = true).up()
    }
}

@Composable
private fun TapTestContent(
    onTapCountChanged: (Int) -> Unit = {},
    onTransitionStateChanged: (NavigationEventTransitionState) -> Unit = {},
    onBackCompletedCountChanged: (Int) -> Unit = {},
    onComposeLayoutDirectionChanged: (LayoutDirection) -> Unit = {}
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var backCompletedCount by remember { mutableIntStateOf(0) }
    val navigationEventState = rememberNavigationEventState(
        currentInfo = NavigationEventInfo.None,
        backInfo = listOf<NavigationEventInfo>(NavigationEventInfo.None)
    )

    val composeLayoutDirection = LocalLayoutDirection.current
    SideEffect {
        onComposeLayoutDirectionChanged(composeLayoutDirection)
    }

    onTapCountChanged(tapCount)
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
            .testTag(TAP_SURFACE)
            .clickable {
                tapCount += 1
            }
    )
}

private const val TAP_SURFACE = "tapSurface"

private enum class Edge {
    Left,
    Right
}
