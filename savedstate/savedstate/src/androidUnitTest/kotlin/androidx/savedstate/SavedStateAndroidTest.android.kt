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

package androidx.savedstate

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.kruth.assertThat
import androidx.kruth.assertThrows
import java.io.FileDescriptor
import java.io.Serializable
import kotlin.test.Test

internal class ParcelableSavedStateTest : RobolectricTest() {

    @Test
    fun getBinder_whenSet_returns() {
        val underTest = savedState { putBinder(KEY_1, BINDER_VALUE_1) }
        val actual = underTest.read { getBinder(KEY_1) }

        assertThat(actual).isEqualTo(BINDER_VALUE_1)
    }

    @Test
    fun getBinder_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> { savedState().read { getBinder(KEY_1) } }
    }

    @Test
    fun getBinder_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> { underTest.read { getBinder(KEY_1) } }
    }

    @Test
    fun getBinderOrElse_whenSet_returns() {
        val underTest = savedState { putBinder(KEY_1, BINDER_VALUE_1) }
        val actual = underTest.read { getBinderOrElse(KEY_1) { BINDER_VALUE_2 } }

        assertThat(actual).isEqualTo(BINDER_VALUE_1)
    }

    @Test
    fun getBinderOrElse_whenNotSet_returnsElse() {
        val actual = savedState().read { getBinderOrElse(KEY_1) { BINDER_VALUE_2 } }

        assertThat(actual).isEqualTo(BINDER_VALUE_2)
    }

    @Test
    fun getBinderOrElse_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getBinderOrElse(KEY_1) { BINDER_VALUE_2 } }

        assertThat(actual).isEqualTo(BINDER_VALUE_2)
    }

    @Test
    fun getSize_whenSet_returns() {
        val underTest = savedState { putSize(KEY_1, SIZE_IN_PIXEL_VALUE_1) }
        val actual = underTest.read { getSize(KEY_1) }

        assertThat(actual).isEqualTo(SIZE_IN_PIXEL_VALUE_1)
    }

    @Test
    fun getSize_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> { savedState().read { getSize(KEY_1) } }
    }

    @Test
    fun getSize_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> { underTest.read { getSize(KEY_1) } }
    }

    @Test
    fun getSizeOrElse_whenSet_returns() {
        val underTest = savedState { putSize(KEY_1, SIZE_IN_PIXEL_VALUE_1) }
        val actual = underTest.read { getSizeOrElse(KEY_1) { SIZE_IN_PIXEL_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_PIXEL_VALUE_1)
    }

    @Test
    fun getSizeOrElse_whenNotSet_returnsElse() {
        val actual = savedState().read { getSizeOrElse(KEY_1) { SIZE_IN_PIXEL_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_PIXEL_VALUE_2)
    }

    @Test
    fun getSizeOrElse_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getSizeOrElse(KEY_1) { SIZE_IN_PIXEL_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_PIXEL_VALUE_2)
    }

    @Test
    fun getSizeF_whenSet_returns() {
        val underTest = savedState { putSizeF(KEY_1, SIZE_IN_FLOAT_VALUE_1) }
        val actual = underTest.read { getSizeF(KEY_1) }

        assertThat(actual).isEqualTo(SIZE_IN_FLOAT_VALUE_1)
    }

    @Test
    fun getSizeF_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> { savedState().read { getSizeF(KEY_1) } }
    }

    @Test
    fun getSizeF_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> { underTest.read { getSizeF(KEY_1) } }
    }

    @Test
    fun getSizeFOrElse_whenSet_returns() {
        val underTest = savedState { putSizeF(KEY_1, SIZE_IN_FLOAT_VALUE_1) }
        val actual = underTest.read { getSizeFOrElse(KEY_1) { SIZE_IN_FLOAT_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_FLOAT_VALUE_1)
    }

    @Test
    fun getSizeFOrElse_whenNotSet_returnsElse() {
        val actual = savedState().read { getSizeFOrElse(KEY_1) { SIZE_IN_FLOAT_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_FLOAT_VALUE_2)
    }

    @Test
    fun getSizeFOrElse_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getSizeFOrElse(KEY_1) { SIZE_IN_FLOAT_VALUE_2 } }

        assertThat(actual).isEqualTo(SIZE_IN_FLOAT_VALUE_2)
    }

    @Test
    fun getParcelable_whenSet_returns() {
        val underTest = savedState { putParcelable(KEY_1, PARCELABLE_VALUE_1) }
        val actual = underTest.read { getParcelable<TestParcelable>(KEY_1) }

        assertThat(actual).isEqualTo(PARCELABLE_VALUE_1)
    }

    @Test
    fun getParcelable_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> {
            savedState().read { getParcelable<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelable_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> {
            underTest.read { getParcelable<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelableOrElse_whenSet_returns() {
        val underTest = savedState { putParcelable(KEY_1, PARCELABLE_VALUE_1) }
        val actual = underTest.read { getParcelableOrElse(KEY_1) { PARCELABLE_VALUE_2 } }

        assertThat(actual).isEqualTo(PARCELABLE_VALUE_1)
    }

    @Test
    fun getParcelableOrElse_whenNotSet_returnsElse() {
        val actual = savedState().read { getParcelableOrElse(KEY_1) { PARCELABLE_VALUE_1 } }

        assertThat(actual).isEqualTo(PARCELABLE_VALUE_1)
    }

    @Test
    fun getParcelableOrElse_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getParcelableOrElse(KEY_1) { PARCELABLE_VALUE_1 } }

        assertThat(actual).isEqualTo(PARCELABLE_VALUE_1)
    }

    @Test
    fun getParcelableList_whenSet_returns() {
        val expected = List(size = 5) { idx -> TestParcelable(idx) }

        val underTest = savedState { putParcelableList(KEY_1, expected) }
        val actual = underTest.read { getParcelableList<TestParcelable>(KEY_1) }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getParcelableList_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> {
            savedState().read { getParcelableList<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelableList_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> {
            underTest.read { getParcelableList<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelableListOrElse_whenSet_returns() {
        val expected = List(size = 5) { idx -> TestParcelable(idx) }

        val underTest = savedState { putParcelableList(KEY_1, expected) }
        val actual =
            underTest.read { getParcelableListOrElse<TestParcelable>(KEY_1) { emptyList() } }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getParcelableListOrElse_whenNotSet_returnsElse() {
        val actual =
            savedState().read { getParcelableListOrElse<TestParcelable>(KEY_1) { emptyList() } }

        assertThat(actual).isEqualTo(emptyList<TestParcelable>())
    }

    @Test
    fun getParcelableListOrElse_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getParcelableListOrElse(KEY_1) { emptyList() } }

        assertThat(actual).isEqualTo(emptyList<Parcelable>())
    }

    @Test
    fun getParcelableArray_whenSet_returns() {
        val expected = Array(size = 5) { idx -> TestParcelable(idx) }

        val underTest = savedState { putParcelableArray(KEY_1, expected) }
        val actual = underTest.read { getParcelableArray<TestParcelable>(KEY_1) }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getParcelableArray_ofParcelable_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> {
            savedState().read { getParcelableArray<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelableArray_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> {
            underTest.read { getParcelableArray<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getParcelableArrayOrElse_whenSet_returns() {
        val expected = Array(size = 5) { idx -> TestParcelable(idx) }

        val underTest = savedState { putParcelableArray(KEY_1, expected) }
        val actual =
            underTest.read { getParcelableArrayOrElse<TestParcelable>(KEY_1) { emptyArray() } }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getParcelableArrayOrElse_ofParcelable_whenNotSet_returnsElse() {
        val actual =
            savedState().read { getParcelableArrayOrElse<TestParcelable>(KEY_1) { emptyArray() } }

        assertThat(actual).isEqualTo(emptyArray<TestParcelable>())
    }

    @Test
    fun getParcelableArrayOrElse_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getParcelableArrayOrElse(KEY_1) { emptyArray() } }

        assertThat(actual).isEqualTo(emptyArray<Parcelable>())
    }

    @Test
    fun getSparseParcelableArray_whenSet_returns() {
        val expected = SPARSE_PARCELABLE_ARRAY

        val underTest = savedState { putSparseParcelableArray(KEY_1, expected) }
        val actual = underTest.read { getSparseParcelableArray<TestParcelable>(KEY_1) }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getSparseParcelableArray_ofParcelable_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> {
            savedState().read { getSparseParcelableArray<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getSparseParcelableArray_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> {
            underTest.read { getSparseParcelableArray<TestParcelable>(KEY_1) }
        }
    }

    @Test
    fun getSparseParcelableArrayOrElse_whenSet_returns() {
        val expected = SPARSE_PARCELABLE_ARRAY

        val underTest = savedState { putSparseParcelableArray(KEY_1, expected) }
        val actual =
            underTest.read {
                getSparseParcelableArrayOrElse<TestParcelable>(KEY_1) { SparseArray() }
            }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getSparseParcelableArrayOrElse_ofParcelable_whenNotSet_returnsElse() {
        val expected = SPARSE_PARCELABLE_ARRAY

        val actual =
            savedState().read { getSparseParcelableArrayOrElse<TestParcelable>(KEY_1) { expected } }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getSparseParcelableArrayOrElse_whenSet_differentType_throws() {
        val expected = SPARSE_PARCELABLE_ARRAY

        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getSparseParcelableArrayOrElse(KEY_1) { expected } }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun getSerializable_whenSet_returns() {
        val underTest = savedState { putJavaSerializable(KEY_1, SERIALIZABLE_VALUE_1) }
        val actual = underTest.read { getJavaSerializable<TestSerializable>(KEY_1) }

        assertThat(actual).isEqualTo(SERIALIZABLE_VALUE_1)
    }

    @Test
    fun getSerializable_whenNotSet_throws() {
        assertThrows<IllegalArgumentException> {
            savedState().read { getJavaSerializable<TestSerializable>(KEY_1) }
        }
    }

    @Test
    fun getSerializable_whenSet_differentType_throws() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }

        assertThrows<IllegalStateException> {
            underTest.read { getJavaSerializable<TestSerializable>(KEY_1) }
        }
    }

    @Test
    fun getSerializableOrElse_whenSet_returns() {
        val underTest = savedState { putJavaSerializable(KEY_1, SERIALIZABLE_VALUE_1) }
        val actual = underTest.read { getJavaSerializableOrElse(KEY_1) { SERIALIZABLE_VALUE_2 } }

        assertThat(actual).isEqualTo(SERIALIZABLE_VALUE_1)
    }

    @Test
    fun getSerializableOrElse_whenNotSet_returnsElse() {
        val actual = savedState().read { getJavaSerializableOrElse(KEY_1) { SERIALIZABLE_VALUE_2 } }

        assertThat(actual).isEqualTo(SERIALIZABLE_VALUE_2)
    }

    @Test
    fun getSerializableOrElse_whenSet_differentType_returnsDefault() {
        val underTest = savedState { putInt(KEY_1, Int.MAX_VALUE) }
        val actual = underTest.read { getJavaSerializableOrElse(KEY_1) { SERIALIZABLE_VALUE_1 } }

        assertThat(actual).isEqualTo(SERIALIZABLE_VALUE_1)
    }

    private companion object {
        const val KEY_1 = "KEY_1"
        val SIZE_IN_PIXEL_VALUE_1 = Size(/* width= */ Int.MIN_VALUE, /* height */ Int.MIN_VALUE)
        val SIZE_IN_PIXEL_VALUE_2 = Size(/* width= */ Int.MAX_VALUE, /* height */ Int.MAX_VALUE)
        val SIZE_IN_FLOAT_VALUE_1 =
            SizeF(/* width= */ Float.MIN_VALUE, /* height */ Float.MIN_VALUE)
        val SIZE_IN_FLOAT_VALUE_2 =
            SizeF(/* width= */ Float.MAX_VALUE, /* height */ Float.MAX_VALUE)
        val BINDER_VALUE_1 = TestBinder(value = Int.MIN_VALUE)
        val BINDER_VALUE_2 = TestBinder(value = Int.MAX_VALUE)
        val PARCELABLE_VALUE_1 = TestParcelable(value = Int.MIN_VALUE)
        val PARCELABLE_VALUE_2 = TestParcelable(value = Int.MAX_VALUE)
        val SERIALIZABLE_VALUE_1 = TestSerializable(value = Int.MIN_VALUE)
        val SERIALIZABLE_VALUE_2 = TestSerializable(value = Int.MAX_VALUE)
        val SPARSE_PARCELABLE_ARRAY =
            SparseArray<TestParcelable>(/* initialCapacity= */ 5).apply {
                repeat(times = 5) { idx -> put(idx, TestParcelable(idx)) }
            }
    }

    internal data class TestBinder(val value: Int) : IBinder {
        override fun getInterfaceDescriptor() = error("")

        override fun pingBinder() = error("")

        override fun isBinderAlive() = error("")

        override fun queryLocalInterface(descriptor: String) = error("")

        override fun dump(fd: FileDescriptor, args: Array<out String>?) = error("")

        override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) = error("")

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int) = error("")

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) = error("")

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int) = error("")
    }

    internal data class TestParcelable(val value: Int) : Parcelable {

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(value)
        }

        companion object {
            @Suppress("unused")
            @JvmField
            val CREATOR =
                object : Parcelable.Creator<TestParcelable> {
                    override fun createFromParcel(source: Parcel) =
                        TestParcelable(value = source.readInt())

                    override fun newArray(size: Int) = arrayOfNulls<TestParcelable>(size)
                }
        }
    }

    internal data class TestSerializable(val value: Int) : Serializable
}
