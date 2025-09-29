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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SdkContextIntegrationTest {

    @get:Rule val testSetup = IntegrationTestSetupRule()

    @Test
    fun perSdkStorageTest() = runTest {
        val testSdk = testSetup.testAppApi().getOrLoadTestSdk()
        val mediateeSdk = testSetup.testAppApi().getOrLoadMediateeSdk()

        val fileName = "PerSdkStorageTest.txt"
        val testSdkData = "TestSDK" + System.currentTimeMillis()
        val mediateeSdkData = "MediateeSDK" + System.currentTimeMillis()

        testSdk.writeToFile(fileName, testSdkData)
        mediateeSdk.writeToFile(fileName, mediateeSdkData)

        val resultFromTestSdk = testSdk.readFromFile(fileName)
        val resultFromMediateeSdk = mediateeSdk.readFromFile(fileName)

        assertThat(resultFromTestSdk).isEqualTo(testSdkData)
        assertThat(resultFromMediateeSdk).isEqualTo(mediateeSdkData)
    }
}
