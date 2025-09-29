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

import androidx.preference.PreferenceManager
import androidx.privacysandbox.databridge.client.SyncFailureCallback
import androidx.privacysandbox.databridge.client.SyncFailureCallback.Companion.ErrorCode
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.integration.testutils.KeyUpdateCallbackImpl
import androidx.privacysandbox.databridge.integration.testutils.SharedPreferenceChangeListener
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Expect
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DataBridgeIntegrationTest {

    private val intKey = Key.createIntKey("intKey")
    private val doubleKey = Key.createDoubleKey("doubleKey")
    private val longKey = Key.createLongKey("longKey")
    private val floatKey = Key.createFloatKey("floatKey")
    private val booleanKey = Key.createBooleanKey("booleanKey")
    private val stringKey = Key.createStringKey("stringKey")
    private val stringSetKey = Key.createStringSetKey("stringSetKey")
    private val byteArrayKey = Key.createByteArrayKey("byteArrayKey")

    private val keyValueMap =
        mapOf(
            intKey to 1,
            doubleKey to 1.1,
            longKey to 1L,
            floatKey to 1.1f,
            booleanKey to true,
            stringKey to "stringValue",
            stringSetKey to setOf("stringValue1", "stringValue2"),
            byteArrayKey to byteArrayOf(1, 2, 3, 4),
        )

    private val currentThreadExecutor = Executor { command -> command.run() }

    @get:Rule val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Rule @JvmField val expect = Expect.create()

    @Before
    fun setUp() = runTest {
        getActivity().loadTestSdk()

        // Clear the keys at the start to ensure accurate test results.
        getActivity().removeValuesFromApp(keyValueMap.keys)
    }

    @After
    fun tearDown() = runTest {
        getActivity().removeValuesFromApp(keyValueMap.keys)

        getActivity().unloadTestSdk()
        activityScenarioRule.scenario.close()
    }

    @Test
    fun testGetValueFromApp_nullValue() = runTest {
        val result = getActivity().getValuesFromApp(setOf(intKey, stringKey))

        expect.that(result.size).isEqualTo(2)
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)
    }

    @Test
    fun testGetValuesFromSdk_nullValue() = runTest {
        val result = getActivity().getValuesFromSdk(setOf(intKey, stringKey))

        expect.that(result.size).isEqualTo(2)
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)
    }

    @Test
    fun testGetValuesFromApp_wrongType() = runTest {
        val tempIntKey = Key.createIntKey("tempKey")
        getActivity().setValuesFromApp(mapOf(intKey to 1, tempIntKey to 10))

        val tempStringKey = Key.createStringKey("tempKey")
        val keyValueMapResult = getActivity().getValuesFromApp(setOf(intKey, tempStringKey))

        verifySuccessfulResult(keyValueMapResult[intKey]!!, keyValueMap[intKey])
        verifyClassCastExceptionFailureResult(keyValueMapResult[tempStringKey]!!)
    }

    @Test
    fun testGetValuesFromSdk_wrongType() = runTest {
        val tempIntKey = Key.createIntKey("tempKey")
        getActivity().setValuesFromSdk(mapOf(intKey to 1, tempIntKey to 10))

        val tempStringKey = Key.createStringKey("tempKey")
        val keyValueMapResult = getActivity().getValuesFromSdk(setOf(intKey, tempStringKey))

        verifySuccessfulResult(keyValueMapResult[intKey]!!, keyValueMap[intKey])
        verifyClassCastExceptionFailureResult(keyValueMapResult[tempStringKey]!!)
    }

    @Test
    fun testSetValuesAndGetValuesFromApp() = runTest {
        getActivity().setValuesFromApp(keyValueMap)

        val result = getActivity().getValuesFromApp(keyValueMap.keys)

        keyValueMap.keys.forEach { key -> verifySuccessfulResult(result[key]!!, keyValueMap[key]) }
    }

    @Test
    fun testSetValuesAndGetValuesFromSdk() = runTest {
        getActivity().setValuesFromSdk(keyValueMap)

        val result = getActivity().getValuesFromSdk(keyValueMap.keys)

        keyValueMap.keys.forEach { key -> verifySuccessfulResult(result[key]!!, keyValueMap[key]) }
    }

    @Test
    fun testSetValuesFromAppAndGetValuesFromSdk() = runTest {
        getActivity().setValuesFromApp(keyValueMap)

        val result = getActivity().getValuesFromSdk(keyValueMap.keys)

        keyValueMap.keys.forEach { key -> verifySuccessfulResult(result[key]!!, keyValueMap[key]) }
    }

    @Test
    fun testSetValuesFromSdkAndGetValuesFromApp() = runTest {
        getActivity().setValuesFromSdk(keyValueMap)

        val res = getActivity().getValuesFromApp(keyValueMap.keys)

        keyValueMap.keys.forEach { key -> verifySuccessfulResult(res[key]!!, keyValueMap[key]) }
    }

    @Test
    fun testRemoveValuesFromApp() = runTest {
        getActivity()
            .setValuesFromApp(
                mapOf(intKey to keyValueMap[intKey], stringKey to keyValueMap[stringKey])
            )
        var result = getActivity().getValuesFromApp(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, keyValueMap[intKey])
        verifySuccessfulResult(result[stringKey]!!, keyValueMap[stringKey])

        getActivity().removeValuesFromApp(setOf(intKey, stringKey))

        result = getActivity().getValuesFromApp(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)
    }

    @Test
    fun testRemoveValuesFromSdk() = runTest {
        getActivity()
            .setValuesFromSdk(
                mapOf(intKey to keyValueMap[intKey], stringKey to keyValueMap[stringKey])
            )
        var result = getActivity().getValuesFromSdk(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, keyValueMap[intKey])
        verifySuccessfulResult(result[stringKey]!!, keyValueMap[stringKey])

        getActivity().removeValuesFromSdk(setOf(intKey, stringKey))

        result = getActivity().getValuesFromSdk(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)

        // Verify that getValues from App also returns null
        result = getActivity().getValuesFromApp(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)
    }

    @Test
    fun testRemoveValuesFromApp_fetchFromSdk_returnsNullValue() = runTest {
        getActivity()
            .setValuesFromSdk(
                mapOf(intKey to keyValueMap[intKey], stringKey to keyValueMap[stringKey])
            )
        var result = getActivity().getValuesFromSdk(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, keyValueMap[intKey])
        verifySuccessfulResult(result[stringKey]!!, keyValueMap[stringKey])

        getActivity().removeValuesFromApp(setOf(intKey, stringKey))

        result = getActivity().getValuesFromSdk(setOf(intKey, stringKey))

        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)
    }

    @Test(expected = java.lang.ClassCastException::class)
    fun testSetValuesFromApp_andGetValues_sameKeyWithDifferentTypeUpdateInSingleCall() = runTest {
        val tempStringKey = Key.createStringKey("intKey")
        // This will try to override an int key "intKey" to the string value
        // "tempString" which will result in a ClassCastException.
        getActivity().setValuesFromApp(mapOf(intKey to 1, tempStringKey to "tempString"))
    }

    @Test(expected = java.lang.ClassCastException::class)
    fun testSetValuesFromSdk_andGetValues_sameKeyWithDifferentTypeUpdateInSingleCall() = runTest {
        val tempStringKey = Key.createStringKey("intKey")
        val keyValueMap = mapOf(Key.createIntKey("intKey") to 1, tempStringKey to "tempString")

        getActivity().setValuesFromSdk(keyValueMap)
    }

    @Test
    fun testRegisterKeyUpdateCallbackFromApp() = runTest {
        val callback1 = KeyUpdateCallbackImpl()
        val callback2 = KeyUpdateCallbackImpl()

        getActivity()
            .registerKeyUpdateCallbackFromApp(
                setOf(intKey, stringKey),
                currentThreadExecutor,
                callback1,
            )

        getActivity()
            .registerKeyUpdateCallbackFromApp(
                setOf(doubleKey, stringKey),
                currentThreadExecutor,
                callback2,
            )

        callback1.initializeLatch(listOf(stringKey))
        callback2.initializeLatch(listOf(stringKey))
        getActivity().setValuesFromApp(mapOf(stringKey to "stringValue"))
        verifyCountAndValue(callback1, stringKey, 1, "stringValue")
        verifyCountAndValue(callback2, stringKey, 1, "stringValue")

        getActivity().unregisterKeyUpdateCallbackFromApp(callback1)
        getActivity().unregisterKeyUpdateCallbackFromApp(callback2)
    }

    @Test(expected = TimeoutException::class)
    fun testUnregisterKeyUpdatesFromApp() = runTest {
        val callback = KeyUpdateCallbackImpl()

        getActivity()
            .registerKeyUpdateCallbackFromApp(setOf(intKey), currentThreadExecutor, callback)

        callback.initializeLatch(listOf(intKey))
        getActivity().setValuesFromApp(mapOf(intKey to 123))
        verifyCountAndValue(callback, intKey, 1, 123)

        getActivity().unregisterKeyUpdateCallbackFromApp(callback)
        callback.initializeLatch(listOf(intKey))
        getActivity().setValuesFromApp(mapOf(intKey to 11))

        // This throws a TimeoutException exception because it CountDownLatch.awaits returns a
        // boolean as the callback has been unregistered
        val unused = callback.getCounterForKey(intKey)
    }

    @Test
    fun testRegisterKeyUpdateCallbackFromSdk() = runTest {
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()

        val callback1 = KeyUpdateCallbackImpl()
        val callback2 = KeyUpdateCallbackImpl()

        getActivity()
            .registerKeyUpdateCallbackFromSdk(
                uuid1,
                setOf(intKey, stringKey),
                currentThreadExecutor,
                callback1,
            )

        getActivity()
            .registerKeyUpdateCallbackFromSdk(
                uuid2,
                setOf(doubleKey, stringKey),
                currentThreadExecutor,
                callback2,
            )

        callback1.initializeLatch(listOf(stringKey))
        callback2.initializeLatch(listOf(stringKey))
        getActivity().setValuesFromApp(mapOf(stringKey to "stringValue"))

        verifyCountAndValue(callback1, stringKey, 1, "stringValue")
        verifyCountAndValue(callback2, stringKey, 1, "stringValue")

        getActivity().unregisterKeyUpdateCallbackFromSdk(uuid1, callback1)
        getActivity().unregisterKeyUpdateCallbackFromSdk(uuid2, callback2)
    }

    @Test(expected = TimeoutException::class)
    fun testUnregisterKeyUpdatesFromSdk() = runTest {
        val uuid = UUID.randomUUID().toString()
        val callback = KeyUpdateCallbackImpl()

        getActivity()
            .registerKeyUpdateCallbackFromSdk(uuid, setOf(intKey), currentThreadExecutor, callback)

        callback.initializeLatch(listOf(intKey))
        getActivity().setValuesFromApp(mapOf(intKey to 123))
        verifyCountAndValue(callback, intKey, 1, 123)

        getActivity().unregisterKeyUpdateCallbackFromSdk(uuid, callback)
        callback.initializeLatch(listOf(intKey))
        getActivity().setValuesFromApp(mapOf(intKey to 11))

        // This throws a TimeoutException exception because it CountDownLatch.awaits returns a
        // boolean as the callback has been unregistered
        val unused = callback.getValueForKey(intKey)
    }

    @Test
    fun testAddKeysForSynchronizationDataBridgeClient() = runTest {
        var result = getActivity().getValuesFromApp(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, null)
        verifySuccessfulResult(result[stringKey]!!, null)

        val keyUpdateCallback = KeyUpdateCallbackImpl()
        getActivity()
            .registerKeyUpdateCallbackFromApp(
                setOf(intKey, stringKey),
                currentThreadExecutor,
                keyUpdateCallback,
            )
        keyUpdateCallback.initializeLatch(listOf(intKey, stringKey))

        getActivity().addKeysForSynchronization(mapOf(intKey to 1, stringKey to "stringValue"))

        // Wait for the KeyUpdateCallback to receive updates
        verifyCountAndValue(keyUpdateCallback, intKey, 1, 1)
        verifyCountAndValue(keyUpdateCallback, stringKey, 1, "stringValue")

        // Ensures values for intKey and stringKey are updated in DataBridgeClient
        result = getActivity().getValuesFromApp(setOf(intKey, stringKey))
        verifySuccessfulResult(result[intKey]!!, 1)
        verifySuccessfulResult(result[stringKey]!!, "stringValue")

        getActivity().unregisterKeyUpdateCallbackFromApp(keyUpdateCallback)
    }

    @Test
    fun testAddKeysForSynchronizationSharedPreference() = runTest {
        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(getActivity())
        sharedPreference.edit().clear().commit()

        expect.that(sharedPreference.all.containsKey(intKey.name)).isFalse()
        expect.that(sharedPreference.all.containsKey(stringKey.name)).isFalse()

        val sharedPreferenceListener = SharedPreferenceChangeListener()
        sharedPreferenceListener.initializeLatch(setOf(intKey.name, stringKey.name))
        sharedPreference.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)

        getActivity().addKeysForSynchronization(mapOf(intKey to 1, stringKey to "stringValue"))

        // Ensures values for intKey and stringKey are updated in SharedPreference
        sharedPreferenceListener.getValue(intKey.name)
        sharedPreferenceListener.getValue(stringKey.name)

        expect.that(sharedPreference.all[intKey.name]).isEqualTo(1)
        expect.that(sharedPreference.all[stringKey.name]).isEqualTo("stringValue")

        sharedPreference.unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener)
        sharedPreference.edit().clear().commit()
    }

    @Test
    fun testGetSyncedKey() = runTest {
        getActivity().addKeysForSynchronization(mapOf(intKey to 1, stringKey to "stringValue"))
        val syncedKeys = getActivity().getSyncedKeys()
        expect.that(syncedKeys.size).isEqualTo(2)
        expect.that(syncedKeys).containsExactly(intKey, stringKey)
    }

    @Test
    fun testGetSyncedKeySameKeyAddedMultipleTimes() = runTest {
        getActivity().addKeysForSynchronization(mapOf(intKey to 1, stringKey to "stringValue"))
        var syncedKeys = getActivity().getSyncedKeys()
        expect.that(syncedKeys.size).isEqualTo(2)
        expect.that(syncedKeys).containsExactly(intKey, stringKey)

        getActivity().addKeysForSynchronization(mapOf(intKey to 1))
        syncedKeys = getActivity().getSyncedKeys()
        expect.that(syncedKeys.size).isEqualTo(2)
        expect.that(syncedKeys).containsExactly(intKey, stringKey)
    }

    @Test
    fun testGetSyncedKeySameKeyAddedMultipleTimesDifferentType() = runTest {
        getActivity().addKeysForSynchronization(mapOf(intKey to 1, stringKey to "stringValue"))
        var syncedKeys = getActivity().getSyncedKeys()
        expect.that(syncedKeys.size).isEqualTo(2)
        expect.that(syncedKeys).containsExactly(intKey, stringKey)

        val intKeyChangedToString = Key.createStringKey(intKey.name)
        getActivity().addKeysForSynchronization(mapOf(intKeyChangedToString to "intValue"))
        syncedKeys = getActivity().getSyncedKeys()
        expect.that(syncedKeys.size).isEqualTo(2)
        expect.that(syncedKeys).containsExactly(intKeyChangedToString, stringKey)
    }

    @Test
    fun testAddSyncFailureCallback() = runTest {
        val syncFailureCallback = SyncFailureCallbackImpl()
        getActivity().addSyncFailureCallback(currentThreadExecutor, syncFailureCallback)

        syncFailureCallback.initializeLatch(setOf(intKey))
        getActivity().addKeysForSynchronization(mapOf(intKey to "intValue"))

        val valErrorData = syncFailureCallback.getValueDataList(intKey)
        expect.that(valErrorData.size).isEqualTo(2)
    }

    @Test(expected = TimeoutException::class)
    fun testRemoveSyncFailureCallback() = runTest {
        val syncFailureCallback = SyncFailureCallbackImpl()
        getActivity().addSyncFailureCallback(currentThreadExecutor, syncFailureCallback)

        syncFailureCallback.initializeLatch(setOf(intKey))
        getActivity().addKeysForSynchronization(mapOf(intKey to "intValue"))

        val valErrorData = syncFailureCallback.getValueDataList(intKey)
        expect.that(valErrorData.size).isEqualTo(2)

        getActivity().removeSyncFailureCallback(syncFailureCallback)
        syncFailureCallback.initializeLatch(setOf(intKey))
        getActivity().addKeysForSynchronization(mapOf(intKey to "intValue"))
        // This throws a TimeoutException exception because it CountDownLatch.awaits returns a
        // boolean as the callback has been unregistered
        val unused = syncFailureCallback.getValueDataList(intKey)
    }

    private fun verifySuccessfulResult(result: Result<Any?>, expectedVal: Any?) {
        expect.that(result.isSuccess).isTrue()
        expect.that(result.getOrNull()).isEqualTo(expectedVal)
    }

    private fun verifyClassCastExceptionFailureResult(result: Result<Any?>) {
        expect.that(result.isFailure).isTrue()
        expect.that(result.exceptionOrNull() is ClassCastException).isTrue()
    }

    private fun verifyCountAndValue(
        callback: KeyUpdateCallbackImpl,
        key: Key,
        count: Int,
        value: Any?,
    ) {
        expect.that(callback.getCounterForKey(key)).isEqualTo(count)
        expect.that(callback.getValueForKey(key)).isEqualTo(value)
    }

    private suspend fun getActivity(): MainActivity = suspendCancellableCoroutine {
        activityScenarioRule.scenario.onActivity { activity -> it.resume(activity) }
    }

    data class ValueErrorData(
        val value: Any?,
        @ErrorCode val errorCode: Int,
        val errorMessage: String,
    )

    class SyncFailureCallbackImpl : SyncFailureCallback {
        private val latchMap = mutableMapOf<Key, CountDownLatch>()
        private val keyValueErrorData = mutableMapOf<Key, MutableList<ValueErrorData>>()

        override fun onSyncFailure(
            keyValueMap: Map<Key, Any?>,
            @ErrorCode errorCode: Int,
            errorMessage: String,
        ) {
            keyValueMap.forEach { (key, value) ->
                val keyValueErrorDataForKey = keyValueErrorData.getOrPut(key) { mutableListOf() }
                keyValueErrorDataForKey.add(ValueErrorData(value, errorCode, errorMessage))
                latchMap[key]?.countDown()
            }
        }

        fun initializeLatch(keys: Set<Key>) {
            keys.forEach { key -> latchMap[key] = CountDownLatch(2) }
        }

        fun getValueDataList(key: Key): List<ValueErrorData> {
            val res = latchMap[key]?.await(5, TimeUnit.SECONDS)
            res?.let {
                if (!it) {
                    throw TimeoutException()
                }
            }
            return keyValueErrorData[key]!!.toList()
        }
    }
}
