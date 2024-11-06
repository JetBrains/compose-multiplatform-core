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

@file:JvmName("SavedStateWriterKt")
@file:JvmMultifileClass
@file:Suppress("NOTHING_TO_INLINE")

package androidx.savedstate

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

@JvmInline
actual value class SavedStateWriter
@PublishedApi
internal actual constructor(
    @PublishedApi internal actual val source: SavedState,
) {

    actual inline fun putBoolean(key: String, value: Boolean) {
        source.map[key] = value
    }

    actual inline fun putChar(key: String, value: Char) {
        source.map[key] = value
    }

    actual inline fun putDouble(key: String, value: Double) {
        source.map[key] = value
    }

    actual inline fun putFloat(key: String, value: Float) {
        source.map[key] = value
    }

    actual inline fun putInt(key: String, value: Int) {
        source.map[key] = value
    }

    actual inline fun putLong(key: String, value: Long) {
        source.map[key] = value
    }

    actual inline fun putNull(key: String) {
        source.map[key] = null
    }

    actual inline fun putString(key: String, value: String) {
        source.map[key] = value
    }

    actual inline fun putIntList(key: String, values: List<Int>) {
        source.map[key] = values
    }

    actual inline fun putStringList(key: String, values: List<String>) {
        source.map[key] = values
    }

    actual inline fun putBooleanArray(key: String, values: BooleanArray) {
        source.map[key] = values
    }

    actual inline fun putCharArray(key: String, values: CharArray) {
        source.map[key] = values
    }

    actual inline fun putDoubleArray(key: String, values: DoubleArray) {
        source.map[key] = values
    }

    actual inline fun putFloatArray(key: String, values: FloatArray) {
        source.map[key] = values
    }

    actual inline fun putIntArray(key: String, values: IntArray) {
        source.map[key] = values
    }

    actual inline fun putLongArray(key: String, values: LongArray) {
        source.map[key] = values
    }

    actual inline fun putStringArray(key: String, values: Array<String>) {
        source.map[key] = values
    }

    actual inline fun putSavedState(key: String, value: SavedState) {
        source.map[key] = value
    }

    actual inline fun putAll(values: SavedState) {
        source.map.putAll(values.map)
    }

    actual inline fun remove(key: String) {
        source.map.remove(key)
    }

    actual inline fun clear() {
        source.map.clear()
    }
}
