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
import androidx.privacysandbox.sdkruntime.integration.callDoSomething
import androidx.privacysandbox.sdkruntime.integration.testaidl.LoadedSdkInfo
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import kotlinx.coroutines.launch

/** Controls for retrieving currently loaded SDKs. */
class LoadedSdksFragment : BaseFragment(layoutId = R.layout.fragment_loaded_sdks) {

    override fun onCreate() {
        setupGetSandboxedSdksFromAppButton()
        setupGetSandboxedSdksFromSdkButton()
    }

    private fun setupGetSandboxedSdksFromAppButton() {
        val getSandboxedSdksButton = findViewById<Button>(R.id.getSandboxedSdksFromAppButton)
        getSandboxedSdksButton.setOnClickListener {
            val sdks = getTestAppApi().getSandboxedSdks()
            logLoadedSdks("APP: GetSandboxedSdks", sdks)

            val messages = sdks.mapNotNull { callDoSomething(it.sdkInterface, "42") }
            logSdkMessages("APP: callDoSomethingOnSandboxedSdks()", messages)
        }
    }

    private fun setupGetSandboxedSdksFromSdkButton() {
        val getSandboxedSdksButton = findViewById<Button>(R.id.getSandboxedSdksFromSdkButton)
        getSandboxedSdksButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                logLoadedSdks("SDK: GetSandboxedSdks", testSdk.getSandboxedSdks())
                logSdkMessages(
                    "SDK: callDoSomethingOnSandboxedSdks()",
                    testSdk.callDoSomethingOnSandboxedSdks("42"),
                )
            }
        }
    }

    private fun logLoadedSdks(title: String, sdks: List<LoadedSdkInfo>) {
        addLogMessage("$title results (${sdks.size}):")
        sdks.forEach {
            addLogMessage("   SDK Package: ${it.sdkName}")
            addLogMessage("   SDK Version: ${it.sdkVersion}")
        }
    }

    private fun logSdkMessages(title: String, messages: List<String>) {
        addLogMessage("$title results (${messages.size}):")
        messages.forEach { addLogMessage("   SDK Message: $it") }
    }
}
