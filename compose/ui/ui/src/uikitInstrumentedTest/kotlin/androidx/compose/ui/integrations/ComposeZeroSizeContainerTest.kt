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

package androidx.compose.ui.integrations

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.scene.ComposeHostingView
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSTimeInterval
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController

/**
 * Verifies that a Compose view with an empty frame doesn't compose or draw its content, doesn't
 * crash, and works normally once it gets a non-empty size.
 */
@OptIn(ExperimentalForeignApi::class)
class ComposeZeroSizeContainerTest {

    @Test
    fun testZeroSizeViewDoesNotComposeOrDraw() = runZeroSizeTest(CGRectZero.readValue()) {
        awaitFrames(1)

        assertNotComposedOrDrawn()
    }

    @Test
    fun testZeroHeightViewDoesNotComposeOrDraw() =
        runZeroSizeTest(CGRectMake(0.0, 0.0, TargetWidth, 0.0)) {
            awaitFrames(1)

            assertNotComposedOrDrawn()
        }

    @Test
    fun testZeroWidthViewDoesNotComposeOrDraw() =
        runZeroSizeTest(CGRectMake(0.0, 0.0, 0.0, TargetHeight)) {
            awaitFrames(1)

            assertNotComposedOrDrawn()
        }

    @Test
    fun testZeroSizeViewComposesAndDrawsAfterResize() = runZeroSizeTest(CGRectZero.readValue()) {
        awaitFrames(1)
        assertNotComposedOrDrawn()

        view.setFrame(TargetFrame)
        waitForIdle()

        assertEquals(1, compositionsCount, "Content should be composed once after the resize")
        assertTrue(drawsCount > 0, "Content should be drawn after the resize")
        assertListIsLaidOutWithRealSize()
    }

    @Test
    fun testScrollAfterResizeToZeroHeight() = runZeroSizeTest(TargetFrame) {
        waitForIdle()
        assertListIsLaidOutWithRealSize()

        resize(CGRectMake(0.0, 0.0, TargetWidth, 0.0))

        listState.requestScrollToItem(50)
        waitForIdle()

        assertEquals(50, listState.firstVisibleItemIndex, "The list should be scrolled")
    }

    @Test
    fun testViewResizedToZeroAndBack() = runZeroSizeTest(TargetFrame) {
        waitForIdle()
        assertListIsLaidOutWithRealSize()
        listState.requestScrollToItem(10)
        waitForIdle()

        resize(CGRectZero.readValue())
        resize(TargetFrame)

        assertListIsLaidOutWithRealSize()
        assertEquals(10, listState.firstVisibleItemIndex, "The scroll position should be kept")

        listState.requestScrollToItem(50)
        waitForIdle()

        val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
        assertTrue(50 in visibleIndices, "The item 50 should be visible, visible: $visibleIndices")
    }

    private class ZeroSizeTestScope(
        val test: UIKitInstrumentedTest,
        val view: ComposeHostingView,
        val listState: LazyListState,
        private val compositions: () -> Int,
        private val draws: () -> Int,
    ) {
        val compositionsCount: Int get() = compositions()
        val drawsCount: Int get() = draws()

        fun waitForIdle() = test.waitForIdle()

        /**
         * Resizes the view and waits until the list is laid out with the new size.
         *
         * [ComposeHostingView] may apply the new frame to its content asynchronously (it checks
         * for a running size transition first), and that pending update isn't tracked by
         * [waitForIdle], so wait for the resulting viewport size explicitly.
         */
        fun resize(frame: CValue<CGRect>) {
            view.setFrame(frame)
            val expectedViewportSize = frame.useContents {
                val scale = UIScreen.mainScreen.scale
                IntSize(
                    width = (size.width * scale).roundToInt(),
                    height = (size.height * scale).roundToInt(),
                )
            }
            test.waitUntil("The list should be laid out with the size $expectedViewportSize") {
                listState.layoutInfo.viewportSize == expectedViewportSize
            }
            waitForIdle()
        }

        /**
         * Requests frames from the window's [FrameChoreographer] and waits until [count] frames
         * have been fully processed, including their out-of-frame phase.
         */
        fun awaitFrames(count: Int) {
            val choreographer = checkNotNull(test.frameChoreographer) { "No frame choreographer" }
            var finishedFrames = 0
            val listener = object : FrameChoreographer.Listener {
                override fun onDisplayLinkTick() {
                    // Keep the display link running until all the frames are produced.
                    choreographer.requestFrame()
                }

                override fun onOutOfFrame(
                    lastFrameTimestamp: NSTimeInterval,
                    targetTimestamp: NSTimeInterval
                ) {
                    finishedFrames++
                }
            }
            choreographer.addListener(listener)
            choreographer.requestFrame()
            try {
                test.waitUntil("$count frames should be produced") { finishedFrames >= count }
            } finally {
                choreographer.removeListener(listener)
            }
        }

        fun assertNotComposedOrDrawn() {
            assertEquals(0, compositionsCount, "Content should not be composed while view is empty")
            assertEquals(0, drawsCount, "Content should not be drawn while the view is empty")
        }

        fun assertListIsLaidOutWithRealSize() {
            val scale = UIScreen.mainScreen.scale
            assertEquals(
                IntSize((TargetWidth * scale).roundToInt(), (TargetHeight * scale).roundToInt()),
                listState.layoutInfo.viewportSize,
                "The list should be laid out with the real view size"
            )
            assertTrue(
                listState.layoutInfo.visibleItemsInfo.size > 1,
                "The list should lay out visible items, " +
                    "but got ${listState.layoutInfo.visibleItemsInfo.size}"
            )
        }
    }

    private fun runZeroSizeTest(
        initialFrame: CValue<CGRect>,
        block: ZeroSizeTestScope.() -> Unit
    ) = runUIKitInstrumentedTest(useHostingView = true) {
        val listState = LazyListState()
        var compositionsCount = 0
        var drawsCount = 0

        val view = createComposeHostingView {
            SideEffect { compositionsCount++ }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawsCount++
                    drawContent()
                }
            ) {
                items(100) { index ->
                    BasicText(
                        text = "Item $index",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ItemHeight)
                    )
                }
            }
        }

        // `setupWindow` waits for idle, which never happens while the view is empty:
        // the pending content keeps the view invalidated. Set up the window directly instead.
        val controller = UIViewController()
        appDelegate.setUpWindow(controller)
        view.setFrame(initialFrame)
        controller.view.addSubview(view)

        try {
            ZeroSizeTestScope(
                test = this,
                view = view,
                listState = listState,
                compositions = { compositionsCount },
                draws = { drawsCount },
            ).block()
        } finally {
            // Detach the view so the tear down doesn't wait for a view that is still empty.
            view.removeFromSuperview()
        }
    }

    private companion object {
        val ItemHeight = 40.dp
        val TargetFrame = CGRectMake(0.0, 0.0, 300.0, 500.0)
        val TargetWidth get() = CGRectGetWidth(TargetFrame)
        val TargetHeight get() = CGRectGetHeight(TargetFrame)
    }
}
