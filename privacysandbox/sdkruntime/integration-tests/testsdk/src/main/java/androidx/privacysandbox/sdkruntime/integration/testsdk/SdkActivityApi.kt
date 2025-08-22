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

import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.privacysandbox.sdkruntime.core.activity.ActivityHolder
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityLifecycleObserver
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityOnBackPressedCallback

/** Wrapper around [ActivityHolder] for passing to client app via IPC. */
internal class SdkActivityApi(private val activityHolder: ActivityHolder) : ISdkActivityApi.Stub() {

    private val appSideLifecycleObservers =
        ClientCallbacksRegistry<ISdkActivityLifecycleObserver, LifecycleObserver>(
            wrapperFun = { ClientLifecycleObserverWrapper(it) },
            addBackend = { addLifecycleObserver(it) },
            removeBackend = { removeLifecycleObserver(it) },
        )

    private val appSideOnBackPressedCallbacks =
        ClientCallbacksRegistry<ISdkActivityOnBackPressedCallback, OnBackPressedCallback>(
            wrapperFun = { ClientOnBackPressedCallbackWrapper(it) },
            addBackend = { addOnBackPressedCallback(it) },
            removeBackend = { removeOnBackPressedCallback(it) },
        )

    override fun addLifecycleObserver(observer: ISdkActivityLifecycleObserver) {
        appSideLifecycleObservers.add(observer)
    }

    override fun removeLifecycleObserver(observer: ISdkActivityLifecycleObserver) {
        appSideLifecycleObservers.remove(observer)
    }

    override fun addOnBackPressedCallback(callback: ISdkActivityOnBackPressedCallback) {
        appSideOnBackPressedCallbacks.add(callback)
    }

    override fun removeOnBackPressedCallback(callback: ISdkActivityOnBackPressedCallback) {
        appSideOnBackPressedCallbacks.remove(callback)
    }

    override fun finishActivity() {
        MainThreadExecutor.execute { activityHolder.getActivity().finish() }
    }

    fun addLifecycleObserver(observer: LifecycleObserver) {
        MainThreadExecutor.execute { activityHolder.lifecycle.addObserver(observer) }
    }

    fun removeLifecycleObserver(observer: LifecycleObserver) {
        MainThreadExecutor.execute { activityHolder.lifecycle.removeObserver(observer) }
    }

    fun addOnBackPressedCallback(callback: OnBackPressedCallback) {
        MainThreadExecutor.execute {
            activityHolder.getOnBackPressedDispatcher().addCallback(callback)
        }
    }

    fun removeOnBackPressedCallback(callback: OnBackPressedCallback) {
        MainThreadExecutor.execute { callback.remove() }
    }

    private class ClientLifecycleObserverWrapper(
        private val clientObserver: ISdkActivityLifecycleObserver
    ) : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            clientObserver.onStateChanged(event.toString())
        }
    }

    private class ClientOnBackPressedCallbackWrapper(
        private val clientCallback: ISdkActivityOnBackPressedCallback
    ) : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            clientCallback.handleOnBackPressed()
        }
    }
}
