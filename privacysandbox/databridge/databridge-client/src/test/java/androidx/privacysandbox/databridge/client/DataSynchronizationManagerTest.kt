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

package androidx.privacysandbox.databridge.client

import android.content.Context
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.privacysandbox.databridge.client.util.KeyValueUtil
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.integration.testutils.KeyUpdateCallbackImpl
import androidx.privacysandbox.databridge.integration.testutils.SharedPreferenceChangeListener
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Expect
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataSynchronizationManagerTest {

    private val intKey = Key.createIntKey("intKey")
    private val stringKey = Key.createStringKey("stringKey")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = CoroutineScope(Dispatchers.Default)
    private val dataSynchronizationManager =
        DataSynchronizationManager.getInstance(context, dispatcher)

    private val dataBridgeClient = DataBridgeClient.getInstance(context)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val currentThreadExecutor = Executor { command -> command.run() }

    @Rule @JvmField val expect = Expect.create()

    @After
    fun tearDown() = runTest {
        dataBridgeClient.removeValues(setOf(intKey, stringKey))
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun testAddKeys() {
        val keyValueMap = mapOf(intKey to 1, stringKey to "stringValue")
        dataSynchronizationManager.addKeys(keyValueMap)

        val keys = dataSynchronizationManager.getKeys()

        expect.that(keys.size).isEqualTo(2)
        expect.that(keys).contains(intKey)
        expect.that(keys).contains(stringKey)
    }

    @Test
    fun testAddKeysSameKeyDifferentType() {
        val keyValueMap = mapOf(intKey to 1, Key.createStringKey(intKey.name) to "stringValue")

        expect.that(keyValueMap.size).isEqualTo(1)

        dataSynchronizationManager.addKeys(keyValueMap)

        val keys = dataSynchronizationManager.getKeys()
        expect.that(keys.size).isEqualTo(1)
        expect.that(keys).contains(Key.createStringKey(intKey.name))
    }

    @Test
    fun testAddKeysWithInvalidType() {
        val keyValueMap = mapOf(intKey to 1, Key.createDoubleKey("doubleKey") to 1.1)

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                dataSynchronizationManager.addKeys(keyValueMap)
            }
        expect
            .that(thrown.message)
            .isEqualTo("Invalid type. Double and ByteArray not supported for synchronization")
    }

    @Test
    fun testAddKeyUpdatesDataBridgeClient() = runTest {
        var dataBridgeClientData = dataBridgeClient.getValues(setOf(intKey, stringKey))

        KeyValueUtil.assertKeyIsMissing(dataBridgeClientData[intKey]!!)
        KeyValueUtil.assertKeyIsMissing(dataBridgeClientData[stringKey]!!)

        val keyValueMap = mapOf(intKey to 1, stringKey to "stringValue")

        val keyUpdateCallback = KeyUpdateCallbackImpl()
        keyUpdateCallback.initializeLatch(listOf(intKey, stringKey))
        dataBridgeClient.registerKeyUpdateCallback(
            setOf(intKey, stringKey),
            currentThreadExecutor,
            keyUpdateCallback,
        )

        dataSynchronizationManager.addKeys(keyValueMap)

        // Ensures values for intKey and stringKey are updated in DataBridgeClient
        val intValue = keyUpdateCallback.getValueForKey(intKey)
        val stringValue = keyUpdateCallback.getValueForKey(stringKey)

        dataBridgeClientData = dataBridgeClient.getValues(setOf(intKey, stringKey))
        KeyValueUtil.assertKeySetSuccessfully(1, dataBridgeClientData[intKey]!!)
        KeyValueUtil.assertKeySetSuccessfully("stringValue", dataBridgeClientData[stringKey]!!)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAddKeyUpdatesSharedPreferences() {
        val testCoroutineDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testCoroutineDispatcher)

        testScope.runTest {
            val sharedPreferenceChangeListener = SharedPreferenceChangeListener()
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                sharedPreferenceChangeListener
            )

            val localDataSynchronizationManager =
                DataSynchronizationManager.getInstance(context, this)

            expect.that(sharedPreferences.all.containsKey(intKey.name)).isFalse()
            expect.that(sharedPreferences.all.containsKey(stringKey.name)).isFalse()

            val keyValueMap = mapOf(intKey to 1, stringKey to "stringValue")
            localDataSynchronizationManager.addKeys(keyValueMap)

            advanceUntilIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            expect.that(sharedPreferences.all.containsKey(intKey.name)).isTrue()
            expect.that(sharedPreferences.getInt(intKey.name, 0)).isEqualTo(1)

            expect.that(sharedPreferences.all.containsKey(stringKey.name)).isTrue()
            expect.that(sharedPreferences.getString(stringKey.name, null)).isEqualTo("stringValue")

            sharedPreferences.unregisterOnSharedPreferenceChangeListener(
                sharedPreferenceChangeListener
            )
        }
    }

    @Test
    fun testAddKeyUpdatesSharedPreferenceException() {
        val syncFailureCallback = SyncFailureCallbackImpl()
        registerAndVerifySyncFailureCallback(syncFailureCallback)
    }

    @Test
    fun testUnregisterOfSyncFailureCallback() {
        val syncFailureCallback = SyncFailureCallbackImpl()
        registerAndVerifySyncFailureCallback(syncFailureCallback)

        dataSynchronizationManager.removeSyncFailureCallback(syncFailureCallback)
        syncFailureCallback.initializeLatch(intKey, 2)

        val keyValueMap = mapOf(intKey to "stringValue")
        dataSynchronizationManager.addKeys(keyValueMap)

        val thrown =
            assertThrows(TimeoutException::class.java) { syncFailureCallback.getResult(intKey) }
    }

    private fun registerAndVerifySyncFailureCallback(syncFailureCallback: SyncFailureCallbackImpl) =
        runTest {
            syncFailureCallback.initializeLatch(intKey, 2)
            dataSynchronizationManager.addSyncFailureCallback(
                currentThreadExecutor,
                syncFailureCallback,
            )

            val keyValueMap = mapOf(intKey to "stringValue")
            dataSynchronizationManager.addKeys(keyValueMap)

            val data = syncFailureCallback.getResult(intKey)

            expect.that(data.size).isEqualTo(2)

            expect.that(data[0].first).isEqualTo(SyncFailureCallback.ERROR_ADDING_KEYS)
            expect
                .that(data[0].second)
                .contains("class java.lang.String cannot be cast to class java.lang.Integer")

            expect.that(data[1].first).isEqualTo(SyncFailureCallback.ERROR_ADDING_KEYS)
            expect
                .that(data[1].second)
                .contains("class java.lang.String cannot be cast to class java.lang.Integer")
        }

    // Implementation of [SyncFailureCallback] for testing. It listens to failures when adding keys
    // for synchronization and when syncing keys between SharedPreference and DataBridgeClient.
    private class SyncFailureCallbackImpl : SyncFailureCallback {
        private val latchMap = mutableMapOf<Key, CountDownLatch>()
        private val keySyncErrorMap = mutableMapOf<Key, MutableList<Pair<Int, String>>>()

        override fun onSyncFailure(
            keyValueMap: Map<Key, Any?>,
            errorCode: Int,
            errorMessage: String,
        ) {
            keyValueMap.forEach { (key, value) ->
                if (!keySyncErrorMap.contains(key)) {
                    keySyncErrorMap[key] = mutableListOf()
                }

                keySyncErrorMap[key]?.add(errorCode to errorMessage)
                latchMap[key]?.countDown()
            }
        }

        fun getResult(key: Key): List<Pair<Int, String>> {
            val res = latchMap[key]?.await(5, TimeUnit.SECONDS)
            res?.let {
                if (!it) {
                    throw TimeoutException()
                }
            }
            return keySyncErrorMap[key]!!
        }

        fun initializeLatch(key: Key, count: Int) {
            latchMap[key] = CountDownLatch(count)
        }
    }
}
