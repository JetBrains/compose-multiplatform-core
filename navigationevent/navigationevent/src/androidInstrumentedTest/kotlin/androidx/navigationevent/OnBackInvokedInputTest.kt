/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.navigationevent

import androidx.navigationevent.testing.TestNavigationEventCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class OnBackInvokedInputTest {

    @Test
    fun testSimpleInvoker() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}
        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        callback.remove()

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    fun testInvokerEnableDisable() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        callback.isBackEnabled = false

        assertThat(invoker.unregisterCount).isEqualTo(1)

        callback.isBackEnabled = true

        assertThat(invoker.registerCount).isEqualTo(2)
    }

    @Test
    fun testCallbackEnabledDisabled() {
        val callback = TestNavigationEventCallback(isBackEnabled = false)
        assertThat(callback.isBackEnabled).isFalse()

        callback.isBackEnabled = true
        assertThat(callback.isBackEnabled).isTrue()

        callback.isBackEnabled = false
        assertThat(callback.isBackEnabled).isFalse()
    }

    @Test
    fun testInvokerAddDisabledCallback() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val callback = TestNavigationEventCallback(isBackEnabled = false)

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(0)

        callback.isBackEnabled = true

        assertThat(invoker.registerCount).isEqualTo(1)

        callback.isBackEnabled = false

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    fun testInvokerAddEnabledCallbackBeforeSet() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        assertThat(invoker.registerCount).isEqualTo(1)

        callback.isBackEnabled = false

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testSimpleAnimatedCallback() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        invoker.dispatchOnBackStarted(TestBackEvent())
        assertThat(callback.onBackStartedInvocations).isEqualTo(1)

        invoker.dispatchOnBackProgressed(TestBackEvent())
        assertThat(callback.onBackProgressedInvocations).isEqualTo(1)

        invoker.dispatchOnBackCancelled()
        assertThat(callback.onBackCancelledInvocations).isEqualTo(1)

        callback.remove()

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testSimpleAnimatedCallbackRemovedCancel() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        invoker.dispatchOnBackStarted(TestBackEvent())

        callback.remove()
        assertThat(callback.onBackCancelledInvocations).isEqualTo(1)

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testSimpleAnimatedCallbackRemovedCancelInHandleOnStarted() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback(onBackStarted = { remove() })

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        invoker.dispatchOnBackStarted(TestBackEvent())

        assertThat(callback.onBackCancelledInvocations).isEqualTo(1)

        assertThat(invoker.unregisterCount).isEqualTo(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testSimpleAnimatedCallbackAddedContinue() {
        val invoker = TestOnBackInvokedDispatcher()
        val dispatcher = NavigationEventDispatcher {}

        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback = TestNavigationEventCallback()

        dispatcher.addCallback(callback)

        assertThat(invoker.registerCount).isEqualTo(1)

        invoker.dispatchOnBackStarted(TestBackEvent())

        dispatcher.addCallback(TestNavigationEventCallback())

        invoker.dispatchOnBackInvoked()

        assertThat(callback.onBackCompletedInvocations).isEqualTo(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testDoubleStartCallbackCausesCancel() {
        val invoker = TestOnBackInvokedDispatcher()

        val dispatcher = NavigationEventDispatcher {}
        val input = OnBackInvokedInput(invoker)
        dispatcher.addInput(input)

        val callback1 = TestNavigationEventCallback()

        dispatcher.addCallback(callback1)

        assertThat(invoker.registerCount).isEqualTo(1)

        invoker.dispatchOnBackStarted(TestBackEvent())

        val callback2 = TestNavigationEventCallback()

        dispatcher.addCallback(callback2)

        invoker.dispatchOnBackStarted(TestBackEvent())

        assertThat(invoker.registerCount).isEqualTo(1)

        assertThat(callback1.onBackCancelledInvocations).isEqualTo(1)

        assertThat(callback2.onBackStartedInvocations).isEqualTo(1)
    }
}
