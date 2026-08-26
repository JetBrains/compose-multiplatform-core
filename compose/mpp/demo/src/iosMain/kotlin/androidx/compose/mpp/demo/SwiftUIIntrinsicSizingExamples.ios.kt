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
import androidx.compose.ui.uikit.PreferredSizeReportingStrategy
import androidx.compose.ui.window.ComposeUIView
import platform.UIKit.UIView
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
internal fun swiftUIIntrinsicSizingExamples(
    makeSizingDemoController: (UIView, SwiftUIIntrinsicSizingExample) -> UIViewController,
) = Screen.Selection(
    "Compose in SwiftUI + intrinsic",
    Screen.Example("Fixed width, fitted height (intrinsic)") {
        UIKitViewController(
            factory = {
                makeSizingDemoController(
                    intrinsicComposeUIView { FixedWidthFittedHeightComposeContent() },
                    SwiftUIIntrinsicSizingExample.FIXED_WIDTH_FITTED_HEIGHT,
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
                    SwiftUIIntrinsicSizingExample.FIXED_HEIGHT_FITTED_WIDTH,
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
                    SwiftUIIntrinsicSizingExample.NATURAL_SIZE_COMPOSE_CONTENT_CHANGES,
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
                    SwiftUIIntrinsicSizingExample.FILL_AVAILABLE_WIDTH_FIXED_HEIGHT,
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
                    SwiftUIIntrinsicSizingExample.FIXED_WIDTH_FILL_AVAILABLE_HEIGHT,
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
                    SwiftUIIntrinsicSizingExample.FILL_BOTH_AVAILABLE_AXES,
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
                    SwiftUIIntrinsicSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT,
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
                    SwiftUIIntrinsicSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    },
)

@OptIn(ExperimentalComposeUiApi::class)
private fun intrinsicComposeUIView(content: @Composable () -> Unit): UIView = ComposeUIView(
    configure = {
        preferredSizeReportingStrategy = PreferredSizeReportingStrategy.IntrinsicContentSize
    },
    content = content,
)

enum class SwiftUIIntrinsicSizingExample {
    FIXED_WIDTH_FITTED_HEIGHT,
    FIXED_HEIGHT_FITTED_WIDTH,
    NATURAL_SIZE_COMPOSE_CONTENT_CHANGES,
    FILL_AVAILABLE_WIDTH_FIXED_HEIGHT,
    FIXED_WIDTH_FILL_AVAILABLE_HEIGHT,
    FILL_BOTH_AVAILABLE_AXES,
    FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT,
    FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH,
}
