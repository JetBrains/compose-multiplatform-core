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

package androidx.compose.ui.window

import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIWindow

internal class ComposeView(
    private var onDidMoveToWindow: (UIWindow?) -> Unit,
    private var onLayoutSubviews: () -> Unit,
    useOpaqueConfiguration: Boolean
): UIView(frame = CGRectZero.readValue()) {
    init {
        setClipsToBounds(true)
        setOpaque(useOpaqueConfiguration)
        backgroundColor = if (useOpaqueConfiguration) UIColor.whiteColor else UIColor.clearColor
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()

        onDidMoveToWindow(window)

        // To avoid a situation where a user decided to call [layoutIfNeeded] on the detached view
        // using a certain frame and it will be attached to the window later, so there is a chance
        // that [onLayoutSubviews] will not be called when a [window] is set.
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()

        onLayoutSubviews()
    }

    override fun safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()

        setNeedsLayout()
    }

    fun dispose() {
        onDidMoveToWindow = {}
        onLayoutSubviews = {}
    }
}