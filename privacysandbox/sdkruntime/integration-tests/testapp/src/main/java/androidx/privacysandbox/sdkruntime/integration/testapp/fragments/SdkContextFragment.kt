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

package androidx.privacysandbox.sdkruntime.integration.testapp.fragments

import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import kotlinx.coroutines.launch

/** Controls for testing sdk context */
class SdkContextFragment : BaseFragment(layoutId = R.layout.fragment_sdk_context) {

    override fun onCreate() {
        setupWriteToTestSdkStorageButton()
        setupReadFromTestSdkStorageButton()

        setupWriteToMediateeSdkStorageButton()
        setupReadFromMediateeSdkStorageButton()
    }

    private fun setupWriteToTestSdkStorageButton() {
        val writeToStorageButton = findViewById<Button>(R.id.writeToTestSdkStorageButton)
        writeToStorageButton.setOnClickListener {
            lifecycleScope.launch {
                val data = "TestSDK: CurrentTime is " + System.currentTimeMillis()
                addLogMessage("TestSDK: Writing $data to $PER_SDK_STORAGE_FILENAME")
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                testSdk.writeToFile(PER_SDK_STORAGE_FILENAME, data)
            }
        }
    }

    private fun setupReadFromTestSdkStorageButton() {
        val readFromStorageButton = findViewById<Button>(R.id.readFromTestSdkStorageButton)
        readFromStorageButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                addLogMessage(
                    "TestSDK: FileContent: ${testSdk.readFromFile(PER_SDK_STORAGE_FILENAME)}"
                )
            }
        }
    }

    private fun setupWriteToMediateeSdkStorageButton() {
        val writeToStorageButton = findViewById<Button>(R.id.writeToMediateeSdkStorageButton)
        writeToStorageButton.setOnClickListener {
            lifecycleScope.launch {
                val data = "MediateeSDK: CurrentTime is " + System.currentTimeMillis()
                addLogMessage("MediateeSDK: Writing $data to $PER_SDK_STORAGE_FILENAME")
                val mediateeSdk = getTestAppApi().getOrLoadMediateeSdk()
                mediateeSdk.writeToFile(PER_SDK_STORAGE_FILENAME, data)
            }
        }
    }

    private fun setupReadFromMediateeSdkStorageButton() {
        val readFromStorageButton = findViewById<Button>(R.id.readFromMediateeSdkStorageButton)
        readFromStorageButton.setOnClickListener {
            lifecycleScope.launch {
                val mediateeSdk = getTestAppApi().getOrLoadMediateeSdk()
                addLogMessage(
                    "MediateeSDK: FileContent: ${mediateeSdk.readFromFile(PER_SDK_STORAGE_FILENAME)}"
                )
            }
        }
    }

    private companion object {
        const val PER_SDK_STORAGE_FILENAME = "per-sdk-storage.txt"
    }
}
