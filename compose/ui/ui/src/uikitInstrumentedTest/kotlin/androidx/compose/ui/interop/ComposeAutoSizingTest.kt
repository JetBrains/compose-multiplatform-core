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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dpSize
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropRemeasureRequester
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.remeasureRequester
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewNoIntrinsicMetric
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
internal abstract class ComposeAutoSizingTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    private val contentSize = DpSize(200.dp, 100.dp)

    @Test
    fun testBothAxesBounded() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, 60.0),
        expected = DpSize(150.dp, 60.dp)
    )

    @Test
    fun testBothAxesBoundedProposedHeightLargerThanContentSize() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, (contentSize.height + 10.dp).value.toDouble()),
        expected = DpSize(150.dp, contentSize.height)
    )

    @Test
    fun testBothAxesBoundedProposedWidthLargerThanContentSize() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake((contentSize.width + 10.dp).value.toDouble(), 60.0),
        expected = DpSize(contentSize.width, 60.dp)
    )

    @Test
    fun testBothAxesBoundedLargerThanContentSize() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(
            (contentSize.width + 10.dp).value.toDouble(),
            (contentSize.height + 10.dp).value.toDouble()
        ),
        expected = DpSize(contentSize.width, contentSize.height)
    )

    @Test
    fun testBoundedWidthAndUnboundedHeight() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(150.0, UIViewNoIntrinsicMetric),
        expected = DpSize(150.dp, 100.dp)
    )

    @Test
    fun testUnboundedWidthAndBoundedHeight() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(UIViewNoIntrinsicMetric, 60.0),
        expected = DpSize(200.dp, 60.dp)
    )

    @Test
    fun testBothAxesUnbounded() = testComposeSizing(
        content = { Box(Modifier.size(contentSize)) },
        proposal = CGSizeMake(UIViewNoIntrinsicMetric, UIViewNoIntrinsicMetric),
        expected = DpSize(200.dp, 100.dp)
    )

    @Test
    fun testSizeThatFitsBeforeWindowAttachmentConvergesAfterLayout() = runUIKitInstrumentedTest {
        var composeSceneSize: DpSize? = null
        val contentHeight = mutableStateOf(100.dp)
        val initialExpected = DpSize(150.dp, 100.dp)
        val updatedExpected = DpSize(150.dp, 160.dp)
        val proposal = CGSizeMake(150.0, UIViewNoIntrinsicMetric)
        val composeHostView = createComposeHostingView(
            content = {
                Column(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        composeSceneSize = coordinates.boundsInWindow().toDpRect(density).size
                    }
                ) {
                    Box(Modifier.size(width = contentSize.width, height = contentHeight.value))
                }
            }
        )
        val context = SwiftUISimulationContext(
            composeHostView = ComposeHostView(composeHostView),
            getComposeContentSize = { composeSceneSize }
        )

        val fallbackSize = context.proposeSwiftUIConstraints(proposal)
        assertTrue(
            fallbackSize.useContents { width > 0.0 && height > 0.0 },
            "A detached Compose view must not return a zero fallback size"
        )

        val rootViewController = UIViewController()
        rootViewController.view.addSubview(composeHostView)
        appDelegate.setUpWindow(rootViewController)
        requestSwiftUISizingFeedback(context)

        waitForExpectedSize(
            context = context,
            expected = initialExpected,
            phase = "after attaching a view sized by the fallback result"
        )

        contentHeight.value = 160.dp
        requestSwiftUISizingFeedback(context)
        waitForExpectedSize(context, updatedExpected, "content change after attachment")
    }

    @Test
    fun testFixedSizeProposalAndComposeContentChanges() {
        val expanded = mutableStateOf(false)
        val sizeProposal = CGSizeMake(150.0, UIViewNoIntrinsicMetric)
        val collapsedExpected = DpSize(150.dp, 60.dp)
        val expandedExpected = DpSize(150.dp, 120.dp)

        runComposeSizingTest(
            content = {
                val height = if (expanded.value) 120.dp else 60.dp
                Box(Modifier.size(width = 200.dp, height = height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)

            waitForExpectedSize(context, collapsedExpected, "initial fixed-width proposal state")

            expanded.value = true
            requestSwiftUISizingFeedback(context)

            waitForExpectedSize(context, expandedExpected, "expanded fixed-width proposal state")
        }
    }

    @Test
    fun testFixedHeightProposalAndComposeContentWidthChanges() {
        val expanded = mutableStateOf(false)
        val sizeProposal = CGSizeMake(UIViewNoIntrinsicMetric, 80.0)
        val collapsedExpected = DpSize(100.dp, 80.dp)
        val expandedExpected = DpSize(180.dp, 80.dp)

        runComposeSizingTest(
            content = {
                val width = if (expanded.value) 180.dp else 100.dp
                Box(Modifier.size(width = width, height = 140.dp))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)
            waitForExpectedSize(context, collapsedExpected, "initial fixed-height proposal state")

            expanded.value = true
            requestSwiftUISizingFeedback(context)

            waitForExpectedSize(context, expandedExpected, "expanded fixed-height proposal state")
        }
    }

    @Test
    fun testUnboundedWidthAndBoundedHeightTracksContentWidthSequence() {
        val contentWidth = mutableStateOf(100.dp)
        val sizeProposal = CGSizeMake(UIViewNoIntrinsicMetric, 80.0)

        runComposeSizingTest(
            content = {
                Box(Modifier.size(width = contentWidth.value, height = 140.dp))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)

            for (i in 0..10) {
                val expectedWidth = (150 - abs(5 - i) * 10).dp
                contentWidth.value = expectedWidth
                requestSwiftUISizingFeedback(context)
                waitForExpectedSize(
                    context = context,
                    expected = DpSize(expectedWidth, 80.dp),
                    phase = "fixed-height proposal with content width $expectedWidth"
                )
            }
        }
    }

    @Test
    fun testRapidContentUpdatesConvergeToLatestSize() {
        val contentWidth = mutableStateOf(100.dp)
        val sizeProposal = CGSizeMake(UIViewNoIntrinsicMetric, 80.0)

        runComposeSizingTest(
            content = {
                Box(Modifier.size(width = contentWidth.value, height = 140.dp))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)
            waitForExpectedSize(context, DpSize(100.dp, 80.dp), "initial content width")

            listOf(120.dp, 140.dp, 160.dp, 180.dp).forEach { width ->
                contentWidth.value = width
            }

            requestSwiftUISizingFeedback(context)
            waitForExpectedSize(context, DpSize(180.dp, 80.dp), "latest rapid content update")
        }
    }

    @Test
    fun testUnboundedWidthAndChangingBoundedHeightProposalSequence() {
        runComposeSizingTest(
            content = { Box(Modifier.size(200.dp)) }
        ) { context ->
            listOf(40.dp, 60.dp, 80.dp, 100.dp, 80.dp, 60.dp, 40.dp).forEach { proposedHeight ->
                proposeSwiftUIConstraints(context,
                    CGSizeMake(UIViewNoIntrinsicMetric, proposedHeight.value.toDouble())
                )
                waitForExpectedSize(
                    context = context,
                    expected = DpSize(200.dp, proposedHeight),
                    phase = "unbounded-width proposal with height $proposedHeight"
                )
            }
        }
    }

    @Test
    fun testContentGrowAndShrinkAcrossBoundedProposal() {
        val contentSize = mutableStateOf(DpSize(80.dp, 40.dp))
        val sizeProposal = CGSizeMake(140.0, 75.0)

        runComposeSizingTest(
            content = {
                Box(Modifier.size(width = contentSize.value.width, height = contentSize.value.height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)

            listOf(
                DpSize(80.dp, 40.dp) to DpSize(80.dp, 40.dp),
                DpSize(220.dp, 180.dp) to DpSize(140.dp, 75.dp),
                DpSize(80.dp, 40.dp) to DpSize(80.dp, 40.dp)
            ).forEach { (newContentSize, expectedSize) ->
                contentSize.value = newContentSize
                requestSwiftUISizingFeedback(context)
                waitForExpectedSize(
                    context = context,
                    expected = expectedSize,
                    phase = "content size $newContentSize with bounded proposal"
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun testWrappingFlowRowTracksWidthProposalSequence() {
        val boxCount = 4
        val boxWidth = 50
        val boxHeight = 20
        val widthStep = 25
        val numberOfSteps = 6

        runComposeSizingTest(
            content = {
                FlowRow {
                    listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow).forEach { color ->
                        Box(
                            Modifier
                                .size(width = boxWidth.dp, height = boxHeight.dp)
                                .background(color)
                        )
                    }
                }
            }
        ) { context ->
            for (i in 0..(numberOfSteps * 2)) {
                val step = if (i <= numberOfSteps) i else numberOfSteps * 2 - i
                val proposedWidth = boxWidth + step * widthStep
                val boxesPerRow = (proposedWidth / boxWidth).coerceAtLeast(1)
                val rowCount = (boxCount + boxesPerRow - 1) / boxesPerRow
                val expectedSize = DpSize(
                    width = minOf(proposedWidth, boxesPerRow * boxWidth).dp,
                    height = (rowCount * boxHeight).dp
                )
                proposeSwiftUIConstraints(context,
                    CGSizeMake(proposedWidth.toDouble(), UIViewNoIntrinsicMetric)
                )
                waitForExpectedSize(
                    context = context,
                    expected = expectedSize,
                    phase = "wrapping flow row with width ${proposedWidth.dp}"
                )
            }
        }
    }

    @Test
    fun testUIKitIntrinsicSizeChangeUpdatesSwiftUISize() {
        class IntrinsicSizeView : UIView(frame = CGRectZero.readValue()) {
            val widthConstraint = widthAnchor.constraintEqualToConstant(50.0)
            val heightConstraint = heightAnchor.constraintEqualToConstant(40.0)

            init {
                translatesAutoresizingMaskIntoConstraints = false
                backgroundColor = UIColor.blueColor
                NSLayoutConstraint.activateConstraints(listOf(widthConstraint, heightConstraint))
            }
        }

        val uiKitView = IntrinsicSizeView()
        val remeasureRequester = UIKitInteropRemeasureRequester()

        runComposeSizingTest(
            content = {
                UIKitView(
                    factory = { uiKitView },
                    modifier = Modifier.remeasureRequester(remeasureRequester),
                    properties = UIKitInteropProperties(placedAsOverlay = false)
                )
            }
        ) { context ->
            proposeSwiftUIConstraints(context,
                CGSizeMake(UIViewNoIntrinsicMetric, UIViewNoIntrinsicMetric)
            )
            waitForExpectedSize(context, DpSize(50.dp, 40.dp), "initial UIKit intrinsic size")

            uiKitView.widthConstraint.constant = 120.0
            uiKitView.heightConstraint.constant = 90.0
            remeasureRequester.requestRemeasure()

            requestSwiftUISizingFeedback(context)
            waitForExpectedSize(context, DpSize(120.dp, 90.dp), "updated UIKit intrinsic size")
        }
    }

    @Test
    fun testUnboundedProposalAndComposeContentBothAxesChange() {
        val expanded = mutableStateOf(false)
        val sizeProposal = CGSizeMake(UIViewNoIntrinsicMetric, UIViewNoIntrinsicMetric)
        val collapsedExpected = DpSize(90.dp, 40.dp)
        val expandedExpected = DpSize(170.dp, 130.dp)

        runComposeSizingTest(
            content = {
                val width = if (expanded.value) 170.dp else 90.dp
                val height = if (expanded.value) 130.dp else 40.dp
                Box(Modifier.size(width = width, height = height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)
            waitForExpectedSize(context, collapsedExpected, "initial fully-unbounded proposal state")

            expanded.value = true
            requestSwiftUISizingFeedback(context)

            waitForExpectedSize(context, expandedExpected, "expanded fully-unbounded proposal state")
        }
    }

    @Test
    fun testBoundedProposalAndComposeContentChangesFromSmallerToLargerThanProposal() {
        val expanded = mutableStateOf(false)
        val sizeProposal = CGSizeMake(140.0, 75.0)
        val collapsedExpected = DpSize(80.dp, 40.dp)
        val expandedExpected = DpSize(140.dp, 75.dp)

        runComposeSizingTest(
            content = {
                val width = if (expanded.value) 220.dp else 80.dp
                val height = if (expanded.value) 180.dp else 40.dp
                Box(Modifier.size(width = width, height = height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)

            waitForExpectedSize(context, collapsedExpected, "initial bounded proposal state")

            expanded.value = true
            requestSwiftUISizingFeedback(context)

            waitForExpectedSize(context, expandedExpected, "expanded bounded proposal state")
        }
    }

    @Test
    fun testBoundedProposalAndComposeContentChangesFromLargerToSmallerThanProposal() {
        val expanded = mutableStateOf(true)
        val sizeProposal = CGSizeMake(140.0, 75.0)
        val collapsedExpected = DpSize(80.dp, 40.dp)
        val expandedExpected = DpSize(140.dp, 75.dp)

        runComposeSizingTest(
            content = {
                val width = if (expanded.value) 220.dp else 80.dp
                val height = if (expanded.value) 180.dp else 40.dp
                Box(Modifier.size(width = width, height = height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, sizeProposal)

            waitForExpectedSize(context, expandedExpected, "initial bounded proposal state")

            expanded.value = false
            requestSwiftUISizingFeedback(context)

            waitForExpectedSize(context, collapsedExpected, "collapsed bounded proposal state")
        }
    }

    @Test
    fun testComposeContentAndProposalChangeSequence() {
        val expanded = mutableStateOf(false)
        val firstProposal = CGSizeMake(150.0, UIViewNoIntrinsicMetric)
        val secondProposal = CGSizeMake(UIViewNoIntrinsicMetric, 90.0)
        val collapsedWithFirstProposal = DpSize(150.dp, 60.dp)
        val expandedWithFirstProposal = DpSize(150.dp, 120.dp)
        val expandedWithSecondProposal = DpSize(200.dp, 90.dp)

        runComposeSizingTest(
            content = {
                val height = if (expanded.value) 120.dp else 60.dp
                Box(Modifier.size(width = 200.dp, height = height))
            }
        ) { context ->
            proposeSwiftUIConstraints(context, firstProposal)
            waitForExpectedSize(context, collapsedWithFirstProposal, "collapsed with first proposal")

            expanded.value = true
            requestSwiftUISizingFeedback(context)
            waitForExpectedSize(context, expandedWithFirstProposal, "expanded with first proposal")

            proposeSwiftUIConstraints(context, secondProposal)
            waitForExpectedSize(context, expandedWithSecondProposal, "expanded with second proposal")
        }
    }

    private fun runComposeSizingTest(
        content: @Composable () -> Unit,
        runTest: UIKitInstrumentedTest.(SwiftUISimulationContext) -> Unit
    ) = runUIKitInstrumentedTest {
        var composeSceneSize: DpSize? = null

        val columnContent = @Composable {
            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    composeSceneSize = coordinates.boundsInWindow().toDpRect(density).size
                },
                content = { content() }
            )
        }

        val rootViewController = UIViewController()

        val composeHostView = if (useHostingView) {
            val hostingView = createComposeHostingView(content = columnContent).also {
                rootViewController.view.addSubview(it)
            }
            ComposeHostView(
                view = hostingView
            )
        } else {
            val hostingViewController = createComposeHostingViewController(content = columnContent).also {
                rootViewController.addChildViewController(it)
                rootViewController.view.addSubview(it.view)
                it.didMoveToParentViewController(rootViewController)
            }
            ComposeHostView(
                view = hostingViewController.view
            )
        }

        appDelegate.setUpWindow(rootViewController)

        this.runTest(
            SwiftUISimulationContext(
                composeHostView = composeHostView,
                getComposeContentSize = { composeSceneSize }
            )
        )
    }

    private class ComposeHostView(
        // view that is embedding the compose scene
        val view: UIView
    )

    private class SwiftUISimulationContext(
        val composeHostView: ComposeHostView,
        private val getComposeContentSize: () -> DpSize?
    ) {
        val composeView: UIView get() = composeHostView.view
        val composeContentSize: DpSize? get() = getComposeContentSize()
        val composeUIViewSize: DpSize get() = composeView.frame.dpSize()

        private var lastSwiftUIConstraints: CValue<CGSize>? = null
        private var hasPendingFeedback = false

        // UIKit processes intrinsic-content-size invalidation in a later layout pass. Replaying the
        // SwiftUI proposal synchronously would call sizeThatFits while Compose is still measuring.
        fun scheduleSwiftUIFeedback() {
            if (lastSwiftUIConstraints == null || hasPendingFeedback) return

            hasPendingFeedback = true
            dispatch_async(dispatch_get_main_queue()) {
                hasPendingFeedback = false
                lastSwiftUIConstraints?.let(::proposeSwiftUIConstraints)
            }
        }

        fun proposeSwiftUIConstraints(size: CValue<CGSize>): CValue<CGSize> {
            lastSwiftUIConstraints = size
            val sizeThatFits = composeView.sizeThatFits(size)
            composeView.applyFrame(sizeThatFits)
            return sizeThatFits
        }

        private fun UIView.applyFrame(size: CValue<CGSize>) {
            size.useContents {
                setFrame(CGRectMake(0.0, 0.0, width, height))
            }
            layoutIfNeeded()
        }
    }

    private fun testComposeSizing(
        proposal: CValue<CGSize>,
        expected: DpSize,
        content: @Composable () -> Unit
    ) = runComposeSizingTest(content) { context ->
        proposeSwiftUIConstraints(context, proposal)

        waitForIdle()

        waitForExpectedSize(context, expected, "proposal")
    }

    private fun UIKitInstrumentedTest.waitForExpectedSize(
        context: SwiftUISimulationContext,
        expected: DpSize,
        phase: String
    ) {
        try {
            waitUntil(
                conditionDescription = "Waiting for expected size ($phase): $expected"
            ) {
                context.composeContentSize == expected &&
                    context.composeUIViewSize == expected
            }
        } catch (e: Throwable) {
            println(
                "composeContentSize ${context.composeContentSize}, " +
                    "composeUIViewSize ${context.composeUIViewSize}, expected $expected"
            )
            throw e
        }
    }

    private fun UIKitInstrumentedTest.requestSwiftUISizingFeedback(
        context: SwiftUISimulationContext
    ) {
        waitForIdle()
        context.scheduleSwiftUIFeedback()
    }

    private fun UIKitInstrumentedTest.proposeSwiftUIConstraints(
        context: SwiftUISimulationContext,
        size: CValue<CGSize>
    ) {
        context.proposeSwiftUIConstraints(size)
        requestSwiftUISizingFeedback(context)
    }
}

// All tests in BaseComposeAutoSizingTest should be run for both hosts ComposeHostingView and
// ComposeHostingViewController. We need to run each test as a separate XCTest and to achieve this
// we create two implementations of BaseComposeAutoSizingTest where each implementation uses a
// different way to run the test. This method avoids code duplication but mainly it avoids flaky
// tests because setContent and therefore appDelegate.setUpWindow is only called once for each XCTest.
internal class ComposeAutoSizingInHostingViewTest :
    ComposeAutoSizingTest(runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) })

internal class ComposeAutoSizingInHostingViewControllerTest :
    ComposeAutoSizingTest(runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) })
