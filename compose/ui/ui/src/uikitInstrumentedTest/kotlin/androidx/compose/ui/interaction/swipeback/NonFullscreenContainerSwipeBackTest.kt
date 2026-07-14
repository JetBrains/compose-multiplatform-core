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

import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.setLayoutDirection
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.up
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

internal class NonFullscreenContainerSwipeBackInHostingViewTest : NonFullscreenContainerSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class NonFullscreenContainerSwipeBackInHostingViewControllerTest :
    NonFullscreenContainerSwipeBackTest(
        runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
    )

internal abstract class NonFullscreenContainerSwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testBackSwipeCompletesInNonFullscreenContainerLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setupWindow {
            val composeViewController = createViewControllerHostingCompose {
                SwipeBackTestContent(
                    onTransitionStateChanged = { transitionState = it },
                    onBackCompletedCountChanged = { backCompletedCount = it }
                )
            }.apply { setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight) }

            UIViewController().also { hostViewController ->
                hostViewController.addChildViewController(composeViewController)
                hostViewController.view.addSubview(composeViewController.view)
                composeViewController.view.setFrame(CGRectMake(80.0, 160.0, 220.0, 260.0))
                composeViewController.didMoveToParentViewController(hostViewController)
            }
        }

        val swipeBack = swipeFromLeftEdge().hold()

        waitUntil("back swipe should be in progress in a non-fullscreen container") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_LEFT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "back swipe should report the expected edge in a non-fullscreen container"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "back swipe should not complete before release in a non-fullscreen container"
        )

        swipeBack.up()

        waitUntil("back swipe should complete in a non-fullscreen container") {
            backCompletedCount == 1
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testBackSwipeCompletesInNonFullscreenContainerRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        setupWindow {
            val composeViewController = createViewControllerHostingCompose {
                SwipeBackTestContent(
                    onTransitionStateChanged = { transitionState = it },
                    onBackCompletedCountChanged = { backCompletedCount = it }
                )
            }.apply { setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft) }

            UIViewController().also { hostViewController ->
                hostViewController.addChildViewController(composeViewController)
                hostViewController.view.addSubview(composeViewController.view)
                composeViewController.view.setFrame(CGRectMake(80.0, 160.0, 220.0, 260.0))
                composeViewController.didMoveToParentViewController(hostViewController)
            }
        }

        val swipeBack = swipeFromRightEdge().hold()

        waitUntil("back swipe should be in progress in a non-fullscreen container") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_RIGHT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "back swipe should report the expected edge in a non-fullscreen container"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "back swipe should not complete before release in a non-fullscreen container"
        )

        swipeBack.up()

        waitUntil("back swipe should complete in a non-fullscreen container") {
            backCompletedCount == 1
        }
    }
}
