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

package androidx.compose.ui.window

import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSValue
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.valueWithCGRect

class KeyboardInsetsManagerTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testKeyboardInsetIsZeroWhenKeyboardIsHidden() {
        val view = UIView()
        var overlap = 0.dp
        val manager = KeyboardInsetsManager(
            view = view,
            frameChoreographer = makeFrameChoreographer(),
            onKeyboardOverlapHeightChanged = { overlap = it },
        )
        manager.start()
        try {
            assertEquals(0.dp, overlap)
        } finally {
            manager.dispose()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testUndockedKeyboardReturnsKeyboardOverlapHeightToZero() {
        val view = UIView(frame = CGRectMake(0.0, 0.0, 100.0, 100.0))
        val viewBottom = 1_000.0
        val safeAreaBottom = 20.0
        var layoutGuideHeight = 400.0
        var inset = 0.dp
        val manager = KeyboardInsetsManager(
            view = view,
            frameChoreographer = makeFrameChoreographer(),
            onKeyboardOverlapHeightChanged = { inset = it },
            keyboardOverlapHeightSource = {
                keyboardOverlapHeightForKeyboardLayoutGuide(
                    viewBottom = viewBottom,
                    layoutGuideBottom = viewBottom,
                    layoutGuideHeight = layoutGuideHeight,
                    safeAreaBottom = safeAreaBottom,
                )
            },
        )
        manager.start()
        try {
            assertEquals(400.dp, inset)

            // UIKit's default keyboardLayoutGuide stops following an undocked keyboard and
            // becomes the safe-area guide, while keyboard notifications still report it visible.
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = UIKeyboardWillChangeFrameNotification,
                `object` = null,
                userInfo = mapOf(
                    UIKeyboardFrameEndUserInfoKey to NSValue.valueWithCGRect(
                        CGRectMake(600.0, 400.0, 300.0, 200.0),
                    ),
                    UIKeyboardAnimationDurationUserInfoKey to 0.0,
                ),
            )
            layoutGuideHeight = safeAreaBottom
            manager.updateOverlapForCurrentGeometry()

            assertEquals(0.dp, inset)
        } finally {
            manager.dispose()
        }
    }

    /*
     * TODO: Replace this test with one that observes UIKit's presentation-layer animation.
     * The previous deterministic clock seam was removed because it did not exercise the
     * UIKit-timed production animation.
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testImePaddingKeepsAnimatingWhenKeyboardHidesAfterFrameChange() {
        val view = UIView(frame = CGRectMake(0.0, 0.0, 100.0, 100.0))
        var guideInset = 400.dp
        var time = 0.0
        var inset = 0.dp
        val manager = KeyboardInsetsManager(
            view = view,
            frameChoreographer = makeFrameChoreographer(),
            onKeyboardOverlapHeightChanged = { inset = it },
            keyboardOverlapHeightSource = { guideInset },
        )
        manager.start()
        try {
            guideInset = 70.dp
            manager.keyboardWillChangeFrame(
                currentFrame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                targetFrame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                duration = 2.0,
                animationOptions = UIViewAnimationOptionCurveLinear,
            )
            time = 0.5
            manager.onDisplayLinkTick()
            val insetBeforeHide = inset
            assertTrue(insetBeforeHide > 70.dp, "The transition to the shortcut bar must have started")

            guideInset = 0.dp
            manager.keyboardWillHide(
                currentFrame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                targetFrame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                duration = 2.0,
                animationOptions = UIViewAnimationOptionCurveLinear,
            )
            manager.onDisplayLinkTick()
            assertEquals(insetBeforeHide, inset, "Hide must start from the displayed inset")

            time = 1.0
            manager.onDisplayLinkTick()
            assertTrue(inset < insetBeforeHide, "The inset must continue moving while hiding")
            assertTrue(inset > 70.dp, "Hide must not jump to the shortcut-bar inset")

            time = 2.5
            manager.onDisplayLinkTick()
            assertEquals(0.dp, inset)
        } finally {
            manager.dispose()
        }
    }
     */

    private companion object {
        // FrameChoreographer holds a weak reference to its UIWindow.
        private val retainedWindows = mutableListOf<UIWindow>()

        fun makeFrameChoreographer(): FrameChoreographer {
            val window = UIWindow()
            retainedWindows.add(window)
            return FrameChoreographer.choreographerForWindow(window)
        }
    }
}
