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

package androidx.privacysandbox.sdkruntime.integration.testapp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityHandler
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityLifecycleObserver
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityOnBackPressedCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SdkActivityIntegrationTest {

    @get:Rule val testSetup = IntegrationTestSetupRule()

    @Test
    fun startSdkActivityTest() = runTest {
        val uiDevice = testSetup.uiDevice

        val startedActivity = startSdkActivity()
        val activityIsVisible = uiDevice.wait(Until.hasObject(SDK_ACTIVITY_LAYOUT), TIMEOUT_MS)
        assertThat(activityIsVisible).isTrue()

        startedActivity.finishActivity()
        val activityIsGone = uiDevice.wait(Until.gone(SDK_ACTIVITY_LAYOUT), TIMEOUT_MS)
        assertThat(activityIsGone).isTrue()

        // App Activity should be on top again
        assertThat(testSetup.activityScenario.state).isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun sdkActivityLifeCycleObserverTest() = runTest {
        val startedActivity = startSdkActivity()

        // Observer will be brought to the current state of the Activity when added.
        val startEventsCollector = EventCollector<Event>(3)
        val lifecycleObserver = TestActivityLifecycleObserver(startEventsCollector::onEvent)
        startedActivity.addLifecycleObserver(lifecycleObserver)
        val startEvents = startEventsCollector.await()
        assertThat(startEvents)
            .containsExactly(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)
            .inOrder()

        // Observer will follow all stages of finishing activity
        val finishEventsCollector = EventCollector<Event>(3)
        lifecycleObserver.impl = finishEventsCollector::onEvent
        startedActivity.finishActivity()
        val finishEvents = finishEventsCollector.await()
        assertThat(finishEvents)
            .containsExactly(Event.ON_PAUSE, Event.ON_STOP, Event.ON_DESTROY)
            .inOrder()
    }

    @Test
    fun sdkActivityOnBackPressedCallbackTest() = runTest {
        val uiDevice = testSetup.uiDevice
        val startedActivity = startSdkActivity()
        val async = CountDownLatch(1)
        val onBackPressedCallback = TestActivityOnBackPressedCallback(async::countDown)

        startedActivity.addOnBackPressedCallback(onBackPressedCallback)
        uiDevice.pressBack()
        async.await()
        // Activity still visible because back key intercepted
        assertThat(uiDevice.hasObject(SDK_ACTIVITY_LAYOUT)).isTrue()

        startedActivity.removeOnBackPressedCallback(onBackPressedCallback)
        uiDevice.pressBack()
        // Activity is gone because back key finished it
        val activityIsGone = uiDevice.wait(Until.gone(SDK_ACTIVITY_LAYOUT), TIMEOUT_MS)
        assertThat(activityIsGone).isTrue()

        // App Activity should be on top again
        assertThat(testSetup.activityScenario.state).isEqualTo(Lifecycle.State.RESUMED)
    }

    private suspend fun startSdkActivity(): ISdkActivityApi {
        val testAppApi = testSetup.testAppApi()
        val testSdk = testAppApi.getOrLoadTestSdk()

        val handler = TestActivityHandler()
        val token = testSdk.registerSdkActivityHandler(handler)
        testAppApi.startSdkActivity(token)
        return handler.waitForSdkActivity()
    }

    private class TestActivityHandler() : ISdkActivityHandler.Stub() {
        val apiCollector = EventCollector<ISdkActivityApi>(1)

        override fun onActivityCreated(activityApi: ISdkActivityApi) {
            apiCollector.onEvent(activityApi)
        }

        fun waitForSdkActivity(): ISdkActivityApi = apiCollector.await().first()
    }

    private class TestActivityLifecycleObserver(var impl: (Event) -> Unit) :
        ISdkActivityLifecycleObserver.Stub() {
        override fun onStateChanged(event: String) = impl(Event.valueOf(event))
    }

    private class TestActivityOnBackPressedCallback(var impl: () -> Unit) :
        ISdkActivityOnBackPressedCallback.Stub() {
        override fun handleOnBackPressed() = impl()
    }

    companion object {
        const val TIMEOUT_MS = 5000.toLong()
        val SDK_ACTIVITY_LAYOUT: BySelector = By.res(TestAppApi.TEST_SDK_NAME, "sdkActivityLayout")
    }
}
