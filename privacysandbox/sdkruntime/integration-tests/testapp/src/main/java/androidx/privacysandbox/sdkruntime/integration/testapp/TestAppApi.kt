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

package androidx.privacysandbox.sdkruntime.integration.testapp

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat
import androidx.privacysandbox.sdkruntime.client.SdkSandboxProcessDeathCallbackCompat
import androidx.privacysandbox.sdkruntime.core.AppOwnedSdkSandboxInterfaceCompat
import androidx.privacysandbox.sdkruntime.core.SandboxedSdkCompat
import androidx.privacysandbox.sdkruntime.core.SandboxedSdkInfo
import androidx.privacysandbox.sdkruntime.integration.testaidl.IMediateeSdkApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.LoadedSdkInfo
import kotlinx.coroutines.Runnable

/**
 * Wrapper around test app functionality.
 *
 * Shared between UI in test app and functional/integration tests.
 */
class TestAppApi(appContext: Context) {

    private val sdkSandboxManager = SdkSandboxManagerCompat.from(appContext)

    private val registeredSandboxDeathCallbacks =
        mutableSetOf<SdkSandboxProcessDeathCallbackCompat>()

    suspend fun loadTestSdk(params: Bundle = Bundle()): ISdkApi {
        val loadedSdk = loadSdk(TEST_SDK_NAME, params)
        return ISdkApi.Stub.asInterface(loadedSdk.getInterface())
    }

    suspend fun loadMediateeSdk(params: Bundle = Bundle()): IMediateeSdkApi {
        val loadedSdk = loadSdk(MEDIATEE_SDK_NAME, params)
        return IMediateeSdkApi.Stub.asInterface(loadedSdk.getInterface())
    }

    suspend fun getOrLoadTestSdk(): ISdkApi {
        val sdkInterface = getOrLoadSdk(TEST_SDK_NAME)
        return ISdkApi.Stub.asInterface(sdkInterface)
    }

    suspend fun getOrLoadMediateeSdk(): IMediateeSdkApi {
        val sdkInterface = getOrLoadSdk(MEDIATEE_SDK_NAME)
        return IMediateeSdkApi.Stub.asInterface(sdkInterface)
    }

    suspend fun getOrLoadSdk(sdkName: String): IBinder {
        val loadedSdk = getSandboxedSdks().firstOrNull { it.sdkName == sdkName }?.sdkInterface
        if (loadedSdk != null) {
            return loadedSdk
        }
        return loadSdk(sdkName).getInterface()!!
    }

    suspend fun loadSdk(sdkName: String, params: Bundle = Bundle()): SandboxedSdkCompat {
        Log.i(TAG, "Loading SDK ($sdkName)")
        val loadedSdk = sdkSandboxManager.loadSdk(sdkName, params)
        Log.i(TAG, "SDK Loaded successfully ($sdkName)")
        return loadedSdk
    }

    fun unloadTestSdk() = unloadSdk(TEST_SDK_NAME)

    fun unloadMediateeSdk() = unloadSdk(MEDIATEE_SDK_NAME)

    fun unloadSdk(sdkName: String) {
        sdkSandboxManager.unloadSdk(sdkName)
    }

    fun registerAppOwnedSdk(appOwnedSdk: AppOwnedSdkSandboxInterfaceCompat) {
        sdkSandboxManager.registerAppOwnedSdkSandboxInterface(appOwnedSdk)
    }

    fun unregisterAppOwnedSdk(appOwnedSdkName: String) {
        sdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(appOwnedSdkName)
    }

    fun registerSandboxDeathCallback(callback: SdkSandboxProcessDeathCallbackCompat) {
        sdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback)
        synchronized(registeredSandboxDeathCallbacks) {
            registeredSandboxDeathCallbacks.add(callback)
        }
    }

    fun unregisterSandboxDeathCallback(callback: SdkSandboxProcessDeathCallbackCompat) {
        sdkSandboxManager.removeSdkSandboxProcessDeathCallback(callback)
        synchronized(registeredSandboxDeathCallbacks) {
            registeredSandboxDeathCallbacks.remove(callback)
        }
    }

    fun getSandboxedSdks(): List<LoadedSdkInfo> {
        return sdkSandboxManager.getSandboxedSdks().map { sdk ->
            LoadedSdkInfo(
                sdkInterface = sdk.getInterface()!!,
                sdkName = sdk.getSdkInfo()?.name,
                sdkVersion = sdk.getSdkInfo()?.version,
            )
        }
    }

    fun getAppOwnedSdks(): List<LoadedSdkInfo> {
        return sdkSandboxManager.getAppOwnedSdkSandboxInterfaces().map { sdk ->
            LoadedSdkInfo(
                sdkInterface = sdk.getInterface(),
                sdkName = sdk.getName(),
                sdkVersion = sdk.getVersion(),
            )
        }
    }

    fun resetTestState() {
        // Unregister AppOwned SDKs
        sdkSandboxManager
            .getAppOwnedSdkSandboxInterfaces()
            .map(AppOwnedSdkSandboxInterfaceCompat::getName)
            .forEach(sdkSandboxManager::unregisterAppOwnedSdkSandboxInterface)

        // Unload all SDKs
        sdkSandboxManager
            .getSandboxedSdks()
            .mapNotNull(SandboxedSdkCompat::getSdkInfo)
            .map(SandboxedSdkInfo::name)
            .forEach(sdkSandboxManager::unloadSdk)

        // Remove all SandboxDeath callbacks
        synchronized(registeredSandboxDeathCallbacks) {
            registeredSandboxDeathCallbacks.forEach(
                sdkSandboxManager::removeSdkSandboxProcessDeathCallback
            )
            registeredSandboxDeathCallbacks.clear()
        }
    }

    companion object {
        private const val TAG = "TestAppApi"

        /** Name of the Test SDK to be loaded. */
        const val TEST_SDK_NAME = "androidx.privacysandbox.sdkruntime.integrationtest.sdk"
        const val MEDIATEE_SDK_NAME =
            "androidx.privacysandbox.sdkruntime.integrationtest.mediateesdk"
    }
}
