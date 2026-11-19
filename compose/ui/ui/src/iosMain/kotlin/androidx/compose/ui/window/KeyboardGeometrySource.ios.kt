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

import kotlin.math.abs
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectIsEmpty
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView

internal interface KeyboardGeometrySource {
    val keyboardFrame: CValue<CGRect>
}

/**
 * Reads keyboard geometry from the view's keyboard layout guide. Unlike keyboard notifications,
 * the guide includes custom input and accessory views.
 */
internal class KeyboardLayoutGuideGeometrySource(
    private val view: UIView
) : KeyboardGeometrySource {
    private val canDisableBottomSafeArea = available(OS.Ios to OSVersion(major = 17))

    init {
        if (canDisableBottomSafeArea) {
            // The safe-area frame is indistinguishable from a hidden keyboard. Using the view's
            // bottom edge makes the hidden layout-guide frame empty.
            view.keyboardLayoutGuide.usesBottomSafeArea = false
        }
    }

    override val keyboardFrame: CValue<CGRect>
        get() {
            val frame = view.keyboardLayoutGuide.layoutFrame
            // A hidden guide can be tied to the bottom safe area instead of becoming empty.
            // Normalize that safe-area-only frame so it is not reported as keyboard geometry.
            return if (isBottomSafeAreaFrame(frame)) {
                CGRectZero.readValue()
            } else {
                frame
            }
        }

    private fun isBottomSafeAreaFrame(frame: CValue<CGRect>): Boolean {
        if (CGRectIsEmpty(frame)) return false

        val viewHeight = view.bounds.useContents { size.height }
        val bottomSafeArea = view.safeAreaInsets.useContents { bottom }
        return frame.useContents {
            abs(origin.y - (viewHeight - bottomSafeArea)) <= KEYBOARD_BOTTOM_EDGE_TOLERANCE &&
                abs(size.height - bottomSafeArea) <= KEYBOARD_BOTTOM_EDGE_TOLERANCE
        }
    }

    private companion object {
        const val KEYBOARD_BOTTOM_EDGE_TOLERANCE = 0.5
    }
}

/**
 * Combines keyboard notification geometry with the view's keyboard layout guide.
 *
 * iOS 26.5 can under-report the system keyboard in the layout guide while a zero-sized accessory
 * view is installed, while iOS 27.2 can under-report custom input views in notifications. Use the
 * taller visible frame and trust the notification's empty frame after the keyboard hides.
 */
internal class KeyboardFrameSource(
    view: UIView,
    private val layoutGuideSource: KeyboardGeometrySource = KeyboardLayoutGuideGeometrySource(view),
    private val notificationSource: KeyboardGeometrySource = KeyboardVisibilityListener
) : KeyboardGeometrySource {
    override val keyboardFrame: CValue<CGRect>
        get() {
            val notificationFrame = notificationSource.keyboardFrame
            val layoutGuideFrame = layoutGuideSource.keyboardFrame

            if (CGRectIsEmpty(notificationFrame)) return notificationFrame
            if (CGRectIsEmpty(layoutGuideFrame)) return notificationFrame
            return if (CGRectGetMinY(layoutGuideFrame) < CGRectGetMinY(notificationFrame)) {
                layoutGuideFrame
            } else {
                notificationFrame
            }
        }
}
