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

package androidx.lifecycle

import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Returns a property delegate that uses [SavedStateHandle] to save and restore a value of type [T]
 * with fully qualified property or variable name as key and the default serializer.
 *
 * @sample androidx.lifecycle.delegate
 * @param init The function to provide the initial value of the property.
 * @return A property delegate that manages the saving and restoring of the value.
 */
inline fun <reified T : Any> SavedStateHandle.saved(
    noinline init: () -> T,
): ReadWriteProperty<Any?, T> {
    return saved(serializer(), init)
}

/**
 * Returns a property delegate that uses [SavedStateHandle] to save and restore a value of type [T]
 * with the default serializer.
 *
 * @sample androidx.lifecycle.delegateExplicitKey
 * @param key The [String] key to use for storing the value in the [SavedStateHandle].
 * @param init The function to provide the initial value of the property.
 * @return A property delegate that manages the saving and restoring of the value.
 */
inline fun <reified T : Any> SavedStateHandle.saved(
    key: String,
    noinline init: () -> T,
): ReadWriteProperty<Any?, T> {
    return saved(key, serializer(), init)
}

/**
 * Returns a property delegate that uses [SavedStateHandle] to save and restore a value of type [T]
 * with fully qualified property or variable name as key.
 *
 * @sample androidx.lifecycle.delegateExplicitSerializer
 * @param serializer The [KSerializer] to use for serializing and deserializing the value.
 * @param init The function to provide the initial value of the property.
 * @return A property delegate that manages the saving and restoring of the value.
 */
fun <T : Any> SavedStateHandle.saved(
    serializer: KSerializer<T>,
    init: () -> T,
): ReadWriteProperty<Any?, T> {
    return SerializablePropertyDelegate(
        savedStateHandle = this,
        key = null,
        serializer = serializer,
        init = init
    )
}

/**
 * Returns a property delegate that uses [SavedStateHandle] to save and restore a value of type [T].
 *
 * @sample androidx.lifecycle.delegateExplicitKeyAndSerializer
 * @param key The [String] key to use for storing the value in the [SavedStateHandle].
 * @param serializer The [KSerializer] to use for serializing and deserializing the value.
 * @param init The function to provide the initial value of the property.
 * @return A property delegate that manages the saving and restoring of the value.
 */
fun <T : Any> SavedStateHandle.saved(
    key: String,
    serializer: KSerializer<T>,
    init: () -> T,
): ReadWriteProperty<Any?, T> {
    return SerializablePropertyDelegate(
        savedStateHandle = this,
        key = key,
        serializer = serializer,
        init = init
    )
}

private class SerializablePropertyDelegate<T : Any>(
    private val savedStateHandle: SavedStateHandle,
    private val key: String?,
    private val serializer: KSerializer<T>,
    private val init: () -> T,
) : ReadWriteProperty<Any?, T> {
    private lateinit var value: T

    private fun lazyInit(thisRef: Any?, property: KProperty<*>) {
        if (::value.isInitialized) return

        val qualifiedKey =
            if (key != null) {
                key
            } else {
                val classNamePrefix =
                    if (thisRef != null) thisRef::class.qualifiedName + "." else ""
                classNamePrefix + property.name
            }

        val restoredState = savedStateHandle.get<SavedState>(qualifiedKey)
        val value =
            if (restoredState != null && restoredState.read { !isEmpty() }) {
                decodeFromSavedState(serializer, restoredState)
            } else {
                init()
            }
        this.value = value

        savedStateHandle.setSavedStateProvider(qualifiedKey) {
            encodeToSavedState(serializer, this.value)
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        lazyInit(thisRef, property)
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        lazyInit(thisRef, property)
        this.value = value
    }
}
