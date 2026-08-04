/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.mpp.demo

import platform.UIKit.UIView
import platform.UIKit.UIViewController

class SwiftHelper {
    fun getViewController(
        makeHostingViewController: (Int) -> UIViewController,
        makeSizingDemoViewController: (UIView, Int) -> UIViewController,
    ): UIViewController = getViewControllerWithCompose(
        makeHostingViewController = makeHostingViewController,
        makeSizingDemoViewController = makeSizingDemoViewController,
    )

    fun getComposeSizingDemoView(): UIView = androidx.compose.mpp.demo.getComposeSizingDemoView()

    fun getComposeHostedSizingDemoView(): UIView =
        androidx.compose.mpp.demo.getComposeHostedSizingDemoView()

    fun getUIKitInteropSizingDemoView(): UIView =
        androidx.compose.mpp.demo.getUIKitInteropSizingDemoView()
}
