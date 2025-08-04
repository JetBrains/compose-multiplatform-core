/*
 * Copyright 2024 The Android Open Source Project
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

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException
import androidx.privacysandbox.sdkruntime.integration.callDoSomething
import androidx.privacysandbox.sdkruntime.integration.testaidl.ILoadSdkCallback
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.LoadedSdkInfo
import androidx.privacysandbox.sdkruntime.provider.controller.SdkSandboxControllerCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class TestSdk(private val sdkContext: Context) : ISdkApi.Stub() {

    override fun doSomething(param: String): String {
        Log.i(TAG, "TestSdk#doSomething($param)")
        return "TestSdk result is $param"
    }

    override fun loadSdk(sdkName: String, params: Bundle, callback: ILoadSdkCallback) {
        MainScope().launch {
            try {
                val sdk = SdkSandboxControllerCompat.from(sdkContext).loadSdk(sdkName, params)
                callback.onSuccess(
                    LoadedSdkInfo(
                        sdkInterface = sdk.getInterface()!!,
                        sdkName = sdk.getSdkInfo()?.name,
                        sdkVersion = sdk.getSdkInfo()?.version,
                    )
                )
            } catch (ex: LoadSdkCompatException) {
                callback.onFailure(ex.message)
            }
        }
    }

    override fun getSandboxedSdks(): List<LoadedSdkInfo> {
        val sdks = SdkSandboxControllerCompat.from(sdkContext).getSandboxedSdks()
        return sdks.map { sdk ->
            LoadedSdkInfo(
                sdkInterface = sdk.getInterface()!!,
                sdkName = sdk.getSdkInfo()?.name,
                sdkVersion = sdk.getSdkInfo()?.version,
            )
        }
    }

    override fun getAppOwnedSdks(): List<LoadedSdkInfo> {
        val sdks = SdkSandboxControllerCompat.from(sdkContext).getAppOwnedSdkSandboxInterfaces()
        return sdks.map { sdk ->
            LoadedSdkInfo(
                sdkInterface = sdk.getInterface(),
                sdkName = sdk.getName(),
                sdkVersion = sdk.getVersion(),
            )
        }
    }

    override fun callDoSomethingOnSandboxedSdks(param: String): List<String> {
        return SdkSandboxControllerCompat.from(sdkContext).getSandboxedSdks().mapNotNull {
            callDoSomething(it.getInterface(), param)
        }
    }

    override fun callDoSomethingOnAppOwnedSdks(param: String): List<String> {
        return SdkSandboxControllerCompat.from(sdkContext)
            .getAppOwnedSdkSandboxInterfaces()
            .mapNotNull { callDoSomething(it.getInterface(), param) }
    }

    companion object {
        private const val TAG = "TestSdk"
    }
}
