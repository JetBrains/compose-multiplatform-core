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
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Expect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataBridgeClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var dataBridgeClient = DataBridgeClient.getInstance(context)

    private val intKey = Key.createIntKey("intKey")
    private val doubleKey = Key.createDoubleKey("doubleKey")
    private val longKey = Key.createLongKey("longKey")
    private val floatKey = Key.createFloatKey("floatKey")
    private val booleanKey = Key.createBooleanKey("booleanKey")
    private val stringKey = Key.createStringKey("stringKey")
    private val stringSetKey = Key.createStringSetKey("stringSetKey")
    private val byteArrayKey = Key.createByteArrayKey("byteArrayKey")
    private val setOfKeys =
        setOf(
            intKey,
            doubleKey,
            longKey,
            floatKey,
            booleanKey,
            stringKey,
            stringSetKey,
            byteArrayKey,
        )

    @Rule @JvmField val expect = Expect.create()

    @After
    fun tearDown() {
        runBlocking { dataBridgeClient.removeValues(setOfKeys) }
        DataBridgeClient.resetInstanceForTesting()
    }

    @Test
    fun testDatBridgeProxyRegisteredToAppOwnedSdkSandboxInterfaceCompat() {
        val sdkSandboxManagerCompat = SdkSandboxManagerCompat.from(context)
        val appOwnedSdkSandboxInterfaces = sdkSandboxManagerCompat.getAppOwnedSdkSandboxInterfaces()
        expect.that(appOwnedSdkSandboxInterfaces.size).isEqualTo(1)
        expect
            .that(appOwnedSdkSandboxInterfaces[0].getName())
            .isEqualTo("androidx.privacysandbox.databridge")
        expect.that(appOwnedSdkSandboxInterfaces[0].getVersion()).isEqualTo(1)
    }

    @Test
    fun testGetInstanceSingleton() {
        expect.that(dataBridgeClient).isNotNull()
        val dataBridgeClient2 = DataBridgeClient.getInstance(context)

        assertSame(
            "Both references should point to the same singleton instance",
            dataBridgeClient,
            dataBridgeClient2,
        )
    }

    // TODO (b/414380274): Move to parameterized tests
    @Test
    fun testInt_getValue_forUnsetValue_returnsNull() = runBlocking { expectKeyIsMissing(intKey) }

    @Test
    fun testInt_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(intKey, 1)
    }

    @Test
    fun testInt_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(intKey, 1)
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createStringKey(intKey.name), "stringValue")
    }

    @Test
    fun testInt_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createStringKey(intKey.name), "stringValue")
            expectGetValueThrowsClassCastException(intKey)
        }

    @Test
    fun testDouble_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(doubleKey)
    }

    @Test
    fun testDouble_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(doubleKey, 1.1)
    }

    @Test
    fun testDouble_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(doubleKey, 1.1)
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createStringKey(doubleKey.name), "stringValue")
    }

    @Test
    fun testDouble_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createStringKey(doubleKey.name), "stringValue")
            expectGetValueThrowsClassCastException(doubleKey)
        }

    @Test
    fun testLong_getValue_forUnsetValue_returnsNull() = runBlocking { expectKeyIsMissing(longKey) }

    @Test
    fun testLong_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(longKey, 1L)
    }

    @Test
    fun testLong_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(longKey, 1L)
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createStringKey(longKey.name), "stringValue")
    }

    @Test
    fun testLong_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createStringKey(longKey.name), "stringValue")
            expectGetValueThrowsClassCastException(longKey)
        }

    @Test
    fun testFloat_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(floatKey)
    }

    @Test
    fun testFloat_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(floatKey, 1.1f)
    }

    @Test
    fun testFloat_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(floatKey, 1.1f)
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createStringKey(floatKey.name), "stringValue")
    }

    @Test
    fun testFloat_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createStringKey(floatKey.name), "stringValue")
            expectGetValueThrowsClassCastException(floatKey)
        }

    @Test
    fun testBoolean_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(booleanKey)
    }

    @Test
    fun testBoolean_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(booleanKey, true)
    }

    @Test
    fun testBoolean_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(booleanKey, true)
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createStringKey(booleanKey.name), "stringValue")
    }

    @Test
    fun testBoolean_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createStringKey(booleanKey.name), "stringValue")
            expectGetValueThrowsClassCastException(booleanKey)
        }

    @Test
    fun testString_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(stringKey)
    }

    @Test
    fun testString_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(stringKey, "stringValue")
    }

    @Test
    fun testString_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(stringKey, "stringValue")
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createBooleanKey(stringKey.name), true)
    }

    @Test
    fun testString_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createBooleanKey(stringKey.name), true)
            expectGetValueThrowsClassCastException(stringKey)
        }

    @Test
    fun testStringSet_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(stringSetKey)
    }

    @Test
    fun testStringSet_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(stringSetKey, setOf("stringValue1", "stringValue2"))
    }

    @Test
    fun testStringSet_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(stringSetKey, setOf("stringValue1", "stringValue2"))
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createBooleanKey(stringSetKey.name), true)
    }

    @Test
    fun testStringSet_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createBooleanKey(stringSetKey.name), true)
            expectGetValueThrowsClassCastException(stringSetKey)
        }

    @Test
    fun testByteArray_getValue_forUnsetValue_returnsNull() = runBlocking {
        expectKeyIsMissing(byteArrayKey)
    }

    @Test
    fun testByteArray_setValue_andGetValue_returnsSetValue() = runBlocking {
        expectKeySetSuccessfully(byteArrayKey, byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun testByteArray_setValue_withIntValue_canBeOverwrittenByStringValue() = runBlocking {
        dataBridgeClient.setValue(byteArrayKey, byteArrayOf(1, 2, 3, 4))
        // Overwriting with different key type is also successful
        expectKeySetSuccessfully(Key.createBooleanKey(byteArrayKey.name), true)
    }

    @Test
    fun testByteArray_getValue_forWrongTypeAfterValueSet_returnsFailureWithClassCastException() =
        runBlocking {
            dataBridgeClient.setValue(Key.createBooleanKey(byteArrayKey.name), true)
            expectGetValueThrowsClassCastException(byteArrayKey)
        }

    @Test
    fun testRemoveValue() = runBlocking {
        expectKeySetSuccessfully(intKey, 1)

        dataBridgeClient.removeValue(intKey)
        expectKeyIsMissing(intKey)
    }

    @Test
    fun testRemoveValues() = runBlocking {
        expectKeySetSuccessfully(intKey, 1)
        expectKeySetSuccessfully(booleanKey, true)

        dataBridgeClient.removeValues(setOf(intKey, booleanKey))
        expectKeyIsMissing(intKey)
        expectKeyIsMissing(booleanKey)
    }

    @Test
    fun testGetValues_NullValues() = runBlocking {
        val keyValueMap = dataBridgeClient.getValues(setOfKeys)

        setOfKeys.forEach {
            expect.that(keyValueMap[it]?.isSuccess)
            expect.that(keyValueMap[it]?.getOrNull()).isNull()
        }
    }

    @Test
    fun testGetValues() = runBlocking {
        expectKeySetSuccessfully(intKey, 1)
        expectKeySetSuccessfully(booleanKey, false)
        expectKeySetSuccessfully(stringKey, "stringValue")

        val keyValueMap = dataBridgeClient.getValues(setOfKeys)
        expect.that(keyValueMap.get(intKey)?.getOrNull()).isEqualTo(1)
        expect.that(keyValueMap.get(booleanKey)?.getOrNull()).isEqualTo(false)
        expect.that(keyValueMap.get(stringKey)?.getOrNull()).isEqualTo("stringValue")
        expect.that(keyValueMap.get(floatKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(doubleKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(longKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(stringSetKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(byteArrayKey)?.getOrNull()).isNull()
    }

    @Test
    fun testSetValues_andGetValues_sameKeyUpdateInSingleCall() {
        runBlocking {
            val tempStringKey = Key.createStringKey("intKey")

            assertThrows(ClassCastException::class.java) {
                runBlocking {
                    // This will try to override an int key "intKey" to the string value
                    // "tempString" which will result in a ClassCastException.
                    dataBridgeClient.setValues(mapOf(intKey to 1, tempStringKey to "tempString"))
                }
            }
        }
    }

    @Test
    fun testSetValues() = runBlocking {
        dataBridgeClient.setValues(
            mapOf(
                intKey to 1,
                longKey to 1L,
                booleanKey to true,
                stringSetKey to setOf("string1", "string2"),
            )
        )

        val keyValueMap = dataBridgeClient.getValues(setOfKeys)
        expect.that(keyValueMap.get(intKey)?.getOrNull()).isEqualTo(1)
        expect.that(keyValueMap.get(booleanKey)?.getOrNull()).isEqualTo(true)
        expect.that(keyValueMap.get(stringKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(floatKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(doubleKey)?.getOrNull()).isNull()
        expect.that(keyValueMap.get(longKey)?.getOrNull()).isEqualTo(1L)
        expect
            .that(keyValueMap.get(stringSetKey)?.getOrNull())
            .isEqualTo(setOf("string1", "string2"))
        expect.that(keyValueMap.get(byteArrayKey)?.getOrNull()).isNull()
    }

    @Test
    fun testSetValues_wrongType() {
        assertThrows(ClassCastException::class.java) {
            runBlocking {
                dataBridgeClient.setValues(
                    mapOf(
                        intKey to "1",
                        longKey to 1L,
                        booleanKey to true,
                        stringSetKey to setOf("string1", "string2"),
                    )
                )
            }
        }

        // Verify that the action is atomic and none of the keys are set
        val keySet = setOf(intKey, longKey, booleanKey, stringSetKey)
        runBlocking { keySet.forEach { expectKeyIsMissing(it) } }
    }

    @Test
    fun testGetValues_wrongType() {
        runBlocking {
            val tempIntKey = Key.createIntKey("tempIntKey")
            dataBridgeClient.setValue(tempIntKey, 10)
            dataBridgeClient.setValues(mapOf(intKey to 1, booleanKey to true))

            val tempStringKey = Key.createStringKey("tempIntKey")
            val keyValueMap = dataBridgeClient.getValues(setOf(intKey, booleanKey, tempStringKey))

            expect.that(keyValueMap[intKey]?.isSuccess)
            expect.that(keyValueMap[intKey]?.getOrNull()).isEqualTo(1)

            expect.that(keyValueMap[booleanKey]?.isSuccess)
            expect.that(keyValueMap[booleanKey]?.getOrNull()).isEqualTo(true)

            expect.that(keyValueMap[tempStringKey]?.isFailure)
            expect.that(keyValueMap[tempStringKey]?.exceptionOrNull() is ClassCastException)
        }
    }

    private suspend fun expectKeySetSuccessfully(key: Key, value: Any) {
        dataBridgeClient.setValue(key, value)
        val res = dataBridgeClient.getValue(key)
        expect.that(res.isSuccess).isTrue()
        expect.that(dataBridgeClient.getValue(key).getOrNull()).isEqualTo(value)
    }

    private suspend fun expectGetValueThrowsClassCastException(key: Key) {
        val result = dataBridgeClient.getValue(key)

        expect.that(result.isFailure).isTrue()
        expect.that(result.exceptionOrNull() is ClassCastException)
    }

    private suspend fun expectKeyIsMissing(key: Key) {
        val result = dataBridgeClient.getValue(key)
        expect.that(result.isSuccess).isTrue()

        expect.that(result.getOrNull()).isNull()
    }
}
