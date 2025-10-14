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

package androidx.privacysandbox.sdkruntime.integration.testsdk

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.privacysandbox.sdkruntime.core.activity.ActivityHolder
import androidx.privacysandbox.sdkruntime.core.activity.SdkSandboxActivityHandlerCompat
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityHandler

/** Inflate SDK Activity and provide API for controlling it to client. */
class ActivityHandlerWrapper(
    private val sdkContext: Context,
    private val appSideActivityHandler: ISdkActivityHandler,
) : SdkSandboxActivityHandlerCompat {

    private val lifecycleObserver = SdkActivityLifecycleObserver(sdkContext)
    private val onBackPressedCallback = SdkActivityOnBackPressedCallback(sdkContext)

    override fun onActivityCreated(activityHolder: ActivityHolder) {
        val sdkActivityApi = SdkActivityApi(activityHolder)
        inflateActivity(activityHolder.getActivity(), sdkActivityApi)
        appSideActivityHandler.onActivityCreated(sdkActivityApi)
    }

    private fun inflateActivity(activity: Activity, sdkActivityApi: SdkActivityApi) {
        val mainLayout = LayoutInflater.from(sdkContext).inflate(R.layout.sdk_activity_layout, null)

        // LifecycleObservers
        setupButton(mainLayout, R.id.addLifecycleObserverButton) {
            sdkActivityApi.addLifecycleObserver(lifecycleObserver)
        }
        setupButton(mainLayout, R.id.removeLifecycleObserverButton) {
            sdkActivityApi.removeLifecycleObserver(lifecycleObserver)
        }

        // OnBackPressedCallbacks
        setupButton(mainLayout, R.id.addOnBackPressedCallbackButton) {
            sdkActivityApi.addOnBackPressedCallback(onBackPressedCallback)
        }
        setupButton(mainLayout, R.id.removeOnBackPressedCallbackButton) {
            sdkActivityApi.removeOnBackPressedCallback(onBackPressedCallback)
        }

        // Finish Activity
        setupButton(mainLayout, R.id.finishActivityButton) { sdkActivityApi.finishActivity() }

        activity.setContentView(mainLayout)
    }

    private fun setupButton(mainLayout: View, @IdRes id: Int, action: () -> Unit) {
        val button = mainLayout.findViewById<Button>(id)
        button.setOnClickListener { action() }
    }

    private class SdkActivityLifecycleObserver(private val sdkContext: Context) :
        LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            Toast.makeText(sdkContext, "onStateChanged: $event", Toast.LENGTH_SHORT).show()
        }
    }

    private class SdkActivityOnBackPressedCallback(private val sdkContext: Context) :
        OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Toast.makeText(sdkContext, "handleOnBackPressed()", Toast.LENGTH_SHORT).show()
        }
    }
}
