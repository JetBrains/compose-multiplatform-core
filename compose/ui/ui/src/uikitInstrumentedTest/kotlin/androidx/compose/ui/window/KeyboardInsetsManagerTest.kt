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

import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSValue
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIView
import platform.UIKit.valueWithCGRect

internal class KeyboardInsetsManagerTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testKeyboardInsetStaysZeroUntilKeyboardReachesView() = runUIKitInstrumentedTest {
        animationSpeed = UIKitInstrumentedTest.IncreasedAnimationSpeed
        setContent {}
        assertEquals(0.dp, keyboardHeight)

        val width = screenSize.width.value.toDouble()
        val height = screenSize.height.value.toDouble()
        val view = UIView(frame = CGRectMake(0.0, 100.0, width, height - 200.0))
        viewController.view.addSubview(view)
        var overlap = 0.dp
        val manager = KeyboardInsetsManager(view, checkNotNull(frameChoreographer)) { overlap = it }
        manager.start()
        try {
            postKeyboardFrame(CGRectMake(0.0, height - 300.0, width, 300.0), duration = 2.0)
            val animationView = view.subviews.single() as UIView
            fun progress(): Double {
                val layer = animationView.layer.presentationLayer() ?: return 0.0
                return layer.frame.useContents { size.height } /
                    animationView.layer.frame.useContents { size.height }
            }
            waitUntil("Keyboard animation must start") { progress() >= 0.1 }
            assertTrue(progress() < 1.0 / 3.0, "Must sample before the keyboard reaches the view")
            manager.onDisplayLinkTick()
            assertEquals(0.dp, overlap, "Keyboard is still below the view")

            waitUntil("Keyboard animation must finish") { !manager.hasPendingWork }
            assertEquals(200.dp, overlap)
        } finally {
            manager.dispose()
            view.removeFromSuperview()
            postKeyboardWillHide()
        }
    }

    private fun postKeyboardFrame(frame: CValue<CGRect>, duration: Double) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            userInfo = mapOf(
                UIKeyboardFrameEndUserInfoKey to NSValue.valueWithCGRect(frame),
                UIKeyboardAnimationDurationUserInfoKey to duration,
            )
        )
    }

    private fun postKeyboardWillHide() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIKeyboardWillHideNotification,
            `object` = null,
            userInfo = mapOf(UIKeyboardAnimationDurationUserInfoKey to 0.0)
        )
    }
}
