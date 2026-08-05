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
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.uikit.SizeReportingStrategy
import androidx.compose.ui.window.ComposeUIView
import platform.UIKit.UIView
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
internal fun swiftUIIntrinsicSizingExamples(
    makeSizingDemoController: (UIView, Int) -> UIViewController,
) = Screen.Selection(
    "Compose in SwiftUI + intrinsic",
    Screen.Example("Fixed width, fitted height (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FixedWidthFittedHeightComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FixedWidthFittedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fixed height, fitted width (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FixedHeightFittedWidthComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FixedHeightFittedWidth,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Natural size, Compose content changes (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { NaturalSizeComposeContentChangeContent() },
                    SwiftUIIntrinsicSizingExampleId.NaturalSizeComposeContentChanges,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill available width, fixed height (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FillAvailableWidthFixedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fixed width, fill available height (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FixedWidthFillAvailableHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both available axes (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FillBothAvailableAxes,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both axes, Compose fixed height (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FixedHeightComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FillBothAxesComposeFixedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both axes, Compose fixed width (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FixedWidthComposeContent() },
                    SwiftUIIntrinsicSizingExampleId.FillBothAxesComposeFixedWidth,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
)

@OptIn(ExperimentalComposeUiApi::class)
private fun intrinsicComposeUIView(content: @Composable () -> Unit): UIView = ComposeUIView(
    configure = {
        sizeReportingStrategy = SizeReportingStrategy.IntrinsicContentSize
    },
    content = content,
)

internal object SwiftUIIntrinsicSizingExampleId {
    const val FixedWidthFittedHeight = 10
    const val FixedHeightFittedWidth = 11
    const val NaturalSizeComposeContentChanges = 13
    const val FillAvailableWidthFixedHeight = 14
    const val FixedWidthFillAvailableHeight = 15
    const val FillBothAvailableAxes = 16
    const val FillBothAxesComposeFixedHeight = 17
    const val FillBothAxesComposeFixedWidth = 18
}
