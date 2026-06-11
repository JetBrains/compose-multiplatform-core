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

package androidx.compose.ui.interop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.AccessibilityTestNode
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.UIKitInstrumentedTestBlock
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTestInHostingView
import androidx.compose.ui.test.runUIKitInstrumentedTestInHostingViewController
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
internal abstract class UIKitNavigationContentSwipeTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTestBlock) -> Unit
) {
    private val SwipeDuration = 100.milliseconds

    @Test
    fun testSwipeRightOnPagerDoesNotPopController() = runUIKitInstrumentedTest {
        val currentPage = mutableIntStateOf(0)

        setNavigationControllerContent {
            TestContent(currentPage = currentPage)
        }

        delay(10)

        swipeRight(fromNode = findNodeWithTag("pager"))

        delay(500)

        assertEquals(2, navigationController.viewControllers.size)
        assertNotNull(findNodeWithTagOrNull("pager"))
        assertEquals(0, currentPage.value)
    }

    @Test
    fun testSwipeLeftOnPagerChangesPage() = runUIKitInstrumentedTest {
        val currentPage = mutableIntStateOf(0)

        setNavigationControllerContent {
            TestContent(currentPage = currentPage)
        }

        delay(10)

        swipeLeft(fromNode = findNodeWithTag("pager"))

        assertEquals(1, currentPage.value)
        assertEquals(2, navigationController.viewControllers.size)
    }

    @Test
    fun testSwipeLeftOutsidePagerNoChanges() = runUIKitInstrumentedTest {
        val initialPage = 1
        val currentPage = mutableIntStateOf(initialPage)

        setNavigationControllerContent {
            TestContent(currentPage = currentPage)
        }

        delay(10)

        swipeLeft(fromNode = findNodeWithTag("outsideBox"))

        assertEquals(initialPage, currentPage.value)
        assertEquals(2, navigationController.viewControllers.size)
    }

    @Test
    fun testSwipeRightOutsidePagerPopsController() = runUIKitInstrumentedTest {
        val initialPage = 1
        val currentPage = mutableIntStateOf(initialPage)

        setNavigationControllerContent {
            TestContent(currentPage = currentPage)
        }

        delay(10)

        swipeRight(fromNode = findNodeWithTag("outsideBox"))

        // wait for pop animation to finish
        delay(500)

        assertNull(findNodeWithTagOrNull("pager"))
        assertNull(findNodeWithTagOrNull("outsideBox"))
        assertEquals(1, navigationController.viewControllers.size)
    }

    private fun UIKitInstrumentedTest.swipeRight(fromNode: AccessibilityTestNode) {
        fromNode.touchDown()
            .dragTo(x = screenSize.width - 16.dp, duration = SwipeDuration)
            .up()

        waitForIdle()
    }

    private fun UIKitInstrumentedTest.swipeLeft(fromNode: AccessibilityTestNode) {
        fromNode.touchDown()
            .dragTo(x = 16.dp, duration = SwipeDuration)
            .up()

        waitForIdle()
    }

    private val UIKitInstrumentedTest.navigationController: UINavigationController get() {
        return assertNotNull(appDelegate.window?.rootViewController as? UINavigationController)
    }

    private fun UIKitInstrumentedTest.setNavigationControllerContent(
        content: @Composable () -> Unit = {}
    ) {
        val firstViewController = UIViewController()
        val secondViewController = createRootViewController(content = content)
        val navigationController = UINavigationController()

        navigationController.setViewControllers(
            listOf(firstViewController, secondViewController), false
        )

        appDelegate.setUpWindow(navigationController)

        waitForIdle()
    }
}

@Composable
private fun TestContent(
    currentPage: MutableIntState
) {
    val pagerColors = listOf(Color.Red, Color.Green, Color.Blue)
    val pagerState = rememberPagerState(initialPage = currentPage.value) { 3 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("pager")
        ) { page ->
            currentPage.value = page
            Box(modifier = Modifier
                .fillMaxSize()
                .background(pagerColors[page])
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .testTag("outsideBox")
        )
    }
}

internal class UIKitNavigationContentSwipeInHostingViewTest : UIKitNavigationContentSwipeTest(
    runUIKitInstrumentedTest = ::runUIKitInstrumentedTestInHostingView
)

internal class UIKitNavigationContentSwipeInHostingViewControllerTest : UIKitNavigationContentSwipeTest(
   runUIKitInstrumentedTest = ::runUIKitInstrumentedTestInHostingViewController
)
