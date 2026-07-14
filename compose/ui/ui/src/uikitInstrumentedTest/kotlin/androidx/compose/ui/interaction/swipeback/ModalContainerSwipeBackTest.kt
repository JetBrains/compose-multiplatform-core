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
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft
import platform.UIKit.UIViewController

internal class ModalContainerSwipeBackInHostingViewTest : ModalContainerSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class ModalContainerSwipeBackInHostingViewControllerTest : ModalContainerSwipeBackTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class ModalContainerSwipeBackTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testBackSwipeCompletesInModalContainerLtr() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        val rootViewController = UIViewController().apply {
            setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight)
        }
        setupWindow { rootViewController }

        waitUntil("root view controller should be attached before presenting modal") {
            rootViewController.view.window != null
        }

        var presented = false
        rootViewController.presentViewController(
            viewControllerToPresent = createViewControllerHostingCompose {
                SwipeBackTestContent(
                    onTransitionStateChanged = { transitionState = it },
                    onBackCompletedCountChanged = { backCompletedCount = it }
                )
            }.apply {
                modalPresentationStyle = UIModalPresentationFullScreen
                setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight)
            },
            animated = false
        ) {
            presented = true
        }

        waitUntil("modal container should be presented") { presented }

        val swipeBack = swipeFromLeftEdge().hold()

        waitUntil("back swipe should be in progress in a modal container") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_LEFT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "back swipe should report the expected edge in a modal container"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "back swipe should not complete before release in a modal container"
        )

        swipeBack.up()

        waitUntil("back swipe should complete in a modal container") {
            backCompletedCount == 1
        }
    }

    @Test
    fun testBackSwipeCompletesInModalContainerRtl() = runUIKitInstrumentedTest {
        var transitionState: NavigationEventTransitionState = NavigationEventTransitionState.Idle
        var backCompletedCount = -1

        val rootViewController = UIViewController().apply {
            setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft)
        }
        setupWindow { rootViewController }

        waitUntil("root view controller should be attached before presenting modal") {
            rootViewController.view.window != null
        }

        var presented = false
        rootViewController.presentViewController(
            viewControllerToPresent = createViewControllerHostingCompose {
                SwipeBackTestContent(
                    onTransitionStateChanged = { transitionState = it },
                    onBackCompletedCountChanged = { backCompletedCount = it }
                )
            }.apply {
                modalPresentationStyle = UIModalPresentationFullScreen
                setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft)
            },
            animated = false
        ) {
            presented = true
        }

        waitUntil("modal container should be presented") { presented }

        val swipeBack = swipeFromRightEdge().hold()

        waitUntil("back swipe should be in progress in a modal container") {
            transitionState is InProgress
        }

        assertEquals(
            expected = NavigationEvent.EDGE_RIGHT,
            actual = (transitionState as InProgress).latestEvent.swipeEdge,
            message = "back swipe should report the expected edge in a modal container"
        )
        assertEquals(
            expected = 0,
            actual = backCompletedCount,
            message = "back swipe should not complete before release in a modal container"
        )

        swipeBack.up()

        waitUntil("back swipe should complete in a modal container") {
            backCompletedCount == 1
        }
    }
}
