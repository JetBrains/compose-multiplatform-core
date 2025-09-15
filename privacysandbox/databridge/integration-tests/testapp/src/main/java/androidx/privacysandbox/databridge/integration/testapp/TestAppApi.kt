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

package androidx.privacysandbox.databridge.integration.testapp

import android.content.Context
import android.os.Bundle
import androidx.privacysandbox.databridge.client.DataBridgeClient
import androidx.privacysandbox.databridge.client.DataSynchronizationManager
import androidx.privacysandbox.databridge.client.SyncCallback
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.KeyUpdateCallback
import androidx.privacysandbox.databridge.integration.testsdk.TestSdk
import androidx.privacysandbox.databridge.integration.testsdk.TestSdkFactory.wrapToTestSdk
import androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat
import java.util.concurrent.Executor

class TestAppApi(appContext: Context) {

    /** Name of the Test SDK to be loaded. */
    private val TEST_SDK_NAME = "androidx.privacysandbox.databridge.integration.sdk"

    private val sdkSandboxManager = SdkSandboxManagerCompat.from(appContext)
    private val dataBridgeClient = DataBridgeClient.getInstance(appContext)
    private val dataSynchronizationManager = DataSynchronizationManager.getInstance(appContext)
    internal var sdk: TestSdk? = null

    suspend fun loadTestSdk() {
        if (sdk != null) return
        val sandboxedSdk = sdkSandboxManager.loadSdk(TEST_SDK_NAME, Bundle.EMPTY)
        sdk = sandboxedSdk.getInterface()?.let { wrapToTestSdk(it) }
    }

    fun unloadTestSdk() = sdkSandboxManager.unloadSdk(TEST_SDK_NAME)

    suspend fun getValues(keys: Set<Key>): Map<Key, Result<Any?>> {
        return dataBridgeClient.getValues(keys)
    }

    suspend fun setValues(keyValueMap: Map<Key, Any?>) {
        dataBridgeClient.setValues(keyValueMap)
    }

    suspend fun removeValues(keys: Set<Key>) {
        return dataBridgeClient.removeValues(keys)
    }

    fun registerKeyUpdateCallback(keys: Set<Key>, executor: Executor, callback: KeyUpdateCallback) {
        dataBridgeClient.registerKeyUpdateCallback(keys, executor, callback)
    }

    fun unregisterKeyUpdateCallback(callback: KeyUpdateCallback) {
        dataBridgeClient.unregisterKeyUpdateCallback(callback)
    }

    fun addKeysForSynchronization(keyValueMap: Map<Key, Any?>) {
        dataSynchronizationManager.addKeys(keyValueMap)
    }

    fun getSyncedKeys(): Set<Key> {
        return dataSynchronizationManager.getKeys()
    }

    fun addDataSyncCallback(executor: Executor, syncCallback: SyncCallback) {
        dataSynchronizationManager.addSyncCallback(executor, syncCallback)
    }

    fun removeDataSyncCallback(syncCallback: SyncCallback) {
        dataSynchronizationManager.removeSyncCallback(syncCallback)
    }
}
