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
    fun defaultAutomaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTest {
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
            )
        }

        waitUntil("Interop view was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun automaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTest {
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Automatic,
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
    fun safeAreaInsetsPolicyUpdatesTheExistingUIKitView() = runUIKitInstrumentedTest {
        val policy = mutableStateOf<UIKitInteropSafeAreaInsetsPolicy>(
            UIKitInteropSafeAreaInsetsPolicy.Inherit,
        )
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(safeAreaInsetsPolicy = policy.value),
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
    fun inheritPolicyExposesSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTest {
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Inherit,
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun ignorePolicyDoesNotExposeSafeAreaInsetsFromTheInteropHost() = runUIKitInstrumentedTest {
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitView(
                factory = { SafeAreaTrackingView().also { view = it } },
                modifier = Modifier.size(100.dp),
                properties = UIKitInteropProperties(
                    safeAreaInsetsPolicy = UIKitInteropSafeAreaInsetsPolicy.Ignore,
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
    fun defaultAutomaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTest {
        var view: SafeAreaTrackingView? = null

        setContent {
            UIKitViewController(
                factory = {
                    UIViewController().also { controller ->
                        controller.view = SafeAreaTrackingView().also { view = it }
                    }
                },
                modifier = Modifier.size(100.dp),
            )
        }

        waitUntil("Interop view controller was not attached and laid out") {
            view?.hasBeenLaidOutInWindow() == true
        }

        assertEquals(0.0, view?.superviewTopSafeAreaInset())
        assertEquals(0.0, view?.lastLayoutSuperviewTopSafeAreaInset)
    }

    @Test
    fun automaticPolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTest {
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
    fun safeAreaInsetsPolicyUpdatesTheExistingUIKitViewController() = runUIKitInstrumentedTest {
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
                properties = UIKitInteropProperties(safeAreaInsetsPolicy = policy.value),
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
    fun inheritPolicyExposesSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTest {
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
                ),
            )
        }

        waitUntil("Inherited safe area was not exposed by the interop host") {
            view?.hasTopSafeAreaInsetFromSuperview() == true
        }
    }

    @Test
    fun ignorePolicyDoesNotExposeSafeAreaInsetsFromTheInteropHostToViewController() = runUIKitInstrumentedTest {
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

private class SafeAreaTrackingView : UIView(frame = CGRectZero.readValue()) {
    var lastLayoutSuperviewTopSafeAreaInset = Double.NaN
        private set

    override fun layoutSubviews() {
        super.layoutSubviews()
        lastLayoutSuperviewTopSafeAreaInset = superviewTopSafeAreaInset()
    }
}
