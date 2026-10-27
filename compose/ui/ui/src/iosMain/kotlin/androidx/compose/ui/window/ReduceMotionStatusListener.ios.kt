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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.darwin.NSObject

internal class ReduceMotionStatusListener(
    private val notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter,
    private var onReduceMotionStatusChanged: () -> Unit
) : NSObject() {
    init {
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::reduceMotionStatusDidChange.name),
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null
        )
    }

    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun reduceMotionStatusDidChange() {
        onReduceMotionStatusChanged()
    }

    fun dispose() {
        onReduceMotionStatusChanged = {}
        notificationCenter.removeObserver(
            observer = this,
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null
        )
    }
}
