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
import androidx.privacysandbox.sdkruntime.integration.testaidl.IClientImportanceListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientImportanceListenerIntegrationTest {

    @get:Rule val testSetup = IntegrationTestSetupRule()

    @Test
    fun clientImportanceListenerTest() = runTest {
        testSetup.assumeCompatRunOrAdServicesVersionAtLeast(14)

        val testSdk = testSetup.testAppApi().getOrLoadTestSdk()
        val listener = TestClientImportanceListener()
        testSdk.registerClientImportanceListener(listener)

        // Move to background
        listener.expectBackgroundEvent()
        testSetup.activityScenario.moveToState(Lifecycle.State.CREATED)
        listener.awaitForExpectedEvent()

        // Move to foreground
        listener.expectForegroundEvent()
        testSetup.activityScenario.moveToState(Lifecycle.State.RESUMED)
        listener.awaitForExpectedEvent()

        testSdk.unregisterClientImportanceListener(listener)
    }

    private class TestClientImportanceListener() : IClientImportanceListener.Stub() {

        private var expectedEvent: ExpectedEvent? = null

        override fun onForegroundImportanceChanged(isForeground: Boolean) {
            expectedEvent?.let {
                if (it.isForeground == isForeground) {
                    it.async.countDown()
                }
            }
        }

        fun expectForegroundEvent() = expectEvent(true)

        fun expectBackgroundEvent() = expectEvent(false)

        fun awaitForExpectedEvent() {
            if (expectedEvent == null) {
                throw IllegalStateException("No expected event set")
            }
            if (!expectedEvent!!.async.await(5, TimeUnit.SECONDS)) {
                throw TimeoutException("Timeout for onForegroundImportanceChanged()")
            }
            expectedEvent = null
        }

        private fun expectEvent(isForeground: Boolean) {
            if (expectedEvent != null) {
                throw IllegalStateException("Already expecting another event")
            }
            expectedEvent = ExpectedEvent(isForeground, CountDownLatch(1))
        }

        private data class ExpectedEvent(val isForeground: Boolean, val async: CountDownLatch)
    }
}
