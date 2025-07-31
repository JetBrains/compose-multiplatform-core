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
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkApi
import androidx.privacysandbox.sdkruntime.integration.testapp.R

/** Controls for retrieving currently loaded SDKs. */
class LoadedSdksFragment : BaseFragment(layoutId = R.layout.fragment_loaded_sdks) {

    override fun onCreate() {
        setupGetSandboxedSdksButton()
    }

    private fun setupGetSandboxedSdksButton() {
        val getSandboxedSdksButton = findViewById<Button>(R.id.getSandboxedSdksButton)
        getSandboxedSdksButton.setOnClickListener {
            val sdks = getTestAppApi().getSandboxedSdks()
            addLogMessage("GetSandboxedSdks results (${sdks.size}):")
            sdks.forEach {
                addLogMessage("   SDK Package: ${it.getSdkInfo()?.name}")
                addLogMessage("   SDK Version: ${it.getSdkInfo()?.version}")
                val testSdk = toTestSdk(it.getInterface())
                if (testSdk != null) {
                    addLogMessage("   SDK Message: ${testSdk.getMessage()}")
                }
            }
        }
    }

    private fun toTestSdk(sdkInterface: IBinder?): ISdkApi? {
        return if (ISdkApi.DESCRIPTOR == sdkInterface?.interfaceDescriptor) {
            ISdkApi.Stub.asInterface(sdkInterface)
        } else {
            null
        }
    }
}
