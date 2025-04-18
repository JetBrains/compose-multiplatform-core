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

package androidx.appfunctions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_BOOLEAN
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_BYTES
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_DOUBLE
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_FLOAT
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_INT
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_LONG
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_PENDING_INTENT
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_STRING
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class AppFunctionDataTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testGenericReadWrite_asParameters_conformSpec() {
        val builder =
            AppFunctionData.Builder(
                TEST_PARAMETER_METADATA,
                AppFunctionComponentsMetadata(),
            )

        builder.setGenericField("int", 234, Int::class.java)
        builder.setGenericField("long", 123L, Long::class.java)
        builder.setGenericField("float", 100.0f, Float::class.java)
        builder.setGenericField("double", 50.0, Double::class.java)
        builder.setGenericField("boolean", true, Boolean::class.java)
        builder.setGenericField("string", "testString", String::class.java)
        builder.setGenericField(
            "pendingIntent",
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            PendingIntent::class.java,
        )
        builder.setGenericField("intArray", intArrayOf(4, 5, 6), IntArray::class.java)
        builder.setGenericField("longArray", longArrayOf(1L, 2L, 3L), LongArray::class.java)
        builder.setGenericField(
            "floatArray",
            floatArrayOf(10.0f, 20.0f, 30.0f),
            FloatArray::class.java
        )
        builder.setGenericField(
            "doubleArray",
            doubleArrayOf(4.0, 5.0, 6.0),
            DoubleArray::class.java
        )
        builder.setGenericField(
            "booleanArray",
            booleanArrayOf(false, true, false),
            BooleanArray::class.java
        )
        builder.setGenericField(
            "byteArray",
            byteArrayOf(10.toByte(), 20.toByte()),
            ByteArray::class.java
        )
        builder.setGenericListField("stringList", listOf("1", "2", "3"), String::class.java)
        builder.setGenericListField(
            "pendingIntentList",
            listOf(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            ),
            PendingIntent::class.java
        )
        val data = builder.build()

        assertThat(data.getGenericField("int", Int::class.java)).isEqualTo(234)
        assertThat(data.getGenericField("long", Long::class.java)).isEqualTo(123L)
        assertThat(data.getGenericField("float", Float::class.java)).isEqualTo(100.0f)
        assertThat(data.getGenericField("double", Double::class.java)).isEqualTo(50.0)
        assertThat(data.getGenericField("boolean", Boolean::class.java)).isTrue()
        assertThat(data.getGenericField("string", String::class.java)).isEqualTo("testString")
        assertThat(data.getGenericField("pendingIntent", PendingIntent::class.java))
            .isEqualTo(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            )
        assertThat(data.getGenericField("intArray", IntArray::class.java))
            .asList()
            .containsExactly(4, 5, 6)
        assertThat(data.getGenericField("longArray", LongArray::class.java))
            .asList()
            .containsExactly(1L, 2L, 3L)
        assertThat(data.getGenericField("floatArray", FloatArray::class.java))
            .usingExactEquality()
            .containsExactly(10.0f, 20.0f, 30.0f)
        assertThat(data.getGenericField("doubleArray", DoubleArray::class.java))
            .usingExactEquality()
            .containsExactly(4.0, 5.0, 6.0)
        assertThat(data.getGenericField("booleanArray", BooleanArray::class.java))
            .asList()
            .containsExactly(false, true, false)
        assertThat(data.getGenericField("byteArray", ByteArray::class.java))
            .asList()
            .containsExactly(10.toByte(), 20.toByte())
        assertThat(data.getGenericListField<String, List<String>>("stringList", String::class.java))
            .containsExactly("1", "2", "3")
        assertThat(
                data.getGenericListField<PendingIntent, List<PendingIntent>>(
                    "pendingIntentList",
                    PendingIntent::class.java
                )
            )
            .containsExactly(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            )
    }

    @Test
    fun testReadWriteGeneric_nestedObjectParameter() {
        val data =
            AppFunctionData.Builder(TEST_NESTED_PARAMETER_METADATA, AppFunctionComponentsMetadata())
                .setGenericField(
                    "data",
                    AppFunctionData.Builder(TEST_OBJECT_METADATA, AppFunctionComponentsMetadata())
                        .setGenericField("long", 100, Long::class.java)
                        .build(),
                    AppFunctionData::class.java,
                )
                .setGenericListField(
                    "dataList",
                    listOf(
                        AppFunctionData.Builder(
                                TEST_OBJECT_METADATA,
                                AppFunctionComponentsMetadata()
                            )
                            .setGenericField("double", 20.0, Double::class.java)
                            .build(),
                        AppFunctionData.Builder(
                                TEST_OBJECT_METADATA,
                                AppFunctionComponentsMetadata()
                            )
                            .setGenericField("string", "testString", String::class.java)
                            .build()
                    ),
                    AppFunctionData::class.java,
                )
                .build()

        assertThat(
                data
                    .getGenericField("data", AppFunctionData::class.java)
                    .getGenericField("long", Long::class.java)
            )
            .isEqualTo(100)
        assertThat(
                data.getGenericListField<AppFunctionData, List<AppFunctionData>>(
                    "dataList",
                    AppFunctionData::class.java
                )
            )
            .hasSize(2)
        assertThat(
                data
                    .getGenericListField<AppFunctionData, List<AppFunctionData>>(
                        "dataList",
                        AppFunctionData::class.java
                    )[0]
                    .getGenericField("double", Double::class.java)
            )
            .isEqualTo(20.0)
        assertThat(
                data
                    .getGenericListField<AppFunctionData, List<AppFunctionData>>(
                        "dataList",
                        AppFunctionData::class.java
                    )[1]
                    .getGenericField("string", String::class.java)
            )
            .isEqualTo("testString")
    }

    @Test
    fun testReadWrite_asParameters_conformSpec() {
        val builder =
            AppFunctionData.Builder(
                TEST_PARAMETER_METADATA,
                AppFunctionComponentsMetadata(),
            )

        builder.setInt("int", 234)
        builder.setLong("long", 123L)
        builder.setFloat("float", 100.0f)
        builder.setDouble("double", 50.0)
        builder.setBoolean("boolean", true)
        builder.setString("string", "testString")
        builder.setPendingIntent(
            "pendingIntent",
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        )
        builder.setIntArray("intArray", intArrayOf(4, 5, 6))
        builder.setLongArray("longArray", longArrayOf(1L, 2L, 3L))
        builder.setFloatArray("floatArray", floatArrayOf(10.0f, 20.0f, 30.0f))
        builder.setDoubleArray("doubleArray", doubleArrayOf(4.0, 5.0, 6.0))
        builder.setBooleanArray("booleanArray", booleanArrayOf(false, true, false))
        builder.setByteArray("byteArray", byteArrayOf(10.toByte(), 20.toByte()))
        builder.setStringList("stringList", listOf("1", "2", "3"))
        builder.setPendingIntentList(
            "pendingIntentList",
            listOf(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            )
        )
        val data = builder.build()

        assertThat(data.getInt("int")).isEqualTo(234)
        assertThat(data.getLong("long")).isEqualTo(123L)
        assertThat(data.getFloat("float")).isEqualTo(100.0f)
        assertThat(data.getDouble("double")).isEqualTo(50.0)
        assertThat(data.getBoolean("boolean")).isTrue()
        assertThat(data.getString("string")).isEqualTo("testString")
        assertThat(data.getPendingIntent("pendingIntent"))
            .isEqualTo(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            )
        assertThat(data.getIntArray("intArray")).asList().containsExactly(4, 5, 6)
        assertThat(data.getLongArray("longArray")).asList().containsExactly(1L, 2L, 3L)
        assertThat(data.getFloatArray("floatArray"))
            .usingExactEquality()
            .containsExactly(10.0f, 20.0f, 30.0f)
        assertThat(data.getDoubleArray("doubleArray"))
            .usingExactEquality()
            .containsExactly(4.0, 5.0, 6.0)
        assertThat(data.getBooleanArray("booleanArray"))
            .asList()
            .containsExactly(false, true, false)
        assertThat(data.getByteArray("byteArray"))
            .asList()
            .containsExactly(10.toByte(), 20.toByte())
        assertThat(data.getStringList("stringList")).containsExactly("1", "2", "3")
        assertThat(data.getPendingIntentList("pendingIntentList"))
            .containsExactly(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            )
    }

    @Test
    fun testWrite_asParameters_notConformSpec() {
        val builder =
            AppFunctionData.Builder(
                TEST_PARAMETER_METADATA,
                AppFunctionComponentsMetadata(),
            )

        assertThrows(IllegalArgumentException::class.java) {
            builder.setIntArray("int", intArrayOf(100, 200))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setLong("int", 50) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("long", longArrayOf(100, 200))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("long", 50.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setFloatArray("float", floatArrayOf(50.0f, 100.0f))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("float", 20.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("double", doubleArrayOf(50.0, 100.0))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setBoolean("double", true) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("boolean", booleanArrayOf(false, true))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setLong("boolean", 100) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("string", listOf("test"))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("string", 100.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setPendingIntentList(
                "pendingIntent",
                listOf(
                    PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setString("pendingIntent", "test")
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setInt("intArray", 100) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("intArray", longArrayOf(2, 3))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setLong("longArray", 100L) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("longArray", doubleArrayOf(2.0))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("floatArray", 1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("floatArray", doubleArrayOf(1.0))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("doubleArray", 1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("doubleArray", booleanArrayOf(false))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBoolean("booleanArray", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("booleanArray", listOf("test1"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("byteArray", booleanArrayOf(false, true))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setInt("byteArray", 1) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setString("stringList", "test1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("stringList", longArrayOf(1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setPendingIntent(
                "pendingIntentList",
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("pendingIntentList", listOf("string"))
        }
    }

    @Test
    fun testRead_asParameters_notConformSpec() {
        val builder =
            AppFunctionData.Builder(
                TEST_PARAMETER_METADATA,
                AppFunctionComponentsMetadata(),
            )
        builder.setInt("int", 234)
        builder.setLong("long", 123L)
        builder.setFloat("float", 100.0f)
        builder.setDouble("double", 50.0)
        builder.setBoolean("boolean", true)
        builder.setString("string", "testString")
        builder.setPendingIntent(
            "pendingIntent",
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        )
        builder.setIntArray("intArray", intArrayOf(4, 5, 6))
        builder.setLongArray("longArray", longArrayOf(1L, 2L, 3L))
        builder.setFloatArray("floatArray", floatArrayOf(10.0f, 20.0f, 30.0f))
        builder.setDoubleArray("doubleArray", doubleArrayOf(4.0, 5.0, 6.0))
        builder.setBooleanArray("booleanArray", booleanArrayOf(false, true, false))
        builder.setByteArray("byteArray", byteArrayOf(10.toByte(), 20.toByte()))
        builder.setStringList("stringList", listOf("1", "2", "3"))
        builder.setPendingIntentList(
            "pendingIntentList",
            listOf(PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE))
        )
        val data = builder.build()

        assertThrows(IllegalArgumentException::class.java) { data.getLong("int") }
        assertThrows(IllegalArgumentException::class.java) { data.getIntArray("int") }

        assertThrows(IllegalArgumentException::class.java) { data.getDouble("long") }
        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("long") }

        assertThrows(IllegalArgumentException::class.java) { data.getDouble("float") }
        assertThrows(IllegalArgumentException::class.java) { data.getFloatArray("float") }

        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("double") }
        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("double") }

        assertThrows(IllegalArgumentException::class.java) { data.getString("boolean") }
        assertThrows(IllegalArgumentException::class.java) { data.getBooleanArray("boolean") }

        assertThrows(IllegalArgumentException::class.java) { data.getLong("string") }
        assertThrows(IllegalArgumentException::class.java) { data.getStringList("string") }

        assertThrows(IllegalArgumentException::class.java) { data.getString("pendingIntent") }
        assertThrows(IllegalArgumentException::class.java) {
            data.getPendingIntentList("pendingIntent")
        }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("intArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getInt("intArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("longArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getLong("longArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("floatArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getFloat("floatArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getBooleanArray("doubleArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getDouble("doubleArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getStringList("booleanArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("booleanArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("byteArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("byteArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("stringList") }
        assertThrows(IllegalArgumentException::class.java) { data.getString("stringList") }

        assertThrows(IllegalArgumentException::class.java) {
            data.getStringList("pendingIntentList")
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.getPendingIntent("pendingIntentList")
        }
    }

    @Test
    fun testReadWrite_asObject_conformSpec() {
        val builder = AppFunctionData.Builder(TEST_OBJECT_METADATA, AppFunctionComponentsMetadata())

        builder.setInt("int", 234)
        builder.setLong("long", 123L)
        builder.setFloat("float", 100.0f)
        builder.setDouble("double", 50.0)
        builder.setBoolean("boolean", true)
        builder.setString("string", "testString")
        builder.setPendingIntent(
            "pendingIntent",
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        )
        builder.setIntArray("intArray", intArrayOf(4, 5, 6))
        builder.setLongArray("longArray", longArrayOf(1L, 2L, 3L))
        builder.setFloatArray("floatArray", floatArrayOf(10.0f, 20.0f, 30.0f))
        builder.setDoubleArray("doubleArray", doubleArrayOf(4.0, 5.0, 6.0))
        builder.setBooleanArray("booleanArray", booleanArrayOf(false, true, false))
        builder.setByteArray("byteArray", byteArrayOf(10.toByte(), 20.toByte()))
        builder.setStringList("stringList", listOf("1", "2", "3"))
        builder.setPendingIntentList(
            "pendingIntentList",
            listOf(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            )
        )
        val data = builder.build()

        assertThat(data.getInt("int")).isEqualTo(234)
        assertThat(data.getLong("long")).isEqualTo(123L)
        assertThat(data.getFloat("float")).isEqualTo(100.0f)
        assertThat(data.getDouble("double")).isEqualTo(50.0)
        assertThat(data.getBoolean("boolean")).isTrue()
        assertThat(data.getString("string")).isEqualTo("testString")
        assertThat(data.getPendingIntent("pendingIntent"))
            .isEqualTo(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            )
        assertThat(data.getIntArray("intArray")).asList().containsExactly(4, 5, 6)
        assertThat(data.getLongArray("longArray")).asList().containsExactly(1L, 2L, 3L)
        assertThat(data.getFloatArray("floatArray"))
            .usingExactEquality()
            .containsExactly(10.0f, 20.0f, 30.0f)
        assertThat(data.getDoubleArray("doubleArray"))
            .usingExactEquality()
            .containsExactly(4.0, 5.0, 6.0)
        assertThat(data.getBooleanArray("booleanArray"))
            .asList()
            .containsExactly(false, true, false)
        assertThat(data.getByteArray("byteArray"))
            .asList()
            .containsExactly(10.toByte(), 20.toByte())
        assertThat(data.getStringList("stringList")).containsExactly("1", "2", "3")
        assertThat(data.getPendingIntentList("pendingIntentList"))
            .containsExactly(
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getService(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE),
            )
    }

    @Test
    fun testWrite_asObject_notConformSpec() {
        val builder = AppFunctionData.Builder(TEST_OBJECT_METADATA, AppFunctionComponentsMetadata())

        assertThrows(IllegalArgumentException::class.java) {
            builder.setIntArray("int", intArrayOf(100, 200))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setLong("int", 50) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("long", longArrayOf(100, 200))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("long", 50.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setFloatArray("float", floatArrayOf(50.0f, 100.0f))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("float", 20.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("double", doubleArrayOf(50.0, 100.0))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setBoolean("double", true) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("boolean", booleanArrayOf(false, true))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setLong("boolean", 100) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("string", listOf("test"))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("string", 100.0) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setPendingIntentList(
                "pendingIntent",
                listOf(
                    PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setString("pendingIntent", "test")
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setInt("intArray", 100) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("intArray", longArrayOf(2, 3))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setLong("longArray", 100L) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("longArray", doubleArrayOf(2.0))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("floatArray", 1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setDoubleArray("floatArray", doubleArrayOf(1.0))
        }

        assertThrows(IllegalArgumentException::class.java) { builder.setDouble("doubleArray", 1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("doubleArray", booleanArrayOf(false))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBoolean("booleanArray", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("booleanArray", listOf("test1"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setBooleanArray("byteArray", booleanArrayOf(false, true))
        }
        assertThrows(IllegalArgumentException::class.java) { builder.setInt("byteArray", 1) }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setString("stringList", "test1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setLongArray("stringList", longArrayOf(1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            builder.setPendingIntent(
                "pendingIntentList",
                PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.setStringList("pendingIntentList", listOf("string"))
        }
    }

    @Test
    fun testRead_asObject_notConformSpec() {
        val builder = AppFunctionData.Builder(TEST_OBJECT_METADATA, AppFunctionComponentsMetadata())
        builder.setInt("int", 234)
        builder.setLong("long", 123L)
        builder.setFloat("float", 100.0f)
        builder.setDouble("double", 50.0)
        builder.setBoolean("boolean", true)
        builder.setString("string", "testString")
        builder.setPendingIntent(
            "pendingIntent",
            PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        )
        builder.setIntArray("intArray", intArrayOf(4, 5, 6))
        builder.setLongArray("longArray", longArrayOf(1L, 2L, 3L))
        builder.setFloatArray("floatArray", floatArrayOf(10.0f, 20.0f, 30.0f))
        builder.setDoubleArray("doubleArray", doubleArrayOf(4.0, 5.0, 6.0))
        builder.setBooleanArray("booleanArray", booleanArrayOf(false, true, false))
        builder.setByteArray("byteArray", byteArrayOf(10.toByte(), 20.toByte()))
        builder.setStringList("stringList", listOf("1", "2", "3"))
        builder.setPendingIntentList(
            "pendingIntentList",
            listOf(PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE))
        )
        val data = builder.build()

        assertThrows(IllegalArgumentException::class.java) { data.getLong("int") }
        assertThrows(IllegalArgumentException::class.java) { data.getIntArray("int") }

        assertThrows(IllegalArgumentException::class.java) { data.getDouble("long") }
        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("long") }

        assertThrows(IllegalArgumentException::class.java) { data.getDouble("float") }
        assertThrows(IllegalArgumentException::class.java) { data.getFloatArray("float") }

        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("double") }
        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("double") }

        assertThrows(IllegalArgumentException::class.java) { data.getString("boolean") }
        assertThrows(IllegalArgumentException::class.java) { data.getBooleanArray("boolean") }

        assertThrows(IllegalArgumentException::class.java) { data.getLong("string") }
        assertThrows(IllegalArgumentException::class.java) { data.getStringList("string") }

        assertThrows(IllegalArgumentException::class.java) { data.getString("pendingIntent") }
        assertThrows(IllegalArgumentException::class.java) {
            data.getPendingIntentList("pendingIntent")
        }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("intArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getInt("intArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("longArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getLong("longArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getDoubleArray("floatArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getFloat("floatArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getBooleanArray("doubleArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getDouble("doubleArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getStringList("booleanArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("booleanArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("byteArray") }
        assertThrows(IllegalArgumentException::class.java) { data.getBoolean("byteArray") }

        assertThrows(IllegalArgumentException::class.java) { data.getLongArray("stringList") }
        assertThrows(IllegalArgumentException::class.java) { data.getString("stringList") }

        assertThrows(IllegalArgumentException::class.java) {
            data.getStringList("pendingIntentList")
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.getPendingIntent("pendingIntentList")
        }
    }

    @Test
    fun testReadWrite_nestedObjectParameter() {
        val data =
            AppFunctionData.Builder(TEST_NESTED_PARAMETER_METADATA, AppFunctionComponentsMetadata())
                .setAppFunctionData(
                    "data",
                    AppFunctionData.Builder(TEST_OBJECT_METADATA, AppFunctionComponentsMetadata())
                        .setLong("long", 100)
                        .build()
                )
                .setAppFunctionDataList(
                    "dataList",
                    listOf(
                        AppFunctionData.Builder(
                                TEST_OBJECT_METADATA,
                                AppFunctionComponentsMetadata()
                            )
                            .setDouble("double", 20.0)
                            .build(),
                        AppFunctionData.Builder(
                                TEST_OBJECT_METADATA,
                                AppFunctionComponentsMetadata()
                            )
                            .setString("string", "testString")
                            .build()
                    )
                )
                .build()

        assertThat(data.getAppFunctionData("data")?.getLong("long")).isEqualTo(100)
        assertThat(data.getAppFunctionDataList("dataList")).hasSize(2)
        assertThat(data.getAppFunctionDataList("dataList")?.get(0)?.getDouble("double"))
            .isEqualTo(20.0)
        assertThat(data.getAppFunctionDataList("dataList")?.get(1)?.getString("string"))
            .isEqualTo("testString")
    }

    @Test
    fun testReadWrite_nestedAppFunctionData_conformSpec() {
        val innerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "innerDouble" to
                            AppFunctionPrimitiveTypeMetadata(
                                type = TYPE_DOUBLE,
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "innerData",
                isNullable = false,
            )
        val outerObjectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("nestedData" to innerObjectType),
                required = emptyList(),
                qualifiedName = "outerData",
                isNullable = false,
            )
        val innerDataBuilder =
            AppFunctionData.Builder(innerObjectType, AppFunctionComponentsMetadata())
        val outerDataBuilder =
            AppFunctionData.Builder(
                outerObjectType,
                AppFunctionComponentsMetadata(),
            )

        innerDataBuilder.setDouble("innerDouble", 500.0)
        outerDataBuilder.setAppFunctionData("nestedData", innerDataBuilder.build())
        val outerData = outerDataBuilder.build()

        assertThat(outerData.getAppFunctionData("nestedData")?.getDouble("innerDouble"))
            .isEqualTo(500.0)
    }

    @Test
    fun testWrite_nestedAppFunctionData_notConformSpec() {
        val innerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "innerDouble" to
                            AppFunctionPrimitiveTypeMetadata(
                                type = TYPE_DOUBLE,
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "innerData",
                isNullable = false,
            )
        val incorrectInnerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "innerDouble" to
                            AppFunctionPrimitiveTypeMetadata(
                                type = TYPE_LONG,
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "innerData",
                isNullable = false,
            )
        val outerObjectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("nestedData" to innerObjectType),
                required = emptyList(),
                qualifiedName = "outerData",
                isNullable = false,
            )
        val incorrectInnerDataBuilder =
            AppFunctionData.Builder(incorrectInnerObjectType, AppFunctionComponentsMetadata())
        val outerDataBuilder =
            AppFunctionData.Builder(
                outerObjectType,
                AppFunctionComponentsMetadata(),
            )

        incorrectInnerDataBuilder.setLong("innerDouble", 500)
        assertThrows(IllegalArgumentException::class.java) {
            outerDataBuilder.setAppFunctionData("nestedData", incorrectInnerDataBuilder.build())
        }
    }

    @Test
    fun testWrite_nestedListAppFunctionData_notConformSpec() {
        val innerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "innerDouble" to
                            AppFunctionPrimitiveTypeMetadata(
                                type = TYPE_DOUBLE,
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "innerData",
                isNullable = false,
            )
        val incorrectInnerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "innerDouble" to
                            AppFunctionPrimitiveTypeMetadata(
                                type = TYPE_LONG,
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "innerData",
                isNullable = false,
            )
        val outerObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf("nestedDataList" to AppFunctionArrayTypeMetadata(innerObjectType, false)),
                required = emptyList(),
                qualifiedName = "outerData",
                isNullable = false,
            )
        val correctInnerDataBuilder =
            AppFunctionData.Builder(innerObjectType, AppFunctionComponentsMetadata())
        val incorrectInnerDataBuilder =
            AppFunctionData.Builder(incorrectInnerObjectType, AppFunctionComponentsMetadata())
        val outerDataBuilder =
            AppFunctionData.Builder(
                outerObjectType,
                AppFunctionComponentsMetadata(),
            )

        correctInnerDataBuilder.setDouble("innerDouble", 500.0)
        incorrectInnerDataBuilder.setLong("innerDouble", 500)

        assertThrows(IllegalArgumentException::class.java) {
            outerDataBuilder.setAppFunctionDataList(
                "nestedDataList",
                listOf(correctInnerDataBuilder.build(), incorrectInnerDataBuilder.build())
            )
        }
    }

    @Test
    fun testSerialize() {
        val note = Note(title = "Test Title", attachment = Attachment(uri = "Test Uri"))

        val data = AppFunctionData.serialize(note, Note::class.java)

        assertThat(data.getString("title")).isEqualTo("Test Title")
        assertThat(data.getAppFunctionData("attachment")?.getString("uri")).isEqualTo("Test Uri")
    }

    @Test
    fun testSerialize_withQualifiedName() {
        val note = Note(title = "Test Title", attachment = Attachment(uri = "Test Uri"))

        val data = AppFunctionData.serialize(note, "androidx.appfunctions.Note")

        assertThat(data.getString("title")).isEqualTo("Test Title")
        assertThat(data.getAppFunctionData("attachment")?.getString("uri")).isEqualTo("Test Uri")
    }

    @Test
    fun testDeserialize() {
        val data =
            AppFunctionData.Builder("androidx.appfunctions.Note")
                .setString("title", "Test Title")
                .setAppFunctionData(
                    "attachment",
                    AppFunctionData.Builder("androidx.appfunctions.Attachment")
                        .setString("uri", "Test Uri")
                        .build()
                )
                .build()

        val note = data.deserialize(Note::class.java)

        assertThat(note.title).isEqualTo("Test Title")
        assertThat(note.attachment.uri).isEqualTo("Test Uri")
    }

    @Test
    fun testDeserialize_withQualifiedName() {
        val data =
            AppFunctionData.Builder("androidx.appfunctions.Note")
                .setString("title", "Test Title")
                .setAppFunctionData(
                    "attachment",
                    AppFunctionData.Builder("androidx.appfunctions.Attachment")
                        .setString("uri", "Test Uri")
                        .build()
                )
                .build()

        val note = data.deserialize<Note>("androidx.appfunctions.Note")

        assertThat(note.title).isEqualTo("Test Title")
        assertThat(note.attachment.uri).isEqualTo("Test Uri")
    }

    @Test
    fun testSerialize_missingFactory() {
        val missingFactoryClass = MissingFactoryClass("test")

        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionData.serialize(missingFactoryClass, MissingFactoryClass::class.java)
        }
    }

    @Test
    fun testDeserialize_missingFactory() {
        val data =
            AppFunctionData.Builder("androidx.appfunctions-MissingFactoryClass")
                .setString("item", "test")
                .build()

        assertThrows(IllegalArgumentException::class.java) {
            data.deserialize(MissingFactoryClass::class.java)
        }
    }

    companion object {
        val TEST_OBJECT_METADATA =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "int" to AppFunctionPrimitiveTypeMetadata(TYPE_INT, false),
                        "long" to AppFunctionPrimitiveTypeMetadata(TYPE_LONG, false),
                        "float" to AppFunctionPrimitiveTypeMetadata(TYPE_FLOAT, false),
                        "double" to AppFunctionPrimitiveTypeMetadata(TYPE_DOUBLE, false),
                        "boolean" to AppFunctionPrimitiveTypeMetadata(TYPE_BOOLEAN, false),
                        "string" to AppFunctionPrimitiveTypeMetadata(TYPE_STRING, false),
                        "pendingIntent" to
                            AppFunctionPrimitiveTypeMetadata(TYPE_PENDING_INTENT, false),
                        "intArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_INT, false),
                                isNullable = false,
                            ),
                        "longArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_LONG, false),
                                isNullable = false,
                            ),
                        "floatArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_FLOAT, false),
                                isNullable = false,
                            ),
                        "doubleArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_DOUBLE, false),
                                isNullable = false,
                            ),
                        "booleanArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_BOOLEAN, false),
                                isNullable = false,
                            ),
                        "byteArray" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_BYTES, false),
                                isNullable = false,
                            ),
                        "stringList" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionPrimitiveTypeMetadata(TYPE_STRING, false),
                                isNullable = false,
                            ),
                        "pendingIntentList" to
                            AppFunctionArrayTypeMetadata(
                                itemType =
                                    AppFunctionPrimitiveTypeMetadata(TYPE_PENDING_INTENT, false),
                                isNullable = false,
                            )
                    ),
                required = emptyList(),
                qualifiedName = "test",
                isNullable = false,
            )

        val TEST_PARAMETER_METADATA =
            listOf(
                AppFunctionParameterMetadata(
                    name = "int",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_INT,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "long",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_LONG,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "float",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_FLOAT,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "double",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_DOUBLE,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "boolean",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_BOOLEAN,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "string",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_STRING,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "pendingIntent",
                    isRequired = true,
                    dataType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = TYPE_PENDING_INTENT,
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "intArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_INT,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "longArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_LONG,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "floatArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_FLOAT,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "doubleArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_DOUBLE,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "booleanArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_BOOLEAN,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "byteArray",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_BYTES,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "stringList",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_STRING,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
                AppFunctionParameterMetadata(
                    name = "pendingIntentList",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType =
                                AppFunctionPrimitiveTypeMetadata(
                                    type = TYPE_PENDING_INTENT,
                                    isNullable = false,
                                ),
                            isNullable = false,
                        ),
                ),
            )
        val TEST_NESTED_PARAMETER_METADATA =
            listOf(
                AppFunctionParameterMetadata(
                    name = "data",
                    isRequired = true,
                    dataType = TEST_OBJECT_METADATA
                ),
                AppFunctionParameterMetadata(
                    name = "dataList",
                    isRequired = true,
                    dataType =
                        AppFunctionArrayTypeMetadata(
                            itemType = TEST_OBJECT_METADATA,
                            isNullable = false
                        )
                )
            )
    }
}
