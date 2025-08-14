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

import android.os.IBinder
import android.os.IInterface

/** Wrap App side callbacks (Binder based) to SDK side callbacks. */
internal class ClientCallbacksRegistry<ClientClass : IInterface, SdkClass : Any>(
    private val wrapperFun: (ClientClass) -> SdkClass,
    private val addBackend: (SdkClass) -> Unit,
    private val removeBackend: (SdkClass) -> Unit,
) {

    private val appToSdkCallbackMap = mutableMapOf<IBinder, SdkClass>()

    fun add(clientCallback: ClientClass) {
        synchronized(appToSdkCallbackMap) {
            val binderToken = clientCallback.asBinder()
            // Replace to putIfAbsent after moving minSdk to 24+
            if (!appToSdkCallbackMap.containsKey(binderToken)) {
                val wrapper = wrapperFun(clientCallback)
                appToSdkCallbackMap.put(binderToken, wrapper)
                addBackend(wrapper)
            }
        }
    }

    fun remove(clientCallback: ClientClass) {
        synchronized(appToSdkCallbackMap) {
            val binderToken = clientCallback.asBinder()
            val wrapper = appToSdkCallbackMap.remove(binderToken)
            if (wrapper != null) {
                removeBackend(wrapper)
            }
        }
    }
}
