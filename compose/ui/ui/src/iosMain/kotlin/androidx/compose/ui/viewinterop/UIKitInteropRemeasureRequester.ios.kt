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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * A handle that allows explicitly requesting remeasurement of [UIKitView] or [UIKitViewController].
 *
 * Calling [requestRemeasure] schedules a new Compose measurement pass for the associated interop node.
 * During that measurement pass, Compose will re-query UIKit for the view's fitting size and may update
 * the Compose size of the [UIKitView] or [UIKitViewController] accordingly.
 *
 * Conceptually, this is the Compose interop equivalent of “trigger a new layout/measurement pass”
 * (similar in spirit to UIKit's `setNeedsLayout()` / `layoutIfNeeded()`), but it operates on the
 * Compose side of the layout pipeline.
 *
 * UIKit does not reliably notify Compose when a UIKit view's intrinsic/fitting size changes due to
 * internal state updates. Call [requestRemeasure] after changes that can affect fitting size, such as
 *  - changing `UILabel.text` / `attributedText` / `font`,
 *  - updating internal Auto Layout constraint constants,
 *  - adding/removing subviews that affect layout.
 *
 * This requester is intended to be associated with a single [UIKitView] or [UIKitViewController] instance at a time.
 * If the same requester instance is passed to multiple [UIKitView] or [UIKitViewController] nodes, the last one wins.
 *
 * @see rememberUIKitInteropRemeasureRequester
 */
@Stable
@ExperimentalComposeUiApi
class UIKitInteropRemeasureRequester @RememberInComposition constructor() {
    internal var requestImpl: (() -> Unit)? = null

    /**
     * Requests remeasurement of the associated [UIKitView] or [UIKitViewController], if attached.
     *
     * If this requester is not currently attached to any [UIKitView] or [UIKitViewController], this is a no-op.
     */
    fun requestRemeasure() {
        requestImpl?.invoke()
    }
}

@Composable
@ExperimentalComposeUiApi
fun rememberUIKitInteropRemeasureRequester(): UIKitInteropRemeasureRequester =
    remember { UIKitInteropRemeasureRequester() }