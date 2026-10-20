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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIView
import platform.UIKit.UIViewController

// These types must be declared in this framework to be available to Swift without exporting
// compose:mpp:demo. SwiftHelper maps them to the corresponding demo enums internally.
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "SwiftUISizeThatFitsSizingExample")
enum class SwiftUISizeThatFitsSizingExampleSwiftBridge {
    FIXED_WIDTH_FITTED_HEIGHT,
    FIXED_HEIGHT_FITTED_WIDTH,
    NATURAL_SIZE_COMPOSE_CONTENT_CHANGES,
    FILL_AVAILABLE_WIDTH_FIXED_HEIGHT,
    FIXED_WIDTH_FILL_AVAILABLE_HEIGHT,
    FILL_BOTH_AVAILABLE_AXES,
    FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT,
    FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "SwiftUIIntrinsicSizingExample")
enum class SwiftUIIntrinsicSizingExampleSwiftBridge {
    FIXED_WIDTH_FITTED_HEIGHT,
    FIXED_HEIGHT_FITTED_WIDTH,
    NATURAL_SIZE_COMPOSE_CONTENT_CHANGES,
    FILL_AVAILABLE_WIDTH_FIXED_HEIGHT,
    FIXED_WIDTH_FILL_AVAILABLE_HEIGHT,
    FILL_BOTH_AVAILABLE_AXES,
    FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT,
    FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "UIKitSizingExample")
enum class UIKitSizingExampleSwiftBridge {
    FIXED_WIDTH_FITTED_HEIGHT,
    COMPOSE_CONTENT_CHANGES_FITTED_HEIGHT,
    FILL_CONSTRAINED_BOUNDS,
}

class SwiftHelper {
    fun getViewController(
        makeHostingViewController: (Int) -> UIViewController,
        makeSwiftUISizeThatFitsSizingDemoViewController: (UIView, SwiftUISizeThatFitsSizingExampleSwiftBridge) -> UIViewController,
        makeSwiftUIIntrinsicSizingDemoViewController: (UIView, SwiftUIIntrinsicSizingExampleSwiftBridge) -> UIViewController,
        makeUIKitSizingDemoViewController: (UIView, UIKitSizingExampleSwiftBridge) -> UIViewController,
    ): UIViewController = getViewControllerWithCompose(
        makeHostingViewController = makeHostingViewController,
        makeSwiftUISizeThatFitsSizingDemoViewController = { view, example ->
            makeSwiftUISizeThatFitsSizingDemoViewController(view, example.toSwiftExample())
        },
        makeSwiftUIIntrinsicSizingDemoViewController = { view, example ->
            makeSwiftUIIntrinsicSizingDemoViewController(view, example.toSwiftExample())
        },
        makeUIKitSizingDemoViewController = { view, example ->
            makeUIKitSizingDemoViewController(view, example.toSwiftExample())
        },
    )
}

private fun SwiftUISizeThatFitsSizingExample.toSwiftExample() = when (this) {
    SwiftUISizeThatFitsSizingExample.FIXED_WIDTH_FITTED_HEIGHT ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FIXED_WIDTH_FITTED_HEIGHT
    SwiftUISizeThatFitsSizingExample.FIXED_HEIGHT_FITTED_WIDTH ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FIXED_HEIGHT_FITTED_WIDTH
    SwiftUISizeThatFitsSizingExample.NATURAL_SIZE_COMPOSE_CONTENT_CHANGES ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.NATURAL_SIZE_COMPOSE_CONTENT_CHANGES
    SwiftUISizeThatFitsSizingExample.FILL_AVAILABLE_WIDTH_FIXED_HEIGHT ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FILL_AVAILABLE_WIDTH_FIXED_HEIGHT
    SwiftUISizeThatFitsSizingExample.FIXED_WIDTH_FILL_AVAILABLE_HEIGHT ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FIXED_WIDTH_FILL_AVAILABLE_HEIGHT
    SwiftUISizeThatFitsSizingExample.FILL_BOTH_AVAILABLE_AXES ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FILL_BOTH_AVAILABLE_AXES
    SwiftUISizeThatFitsSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT
    SwiftUISizeThatFitsSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH ->
        SwiftUISizeThatFitsSizingExampleSwiftBridge.FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH
}

private fun SwiftUIIntrinsicSizingExample.toSwiftExample() = when (this) {
    SwiftUIIntrinsicSizingExample.FIXED_WIDTH_FITTED_HEIGHT ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FIXED_WIDTH_FITTED_HEIGHT
    SwiftUIIntrinsicSizingExample.FIXED_HEIGHT_FITTED_WIDTH ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FIXED_HEIGHT_FITTED_WIDTH
    SwiftUIIntrinsicSizingExample.NATURAL_SIZE_COMPOSE_CONTENT_CHANGES ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.NATURAL_SIZE_COMPOSE_CONTENT_CHANGES
    SwiftUIIntrinsicSizingExample.FILL_AVAILABLE_WIDTH_FIXED_HEIGHT ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FILL_AVAILABLE_WIDTH_FIXED_HEIGHT
    SwiftUIIntrinsicSizingExample.FIXED_WIDTH_FILL_AVAILABLE_HEIGHT ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FIXED_WIDTH_FILL_AVAILABLE_HEIGHT
    SwiftUIIntrinsicSizingExample.FILL_BOTH_AVAILABLE_AXES ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FILL_BOTH_AVAILABLE_AXES
    SwiftUIIntrinsicSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FILL_BOTH_AXES_COMPOSE_FIXED_HEIGHT
    SwiftUIIntrinsicSizingExample.FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH ->
        SwiftUIIntrinsicSizingExampleSwiftBridge.FILL_BOTH_AXES_COMPOSE_FIXED_WIDTH
}

private fun UIKitSizingExample.toSwiftExample() = when (this) {
    UIKitSizingExample.FIXED_WIDTH_FITTED_HEIGHT ->
        UIKitSizingExampleSwiftBridge.FIXED_WIDTH_FITTED_HEIGHT
    UIKitSizingExample.COMPOSE_CONTENT_CHANGES_FITTED_HEIGHT ->
        UIKitSizingExampleSwiftBridge.COMPOSE_CONTENT_CHANGES_FITTED_HEIGHT
    UIKitSizingExample.FILL_CONSTRAINED_BOUNDS ->
        UIKitSizingExampleSwiftBridge.FILL_CONSTRAINED_BOUNDS
}
