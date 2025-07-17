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

import android.os.IBinder
import android.widget.Button
import androidx.privacysandbox.sdkruntime.core.AppOwnedSdkSandboxInterfaceCompat
import androidx.privacysandbox.sdkruntime.integration.testaidl.IAppSdk
import androidx.privacysandbox.sdkruntime.integration.testapp.AppOwnedSdk
import androidx.privacysandbox.sdkruntime.integration.testapp.R

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
        setupGetAppSdksButton()
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

    private fun setupGetAppSdksButton() {
        val getAppSdksButton = findViewById<Button>(R.id.getAppSdksButton)
        getAppSdksButton.setOnClickListener {
            val sdks = getTestAppApi().getAppOwnedSdks()
            addLogMessage("GetAppSdks results (${sdks.size}):")
            sdks.forEach {
                addLogMessage("   AppOwned SDK Package: ${it.getName()}")
                addLogMessage("   AppOwned SDK Version: ${it.getVersion()}")
                val appOwnedSdk = toAppOwnedSdk(it.getInterface())
                if (appOwnedSdk != null) {
                    addLogMessage("   AppOwned SDK Message: ${appOwnedSdk.getMessage(42)}")
                }
            }
        }
    }

    private fun toAppOwnedSdk(appInterface: IBinder?): IAppSdk? {
        return if (IAppSdk.DESCRIPTOR == appInterface?.interfaceDescriptor) {
            IAppSdk.Stub.asInterface(appInterface)
        } else {
            null
        }
    }
}
