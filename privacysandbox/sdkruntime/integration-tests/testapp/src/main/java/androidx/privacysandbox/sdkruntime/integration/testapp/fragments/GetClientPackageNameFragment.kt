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

/** Controls for getting client package name from SDK side */
class GetClientPackageNameFragment : BaseFragment(layoutId = R.layout.fragment_get_client_package) {

    override fun onCreate() {
        setupGetClientPackageNameButton()
    }

    private fun setupGetClientPackageNameButton() {
        val getClientPackageNameButton = findViewById<Button>(R.id.getClientPackageNameButton)
        getClientPackageNameButton.setOnClickListener {
            lifecycleScope.launch {
                val testSdk = getTestAppApi().getOrLoadTestSdk()
                addLogMessage("SDK: ClientPackageName: ${testSdk.getClientPackageName()}")
            }
        }
    }
}
