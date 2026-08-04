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
internal fun swiftUISizeThatFitsSizingExamples(
    makeSizingDemoController: (UIView, Int) -> UIViewController,
) = Screen.Selection(
    "Compose in SwiftUI + sizeThatFits",
    Screen.Example("Fixed width, fitted height") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FixedWidthFittedHeightComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FixedWidthFittedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fixed height, fitted width") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FixedHeightFittedWidthComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FixedHeightFittedWidth,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Natural size, Compose content changes") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { NaturalSizeComposeContentChangeContent() },
                    SwiftUISizeThatFitsSizingExampleId.NaturalSizeComposeContentChanges,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill available width, fixed height") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FillAvailableWidthFixedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fixed width, fill available height") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FixedWidthFillAvailableHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both available axes") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FillAvailableSpaceComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FillBothAvailableAxes,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both axes, Compose fixed height") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FixedHeightComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FillBothAxesComposeFixedHeight,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
    Screen.Example("Fill both axes, Compose fixed width") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    ComposeUIView { FixedWidthComposeContent() },
                    SwiftUISizeThatFitsSizingExampleId.FillBothAxesComposeFixedWidth,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
)

internal object SwiftUISizeThatFitsSizingExampleId {
    const val FixedWidthFittedHeight = 0
    const val FixedHeightFittedWidth = 1
    const val NaturalSizeComposeContentChanges = 3
    const val FillAvailableWidthFixedHeight = 4
    const val FixedWidthFillAvailableHeight = 5
    const val FillBothAvailableAxes = 6
    const val FillBothAxesComposeFixedHeight = 7
    const val FillBothAxesComposeFixedWidth = 8
}
