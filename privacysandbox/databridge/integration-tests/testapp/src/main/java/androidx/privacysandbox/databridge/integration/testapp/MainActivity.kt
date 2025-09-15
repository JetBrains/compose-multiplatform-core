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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.privacysandbox.databridge.client.SyncCallback
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.KeyUpdateCallback
import androidx.privacysandbox.databridge.integration.testsdk.SdkKeyUpdateCallback
import androidx.privacysandbox.databridge.integration.testsdk.TestSdk
import androidx.privacysandbox.databridge.integration.testutils.fromKeyValue
import androidx.privacysandbox.databridge.integration.testutils.toKeyResultPair
import androidx.privacysandbox.databridge.integration.testutils.toKeyValuePair
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    lateinit var testAppApi: TestAppApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        testAppApi = TestAppApi(applicationContext)
    }

    internal suspend fun loadTestSdk() {
        testAppApi.loadTestSdk()
    }

    internal suspend fun unloadTestSdk() {
        testAppApi.unloadTestSdk()
    }

    internal fun getSdk(): TestSdk {
        return testAppApi.sdk!!
    }

    internal suspend fun getValuesFromApp(keys: Set<Key>): Map<Key, Result<Any?>> {
        return testAppApi.getValues(keys)
    }

    internal suspend fun getValuesFromSdk(keys: Set<Key>): Map<Key, Result<Any?>> {
        val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
        val result = testAppApi.sdk!!.getValues(keyNames, keyTypes)
        return result.associate { it.toKeyResultPair() }
    }

    internal suspend fun setValuesFromApp(keyValueMap: Map<Key, Any?>) {
        testAppApi.setValues(keyValueMap)
    }

    internal suspend fun setValuesFromSdk(keyValueMap: Map<Key, Any?>) {
        val keyValueData = keyValueMap.map { Bundle().fromKeyValue(it.key, it.value) }
        testAppApi.sdk!!.setValues(
            keyValueMap.keys.map { it.name },
            keyValueMap.keys.map { it.type.toString() },
            keyValueData,
        )
    }

    internal suspend fun removeValuesFromApp(keys: Set<Key>) {
        testAppApi.removeValues(keys)
    }

    internal suspend fun removeValuesFromSdk(keys: Set<Key>) {
        val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
        testAppApi.sdk!!.removeValues(keyNames, keyTypes)
    }

    internal fun registerKeyUpdateCallbackFromApp(
        keys: Set<Key>,
        executor: Executor,
        callback: KeyUpdateCallback,
    ) {
        testAppApi.registerKeyUpdateCallback(keys, executor, callback)
    }

    internal fun unregisterKeyUpdateCallbackFromApp(callback: KeyUpdateCallback) {
        testAppApi.unregisterKeyUpdateCallback(callback)
    }

    val keyUpdateCallbackToSdkKeyUpdateCallbackMap =
        mutableMapOf<KeyUpdateCallback, SdkKeyUpdateCallbackImpl>()

    internal fun registerKeyUpdateCallbackFromSdk(
        uuid: String,
        keys: Set<Key>,
        executor: Executor,
        callback: KeyUpdateCallback,
    ) {
        val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
        if (!keyUpdateCallbackToSdkKeyUpdateCallbackMap.containsKey(callback)) {
            keyUpdateCallbackToSdkKeyUpdateCallbackMap[callback] =
                SdkKeyUpdateCallbackImpl(executor, callback)
        }
        testAppApi.sdk!!.registerKeyUpdateCallback(
            uuid,
            keyNames,
            keyTypes,
            keyUpdateCallbackToSdkKeyUpdateCallbackMap[callback]!!,
        )
    }

    internal fun unregisterKeyUpdateCallbackFromSdk(uuid: String, callback: KeyUpdateCallback) {
        if (!keyUpdateCallbackToSdkKeyUpdateCallbackMap.containsKey(callback)) {
            return
        }
        testAppApi.sdk!!.unregisterKeyUpdateCallback(
            uuid,
            keyUpdateCallbackToSdkKeyUpdateCallbackMap[callback]!!,
        )
        keyUpdateCallbackToSdkKeyUpdateCallbackMap.remove(callback)
    }

    internal fun addKeysForSynchronization(keyValueMap: Map<Key, Any?>) {
        testAppApi.addKeysForSynchronization(keyValueMap)
    }

    internal fun getSyncedKeys(): Set<Key> {
        return testAppApi.getSyncedKeys()
    }

    internal fun addDataSyncCallback(executor: Executor, syncCallback: SyncCallback) {
        testAppApi.addDataSyncCallback(executor, syncCallback)
    }

    internal fun removeDataSyncCallback(syncCallback: SyncCallback) {
        testAppApi.removeDataSyncCallback(syncCallback)
    }

    class SdkKeyUpdateCallbackImpl(val executor: Executor, val callback: KeyUpdateCallback) :
        SdkKeyUpdateCallback {
        override fun onKeyUpdated(keyName: String, keyType: String, value: Bundle) {
            executor.execute {
                val keyValuePair = value.toKeyValuePair(keyName, keyType)
                executor.execute { callback.onKeyUpdated(keyValuePair.first, keyValuePair.second) }
            }
        }
    }
}
