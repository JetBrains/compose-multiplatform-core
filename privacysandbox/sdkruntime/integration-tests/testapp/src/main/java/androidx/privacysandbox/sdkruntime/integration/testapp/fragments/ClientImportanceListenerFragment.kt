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
import androidx.privacysandbox.sdkruntime.integration.testaidl.IClientImportanceListener
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import java.util.Date
import kotlinx.coroutines.launch

/** Controls for testing client importance listeners. */
class ClientImportanceListenerFragment :
    BaseFragment(layoutId = R.layout.fragment_client_importance_listener) {

    private val clientImportanceListener = ClientImportanceListener(this::addLogMessage)

    override fun onCreate() {
        setupRegisterClientImportanceListenerButton()
        setupUnregisterClientImportanceListenerButton()
    }

    private fun setupRegisterClientImportanceListenerButton() {
        val registerListenerButton =
            findViewById<Button>(R.id.registerClientImportanceListenerButton)
        registerListenerButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                testSdk.registerClientImportanceListener(clientImportanceListener)
                addLogMessage("TestSDK: Registered ClientImportanceListener")
            }
        }
    }

    private fun setupUnregisterClientImportanceListenerButton() {
        val unregisterListenerButton =
            findViewById<Button>(R.id.unregisterClientImportanceListenerButton)
        unregisterListenerButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                testSdk.unregisterClientImportanceListener(clientImportanceListener)
                addLogMessage("TestSDK: Unregistered ClientImportanceListener")
            }
        }
    }

    private class ClientImportanceListener(private val logFunction: (String) -> Unit) :
        IClientImportanceListener.Stub() {

        override fun onForegroundImportanceChanged(isForeground: Boolean) {
            logFunction("SDK: onForegroundImportanceChanged($isForeground) at ${Date()}")
        }
    }
}
