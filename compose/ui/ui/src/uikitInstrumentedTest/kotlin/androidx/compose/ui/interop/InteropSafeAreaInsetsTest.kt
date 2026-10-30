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

package androidx.compose.ui.interop

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropSafeAreaInsetsPolicy
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.UIKit.UIViewController

class InteropSafeAreaInsetsTest {
    @Test
    fun defaultAutomaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(placedAsOverlay = overlay),
            )
        }

        waitUntil("Interop view was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun automaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Automatic,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Interop view was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun safeAreaInsetsPolicyUpdatesTheExistingUIKitView() = runUIKitInstrumentedTestWithInterop { overlay ->
        val policy = mutableStateOf<UIKitInteropSafeAreaInsetsPolicy>(
            UIKitInteropSafeAreaInsetsPolicy.Inherit,
        )
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = policy.value,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }

        policy.value = UIKitInteropSafeAreaInsetsPolicy.Automatic

        waitUntil("Automatic safe area policy was not applied") {
            view?.hasNoTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun safeAreaInsetsPolicyAndOverlayPlacementUpdateTogether() = runUIKitInstrumentedTest {
        val properties = mutableStateOf(
            UIKitInteropProperties(
                safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Inherit,
                placedAsOverlay = false,
            ),
        )
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = properties.value,
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true && view?.isWrappedInClippingView() == true
        }

        properties.value = UIKitInteropProperties(
            safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Automatic,
            placedAsOverlay = true,
        )

        waitUntil("Safe area policy and overlay placement were not updated") {
            view?.hasNoTopSafeAreaInsetFromSuperview() == true && view?.isWrappedInClippingView() == false
        }
    }

    @Test
    fun inheritPolicyExposesSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Inherit,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun ignorePolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Ignore,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Interop view was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun defaultAutomaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(placedAsOverlay = overlay),
            )
        }

        waitUntil("Interop view controller was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun automaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Automatic,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Interop view controller was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun safeAreaInsetsPolicyUpdatesTheExistingUIKitViewController() = runUIKitInstrumentedTestWithInterop { overlay ->
        val policy = mutableStateOf<UIKitInteropSafeAreaInsetsPolicy>(
            UIKitInteropSafeAreaInsetsPolicy.Inherit,
        )
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = policy.value,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }

        policy.value = UIKitInteropSafeAreaInsetsPolicy.Automatic

        waitUntil("Automatic safe area policy was not applied") {
            view?.hasNoTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun inheritPolicyExposesSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Inherit,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun ignorePolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTestWithInterop { overlay ->
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Ignore,
                    placedAsOverlay = overlay,
                ),
            )
        }

        waitUntil("Interop view controller was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }
}

private fun UIView.superviewTopSafeAreaInset(): Double =
    superview?.safeAreaInsets?.useContents { top } ?: Double.NaN

private fun SafeAreaTrackingView.hasTopSafeAreaInsetFromSuperview(): Boolean =
    superviewTopSafeAreaInset() > 0.0 && lastLayoutSuperviewTopSafeAreaInset > 0.0

private fun SafeAreaTrackingView.hasNoTopSafeAreaInsetFromSuperview(): Boolean =
    superviewTopSafeAreaInset() == 0.0 && lastLayoutSuperviewTopSafeAreaInset == 0.0

private fun SafeAreaTrackingView.hasBeenLaidOutInWindow(): Boolean =
    window != null && !lastLayoutSuperviewTopSafeAreaInset.isNaN()

private fun SafeAreaTrackingView.isWrappedInClippingView(): Boolean? =
    superview?.superview?.clipsToBounds

private class SafeAreaTrackingView : UIView(frame = CGRectZero.readValue()) {
    var lastLayoutSuperviewTopSafeAreaInset = Double.NaN
        private set

    override fun layoutSubviews() {
        super.layoutSubviews()
        lastLayoutSuperviewTopSafeAreaInset = superviewTopSafeAreaInset()
    }
}
