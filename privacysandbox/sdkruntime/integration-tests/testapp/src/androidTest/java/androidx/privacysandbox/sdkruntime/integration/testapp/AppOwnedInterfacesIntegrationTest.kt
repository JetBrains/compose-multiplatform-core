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

import androidx.privacysandbox.sdkruntime.core.AppOwnedSdkSandboxInterfaceCompat
import androidx.privacysandbox.sdkruntime.integration.callDoSomething
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppOwnedInterfacesIntegrationTest {

    @get:Rule val testSetup = IntegrationTestSetupRule()

    private lateinit var appOwnedSdk: AppOwnedSdkSandboxInterfaceCompat

    @Before
    fun setUp() {
        appOwnedSdk =
            AppOwnedSdkSandboxInterfaceCompat(
                name = "AppOwnedSdk",
                version = 42,
                binder = AppOwnedSdk(),
            )
    }

    @Test
    fun registerAppOwnedInterfaceTest() = runTest {
        val testAppApi = testSetup.testAppApi()
        assertThat(testAppApi.getAppOwnedSdks()).hasSize(0)

        testAppApi.registerAppOwnedSdk(appOwnedSdk)
        assertThat(testAppApi.getAppOwnedSdks()).hasSize(1)
    }

    @Test
    fun unregisterAppOwnedInterfaceTest() = runTest {
        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)
        assertThat(testAppApi.getAppOwnedSdks()).hasSize(1)

        testAppApi.unregisterAppOwnedSdk(appOwnedSdk.getName())
        assertThat(testAppApi.getAppOwnedSdks()).hasSize(0)
    }

    @Test
    fun getAppOwnedInterfacesFromAppTest() = runTest {
        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)

        val resultList = testAppApi.getAppOwnedSdks()
        assertThat(resultList).hasSize(1)
        val result = resultList.first()
        assertThat(result.sdkName).isEqualTo(appOwnedSdk.getName())
    }

    @Test
    fun getAppOwnedInterfacesFromSdk_whenSupportedTest() = runTest {
        testSetup.assumeCompatRunOrAdServicesVersionAtLeast(8)

        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)

        val resultList = testAppApi.getOrLoadTestSdk().getAppOwnedSdks()
        assertThat(resultList).hasSize(1)
        val result = resultList.first()
        assertThat(result.sdkName).isEqualTo(appOwnedSdk.getName())
    }

    @Test
    fun getAppOwnedInterfacesFromSdk_whenNotSupportedTest() = runTest {
        testSetup.assumeSandboxRunAndAdServicesVersionBelow(8)

        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)

        // Returns empty list if not supported
        val resultList = testAppApi.getOrLoadTestSdk().getAppOwnedSdks()
        assertThat(resultList).isEmpty()
    }

    @Test
    fun callAppOwnedSdkMethodFromAppTest() = runTest {
        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)

        val appOwnedSdkInterface = testAppApi.getAppOwnedSdks().first().sdkInterface
        val result = callDoSomething(appOwnedSdkInterface, "42")
        assertThat(result).isEqualTo("AppOwnedSdk result is 42")
    }

    @Test
    fun callAppOwnedSdkMethodFromSdkTest() = runTest {
        testSetup.assumeCompatRunOrAdServicesVersionAtLeast(8)

        val testAppApi = testSetup.testAppApi()
        testAppApi.registerAppOwnedSdk(appOwnedSdk)

        val result = testAppApi.getOrLoadTestSdk().callDoSomethingOnAppOwnedSdks("42")
        assertThat(result).containsExactly("AppOwnedSdk result is 42")
    }
}
