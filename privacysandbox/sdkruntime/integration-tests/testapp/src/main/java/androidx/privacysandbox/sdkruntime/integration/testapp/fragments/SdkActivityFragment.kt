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
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityHandler
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import kotlinx.coroutines.launch

/** Controls for testing Sdk Activities. */
class SdkActivityFragment : BaseFragment(layoutId = R.layout.fragment_sdk_activity) {

    private val sdkActivityHandler = SdkActivityHandler(this::addLogMessage)

    override fun onCreate() {
        setupStartSdkActivityButton()
    }

    private fun setupStartSdkActivityButton() {
        val startSdkActivityButton = findViewById<Button>(R.id.startSdkActivityButton)
        startSdkActivityButton.setOnClickListener {
            lifecycleScope.launch {
                val testAppApi = getTestAppApi()

                val testSdk = testAppApi.getOrLoadTestSdk()
                val token = testSdk.registerSdkActivityHandler(sdkActivityHandler)

                addLogMessage("Starting Sdk Activity")
                testAppApi.startSdkActivity(token)
            }
        }
    }

    private class SdkActivityHandler(private val logFunction: (String) -> Unit) :
        ISdkActivityHandler.Stub() {
        override fun onActivityCreated(activityApi: ISdkActivityApi) {
            logFunction("SDK: Activity started")
        }
    }
}
