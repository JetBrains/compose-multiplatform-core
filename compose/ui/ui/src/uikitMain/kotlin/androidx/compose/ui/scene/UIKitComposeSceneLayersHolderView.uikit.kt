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

package androidx.compose.ui.scene

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIEvent
import platform.UIKit.UIView

/**
 * A view that hosts the [ComposeScene] layers and a metal view shared by all of them.
 */
internal class UIKitComposeSceneLayersHolderView: UIView(frame = CGRectZero.readValue()) {
    /**
     * This view is transparent for touches, unless a child view is hit-tested.
     */
    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? {
        val result = super.hitTest(point, withEvent)

        return if (result == this) {
            null
        } else {
            result
        }
    }
}