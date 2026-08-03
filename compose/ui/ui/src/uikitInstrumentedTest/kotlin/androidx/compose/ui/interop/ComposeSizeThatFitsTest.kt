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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewNoIntrinsicMetric
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
internal abstract class ComposeSizeThatFitsTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    private val contentSize = DpSize(200.dp, 100.dp)

    @Test
    fun testBothAxesBounded() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, 60.0),
        expected = DpSize(150.dp, 60.dp)
    )

    @Test
    fun testBothAxesBoundedProposedHeightLargerThanContentSize() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, (contentSize.height + 10.dp).value.toDouble()),
        expected = DpSize(150.dp, contentSize.height)
    )

    @Test
    fun testBothAxesBoundedProposedWidthLargerThanContentSize() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake((contentSize.width + 10.dp).value.toDouble(), 60.0),
        expected = DpSize(contentSize.width, 60.dp)
    )

    @Test
    fun testBothAxesBoundedLargerThanContentSize() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(
            (contentSize.width + 10.dp).value.toDouble(),
            (contentSize.height + 10.dp).value.toDouble()
        ),
        expected = DpSize(contentSize.width, contentSize.height)
    )

    @Test
    fun testBoundedWidthAndUnboundedHeight() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, UIViewNoIntrinsicMetric),
        expected = DpSize(150.dp, 100.dp)
    )

    @Test
    fun testUnboundedWidthAndBoundedHeight() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(UIViewNoIntrinsicMetric, 60.0),
        expected = DpSize(200.dp, 60.dp)
    )

    @Test
    fun testBothAxesUnbounded() = testComposeSizeThatFits(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(UIViewNoIntrinsicMetric, UIViewNoIntrinsicMetric),
        expected = DpSize(200.dp, 100.dp)
    )

    private fun testComposeSizeThatFits(
        proposal: CValue<CGSize>,
        expected: DpSize,
        content: @Composable () -> Unit
    ) = runComposeSizeThatFitsTest(content) { composeView ->
        composeView.applyFrame(composeView.sizeThatFits(proposal))
        waitForIdle()

        val actual = composeView.sizeThatFits(proposal)
        composeView.applyFrame(actual)
        waitForIdle()

        assertEquals(expected, actual.toDpSize())
    }

    private fun runComposeSizeThatFitsTest(
        content: @Composable () -> Unit,
        runTest: UIKitInstrumentedTest.(UIView) -> Unit
    ) = runUIKitInstrumentedTest {
        val rootViewController = UIViewController()
        val composeView = if (useHostingView) {
            createComposeHostingView(content = content).also {
                rootViewController.view.addSubview(it)
            }
        } else {
            createComposeHostingViewController(content = content).also {
                rootViewController.addChildViewController(it)
                rootViewController.view.addSubview(it.view)
                it.didMoveToParentViewController(rootViewController)
            }.view
        }

        appDelegate.setUpWindow(rootViewController)

        this.runTest(composeView)
    }

    private fun CValue<CGSize>.toDpSize(): DpSize =
        useContents {
            DpSize(width.dp, height.dp)
        }

    private fun UIView.applyFrame(size: CValue<CGSize>) {
        size.useContents {
            setFrame(CGRectMake(0.0, 0.0, width, height))
        }
        layoutIfNeeded()
    }
}

internal class ComposeSizeThatFitsInHostingViewTest :
    ComposeSizeThatFitsTest(runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) })

internal class ComposeSizeThatFitsInHostingViewControllerTest :
    ComposeSizeThatFitsTest(runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) })
