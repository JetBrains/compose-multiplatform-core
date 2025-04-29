/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.dpRectInWindow
import androidx.compose.ui.test.utils.forEachWithPrevious
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import platform.UIKit.UIView

// TODO: https://youtrack.jetbrains.com/issue/CMP-7991 Does not work with new Launcher. Rework the test.
@Ignore
internal class KeyboardInsetsTest {
    companion object {
        // Maximum duration of a frame that is not considered as a frame drop.
        // Warning: The test can be flaky on slow agents. Consider increasing the duration or
        // using multiple retries in this case.
        val maxFrameDuration = 1.seconds / 60 * 1.7
    }

    @Test
    fun testImeInsetsAnimationFrames() = runUIKitInstrumentedTest {
        val contentFrames = mutableListOf<DpRect>()
        var lastContentFrame = DpRect(DpOffset.Unspecified, DpSize.Unspecified)

        setContent {
            Box(Modifier
                .fillMaxSize()
                .imePadding()
                .onGloballyPositioned { coordinates ->
                    // Since you can have multiple layouts per render cycle, remember the last
                    // one and add it for further analysis during the render phase.
                    lastContentFrame = coordinates.boundsInWindow().toDpRect(density)
                }
                .drawWithContent {
                    contentFrames.add(lastContentFrame)
                }
            )
        }

        val screenRect = DpRect(origin = DpOffset.Zero, size = screenSize)

        assertEquals(screenRect, contentFrames.last())
        assertTrue(contentFrames.all { it == screenRect })

        // Show keyboard with animation
        // showKeyboard(animated = true)
        contentFrames.clear()
        waitForIdle()

        val visibleRect = DpRect(
            left = 0.dp,
            top = 0.dp,
            right = screenSize.width,
            bottom = screenSize.height - keyboardHeight
        )

        assertTrue(contentFrames.count() > 5, "Animation should produce large number of frames")
        assertEquals(visibleRect, contentFrames.last(), "")
        contentFrames.forEach {
            assertEquals(0.dp, it.top, "Content must be top-aligned")
        }
        contentFrames.forEachWithPrevious { previousFrame, nextFrame ->
            assertTrue(
                nextFrame.bottom <= previousFrame.bottom,
                "Content must shrink up on every frame"
            )
            assertTrue(
                nextFrame.height <= previousFrame.height,
                "Content size must decrease on every frame"
            )
        }

        // Hide keyboard with animation
        contentFrames.clear()
        waitForIdle()

        assertTrue(contentFrames.count() > 5, "Animation should produce large number of frames")
        assertEquals(screenRect, contentFrames.last())
        contentFrames.forEach {
            assertEquals(0.dp, it.top, "Content must be top-aligned")
        }
        contentFrames.forEachWithPrevious { previousFrame, nextFrame ->
            assertTrue(
                actual = nextFrame.bottom >= previousFrame.bottom,
                "Content must expand down on every frame"
            )
            assertTrue(
                actual = nextFrame.height >= previousFrame.height,
                "Content size must increase on every frame"
            )
        }
    }

    @Ignore // Became flaky. TODO: Investigate performance problem.
    @Test
    fun testImeInsetsAnimationFrameRate() = runUIKitInstrumentedTest {
        val refreshTimings = mutableListOf<ValueTimeMark>()

        setContent {
            Box(Modifier
                .fillMaxSize()
                .imePadding()
                .drawWithContent {
                    refreshTimings.add(TimeSource.Monotonic.markNow())
                }
            )
        }

        // Show keyboard with animation
        refreshTimings.clear()
        waitForIdle()

        refreshTimings.forEachWithPrevious { previous, next ->
            assertTrue(next - previous < maxFrameDuration)
        }

        // Hide keyboard with animation
        refreshTimings.clear()
        waitForIdle()

        refreshTimings.forEachWithPrevious { previous, next ->
            assertTrue(next - previous < maxFrameDuration)
        }
    }

    @Ignore // Became flaky. TODO: Investigate performance problem.
    @Test
    fun testImeInsetsAnimationFrameRateWithFocusedTextField() =
        runUIKitInstrumentedTest {
            // Maximum duration of a frame that is not considered as a frame drop
            // Warning: The test can be flaky on slow agents. Consider using multiple retries
            // in this case scenario
            val maxFrameDuration = 1.seconds / 60 * 1.7

            val refreshTimings = mutableListOf<ValueTimeMark>()
            val focusRequester = FocusRequester()

            setContent {
                Box(Modifier
                    .fillMaxSize()
                    .imePadding()
                    .drawWithContent {
                        refreshTimings.add(TimeSource.Monotonic.markNow())
                    }
                ) {
                    TextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            // Show keyboard with animation
            focusRequester.requestFocus()
            // showKeyboard(animated = true)
            refreshTimings.clear()
            waitForIdle()

            refreshTimings.forEachWithPrevious { previous, next ->
                assertTrue(next - previous < maxFrameDuration)
            }

            // Hide keyboard with animation
            focusRequester.freeFocus()
            refreshTimings.clear()
            waitForIdle()

            refreshTimings.forEachWithPrevious { previous, next ->
                assertTrue(next - previous < maxFrameDuration)
            }
        }

    @Test
    fun testFocusableAboveKeyboardOffsetBehavior() =
        runUIKitInstrumentedTest {
            var textRectInWindow: DpRect? = null
            var textRectInRoot: DpRect? = null
            val bottomPadding = 100.dp
            val interopView = UIView()

            val focusRequester = FocusRequester()
            setContent({
                onFocusBehavior = OnFocusBehavior.FocusableAboveKeyboard
            }) {
                Box(Modifier.padding(bottom = bottomPadding)) {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize()
                    )
                    TextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .align(Alignment.BottomCenter)
                            .onGloballyPositioned { coordinates ->
                                textRectInWindow = coordinates.boundsInWindow().toDpRect(density)
                                textRectInRoot = coordinates.boundsInRoot().toDpRect(density)
                            }
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    // showKeyboard(animated = false)
                }
            }

            assertEquals(screenSize.height - keyboardHeight, textRectInWindow?.bottom)
            assertEquals(screenSize.height - keyboardHeight, textRectInRoot?.bottom)
            assertEquals(screenSize.height - keyboardHeight, interopView.dpRectInWindow().bottom)

            focusRequester.freeFocus()

            waitForIdle()
            assertEquals(screenSize.height - bottomPadding, textRectInWindow?.bottom)
            assertEquals(screenSize.height - bottomPadding, textRectInRoot?.bottom)
            assertEquals(screenSize.height - bottomPadding, interopView.dpRectInWindow().bottom)
        }

    @Test
    fun testFocusableAboveKeyboardRefocusBehavior() =
        runUIKitInstrumentedTest {
            var text1RectInWindow: DpRect? = null
            var text2RectInWindow: DpRect? = null
            var text3RectInWindow: DpRect? = null

            val focusRequester1 = FocusRequester()
            val focusRequester2 = FocusRequester()
            val focusRequester3 = FocusRequester()
            setContent({
                onFocusBehavior = OnFocusBehavior.FocusableAboveKeyboard
            }) {
                @Composable
                fun TestTextField(
                    requester: FocusRequester,
                    onPositionInWindowChanged: (DpRect) -> Unit
                ) = TextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .focusRequester(requester)
                        .onGloballyPositioned {
                            onPositionInWindowChanged(it.boundsInWindow().toDpRect(density))
                        }
                )

                Column(Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(1f))
                    TestTextField(focusRequester1) { text1RectInWindow = it }
                    TestTextField(focusRequester2) { text2RectInWindow = it }
                    TestTextField(focusRequester3) { text3RectInWindow = it }
                }

                LaunchedEffect(Unit) {
                    focusRequester1.requestFocus()
                    // showKeyboard(animated = false)
                }
            }

            assertEquals(screenSize.height - keyboardHeight, text1RectInWindow?.bottom)
            assertNotEquals(screenSize.height - keyboardHeight, text2RectInWindow?.bottom)
            assertNotEquals(screenSize.height - keyboardHeight, text3RectInWindow?.bottom)

            focusRequester2.requestFocus()
            waitForIdle()

            assertNotEquals(screenSize.height - keyboardHeight, text1RectInWindow?.bottom)
            assertEquals(screenSize.height - keyboardHeight, text2RectInWindow?.bottom)
            assertNotEquals(screenSize.height - keyboardHeight, text3RectInWindow?.bottom)

            focusRequester3.requestFocus()
            waitForIdle()

            assertNotEquals(screenSize.height - keyboardHeight, text1RectInWindow?.bottom)
            assertNotEquals(screenSize.height - keyboardHeight, text2RectInWindow?.bottom)
            assertEquals(screenSize.height - keyboardHeight, text3RectInWindow?.bottom)
        }
}
