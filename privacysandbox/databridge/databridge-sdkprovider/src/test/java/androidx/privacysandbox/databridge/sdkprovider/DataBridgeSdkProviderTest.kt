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

package androidx.privacysandbox.databridge.sdkprovider

import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.KeyUpdateCallback
import androidx.privacysandbox.databridge.core.aidl.ResultInternal
import androidx.privacysandbox.databridge.core.aidl.ValueInternal
import androidx.privacysandbox.databridge.sdkprovider.util.FakeDataBridgeProxy
import com.google.common.truth.Expect
import java.lang.ClassCastException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataBridgeSdkProviderTest {

    private val intKey = Key.createIntKey("intKey")
    private val stringKey = Key.createStringKey("stringKey")
    private val currentThreadExecutor = Executor { command -> command.run() }

    private val dataBridgeProxy =
        FakeDataBridgeProxy(
            resultInternals =
                listOf(
                    ResultInternal(
                        keyName = intKey.name,
                        valueInternal =
                            ValueInternal(
                                value = 1,
                                type = intKey.type.toString(),
                                isValueNull = false,
                            ),
                        exceptionName = null,
                        exceptionMessage = null,
                    )
                )
        )
    private val dataBridgeSdkProviderWithSuccessResult =
        DataBridgeSdkProvider.getInstance(dataBridgeProxy)

    private val dataBridgeSdkProviderWithFailureResult =
        DataBridgeSdkProvider.getInstance(
            FakeDataBridgeProxy(
                shouldThrowException = true,
                resultInternals =
                    listOf(
                        ResultInternal(
                            keyName = intKey.name,
                            valueInternal = null,
                            exceptionName = ClassCastException::class.java.canonicalName,
                            exceptionMessage = "Cannot convert String to Int",
                        )
                    ),
                exceptionName = ClassCastException::class.java.canonicalName,
                exceptionMessage = "Cannot convert String to Int",
            )
        )

    @Rule @JvmField val expect = Expect.create()

    @Test
    fun testGetValues_success() = runBlocking {
        val result = dataBridgeSdkProviderWithSuccessResult.getValues(setOf(intKey))

        expect.that(result.size).isEqualTo(1)
        expect.that(result[intKey]?.isSuccess).isTrue()
        expect.that(result[intKey]?.getOrNull()).isEqualTo(1)
    }

    @Test
    fun testGetValues_failure() = runBlocking {
        val result = dataBridgeSdkProviderWithFailureResult.getValues(setOf(intKey))

        expect.that(result.size).isEqualTo(1)

        expect.that(result[intKey]?.isFailure).isTrue()
        val throwable = result[intKey]?.exceptionOrNull()

        expect.that(throwable is ClassCastException).isTrue()
        expect.that(throwable?.message).contains("Cannot convert String to Int")
    }

    @Test
    fun testSetValues_success() = runBlocking {
        dataBridgeSdkProviderWithSuccessResult.setValues(mapOf(intKey to 1))
    }

    @Test
    fun testSetValues_failure() = runBlocking {
        val thrown =
            assertThrows(ClassCastException::class.java) {
                runBlocking { dataBridgeSdkProviderWithFailureResult.setValues(mapOf(intKey to 1)) }
            }
        expect.that(thrown.message).contains("Cannot convert String to Int")
    }

    @Test
    fun testRemoveValues_success() = runBlocking {
        dataBridgeSdkProviderWithSuccessResult.removeValues(setOf(intKey))
    }

    @Test
    fun testRemoveValues_failure() = runBlocking {
        val thrown =
            assertThrows(ClassCastException::class.java) {
                runBlocking { dataBridgeSdkProviderWithFailureResult.removeValues(setOf(intKey)) }
            }
        expect.that(thrown.message).contains("Cannot convert String to Int")
    }

    @Test
    fun testRegisterKeyUpdateCallback() {
        val callback = KeyUpdateCallbackImpl()
        dataBridgeSdkProviderWithSuccessResult.registerKeyUpdateCallback(
            setOf(intKey, stringKey),
            currentThreadExecutor,
            callback,
        )
        expect.that(callback.getValue(intKey)).isNull()
        val keysForUpdate = dataBridgeProxy.getKeysRegisteredForUpdate()

        expect.that(keysForUpdate.size).isEqualTo(2)
    }

    @Test
    fun testUnregisterKeyUpdates() {
        val callback = KeyUpdateCallbackImpl()
        dataBridgeSdkProviderWithSuccessResult.registerKeyUpdateCallback(
            setOf(intKey, stringKey),
            currentThreadExecutor,
            callback,
        )

        var keysForUpdate = dataBridgeProxy.getKeysRegisteredForUpdate()
        expect.that(keysForUpdate.size).isEqualTo(2)

        dataBridgeSdkProviderWithSuccessResult.unregisterKeyUpdateCallback(callback)
        keysForUpdate = dataBridgeProxy.getKeysRegisteredForUpdate()
        expect.that(keysForUpdate.size).isEqualTo(0)
    }

    class KeyUpdateCallbackImpl : KeyUpdateCallback {
        private val latch = CountDownLatch(1)
        private val keyValuePair = mutableMapOf<Key, Any?>()

        override fun onKeyUpdated(key: Key, value: Any?) {
            keyValuePair[key] = value
            latch.countDown()
        }

        fun getValue(key: Key): Any? {
            val res = latch.await(5, TimeUnit.SECONDS)
            if (!res) {
                throw TimeoutException()
            }
            if (!keyValuePair.containsKey(key)) {
                throw IllegalArgumentException()
            }
            return keyValuePair[key]
        }
    }
}
