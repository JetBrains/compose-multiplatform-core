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
import androidx.privacysandbox.sdkruntime.client.SdkSandboxProcessDeathCallbackCompat
import androidx.privacysandbox.sdkruntime.integration.testapp.R
import kotlinx.coroutines.launch

/** Controls for registering Sandbox death callbacks and triggering it from SDK */
class SandboxDeathFragment : BaseFragment(layoutId = R.layout.fragment_sandbox_death) {

    private val sandboxDeathCallback =
        object : SdkSandboxProcessDeathCallbackCompat {
            override fun onSdkSandboxDied() {
                addLogMessage("onSdkSandboxDied() called")
            }
        }

    override fun onCreate() {
        setupRegisterCallbackButton()
        setupUnregisterCallbackButton()
        setupTriggerSandboxDeathButton()
    }

    private fun setupRegisterCallbackButton() {
        val registerCallbackButton = findViewById<Button>(R.id.registerSandboxDeathCallbackButton)
        registerCallbackButton.setOnClickListener {
            getTestAppApi().registerSandboxDeathCallback(sandboxDeathCallback)
            addLogMessage("Registered SandboxDeathCallback")
        }
    }

    private fun setupUnregisterCallbackButton() {
        val unregisterCallbackButton =
            findViewById<Button>(R.id.unregisterSandboxDeathCallbackButton)
        unregisterCallbackButton.setOnClickListener {
            getTestAppApi().unregisterSandboxDeathCallback(sandboxDeathCallback)
            addLogMessage("Unregistered SandboxDeathCallback")
        }
    }

    private fun setupTriggerSandboxDeathButton() {
        val getAppSdksButton = findViewById<Button>(R.id.triggerSandboxDeathButton)
        getAppSdksButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                addLogMessage("Triggering Sandbox Death via SDK")
                testSdk.triggerSandboxDeath()
            }
        }
    }
}
