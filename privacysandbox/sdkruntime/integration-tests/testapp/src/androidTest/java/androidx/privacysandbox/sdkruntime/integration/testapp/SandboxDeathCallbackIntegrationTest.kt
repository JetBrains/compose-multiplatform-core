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

import androidx.privacysandbox.sdkruntime.client.SdkSandboxProcessDeathCallbackCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SandboxDeathCallbackIntegrationTest {

    @get:Rule val testSetup = IntegrationTestSetupRule()

    @Test
    fun sandboxDeathCallbackTest() = runTest {
        assumeFalse(testSetup.isCompatRun)

        val testAppApi = testSetup.testAppApi()
        val testSdk = testAppApi.loadTestSdk()

        val callback = SandboxDeathCallback()
        testAppApi.registerSandboxDeathCallback(callback)

        testSdk.triggerSandboxDeath()
        callback.waitForSandboxDeath()

        assertThat(testAppApi.getSandboxedSdks()).hasSize(0)
    }

    private class SandboxDeathCallback : SdkSandboxProcessDeathCallbackCompat {

        private val async = CountDownLatch(1)

        override fun onSdkSandboxDied() {
            async.countDown()
        }

        fun waitForSandboxDeath() {
            if (!async.await(5, TimeUnit.SECONDS)) {
                throw TimeoutException("Timeout for onSdkSandboxDied() call")
            }
        }
    }
}
