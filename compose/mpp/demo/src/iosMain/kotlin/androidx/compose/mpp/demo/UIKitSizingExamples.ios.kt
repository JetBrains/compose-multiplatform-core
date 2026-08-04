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

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.window.ComposeUIView
import platform.UIKit.UIView
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
internal fun uiKitSizingExamples(
    makeSizingDemoController: (UIView, Int) -> UIViewController,
) = Screen.Selection(
    "Compose in UIKit",
    Screen.Example("Fixed width, fitted height (UIKit)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FixedWidthFittedHeightComposeContent() },
                    UIKitSizingExampleId.FixedWidthFittedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Compose content changes natural size") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { NaturalSizeComposeContentChangeContent() },
                    UIKitSizingExampleId.ComposeContentChangesFittedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill UIKit's constrained bounds") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FillAvailableSpaceComposeContent() },
                    UIKitSizingExampleId.FillConstrainedBounds,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
)

internal object UIKitSizingExampleId {
    const val FixedWidthFittedHeight = 20
    const val ComposeContentChangesFittedHeight = 21
    const val FillConstrainedBounds = 22
}
