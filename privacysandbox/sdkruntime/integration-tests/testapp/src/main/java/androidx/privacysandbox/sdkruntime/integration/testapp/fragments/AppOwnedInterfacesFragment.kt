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
import androidx.privacysandbox.sdkruntime.core.AppOwnedSdkSandboxInterfaceCompat
import androidx.privacysandbox.sdkruntime.integration.callDoSomething
import androidx.privacysandbox.sdkruntime.integration.testaidl.LoadedSdkInfo
import androidx.privacysandbox.sdkruntime.integration.testapp.AppOwnedSdk
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import kotlin.collections.forEach
import kotlinx.coroutines.launch

/** Controls for working with AppOwned SDKs. */
class AppOwnedInterfacesFragment : BaseFragment(layoutId = R.layout.fragment_app_sdks) {

    private val appOwnedSdk =
        AppOwnedSdkSandboxInterfaceCompat(
            name = "AppOwnedSdk",
            version = 42,
            binder = AppOwnedSdk(),
        )

    override fun onCreate() {
        setupRegisterAppSdkButton()
        setupUnregisterAppSdkButton()
        setupGetAppSdksFromAppButton()
        setupGetAppSdksFromSdkButton()
    }

    private fun setupRegisterAppSdkButton() {
        val registerAppSdkButton = findViewById<Button>(R.id.registerAppSdkButton)
        registerAppSdkButton.setOnClickListener {
            try {
                addLogMessage("Registering AppOwnedSdk...")
                getTestAppApi().registerAppOwnedSdk(appOwnedSdk)
                addLogMessage("Successfully registered AppOwnedSdk")
            } catch (ex: Throwable) {
                addLogMessage("Failed to register AppOwnedSdk: " + ex.message)
            }
        }
    }

    private fun setupUnregisterAppSdkButton() {
        val unregisterAppSdkButton = findViewById<Button>(R.id.unregisterAppSdkButton)
        unregisterAppSdkButton.setOnClickListener {
            getTestAppApi().unregisterAppOwnedSdk(appOwnedSdk.getName())
            addLogMessage("Unregistered AppOwnedSdk")
        }
    }

    private fun setupGetAppSdksFromAppButton() {
        val getAppSdksButton = findViewById<Button>(R.id.getAppSdksFromAppButton)
        getAppSdksButton.setOnClickListener {
            val sdks = getTestAppApi().getAppOwnedSdks()
            logAppOwnedSdks("APP: GetAppSdks", sdks)

            val messages = sdks.mapNotNull { callDoSomething(it.sdkInterface, "42") }
            logAppOwnedSdkMessages("APP: callDoSomethingOnAppOwnedSdks()", messages)
        }
    }

    private fun setupGetAppSdksFromSdkButton() {
        val getAppSdksButton = findViewById<Button>(R.id.getAppSdksFromSdkButton)
        getAppSdksButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                logAppOwnedSdks("SDK: GetAppSdks", testSdk.getAppOwnedSdks())

                logAppOwnedSdkMessages(
                    "SDK: callDoSomethingOnAppOwnedSdks()",
                    testSdk.callDoSomethingOnAppOwnedSdks("42"),
                )
            }
        }
    }

    private fun logAppOwnedSdks(title: String, sdks: List<LoadedSdkInfo>) {
        addLogMessage("$title results (${sdks.size}):")
        sdks.forEach {
            addLogMessage("   AppOwned SDK Package: ${it.sdkName}")
            addLogMessage("   AppOwned SDK Version: ${it.sdkVersion}")
        }
    }

    private fun logAppOwnedSdkMessages(title: String, messages: List<String>) {
        addLogMessage("$title results (${messages.size}):")
        messages.forEach { addLogMessage("   AppOwned SDK Message: $it") }
    }
}
