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

package androidx.savedstate.serialization

import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.read
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Returns a property delegate provider that manages the saving and restoring of a value of type [T]
 * within the [SavedStateRegistry] of this [SavedStateRegistryOwner].
 *
 * @sample androidx.savedstate.serialization.savedStateRegistryOwner_saved_withKey_withSerializer
 * @param key The [String] key to use for storing the value in the [SavedStateRegistry].
 * @param serializer The [KSerializer] to use for serializing and deserializing the value.
 * @param init The function to provide the initial value of the property.
 * @return A property delegate provider that manages the saving and restoring of the value.
 * @see encodeToSavedState
 * @see decodeFromSavedState
 */
public fun <T : Any> SavedStateRegistryOwner.saved(
    key: String,
    serializer: KSerializer<T>,
    init: () -> T,
): ReadWriteProperty<Any?, T> {
    return SavedStateReadWriteProperty(
        registry = savedStateRegistry,
        key = key,
        serializer = serializer,
        init = init
    )
}

/**
 * Returns a property delegate provider that manages the saving and restoring of a value of type [T]
 * within the [SavedStateRegistry] of this [SavedStateRegistryOwner].
 *
 * This is a convenience overload that uses the name of the property as the key for storing the
 * value in the [SavedStateRegistry]
 *
 * @sample androidx.savedstate.serialization.savedStateRegistryOwner_saved_withSerializer
 * @param serializer The [KSerializer] to use for serializing and deserializing the value.
 * @param init The function to provide the initial value of the property.
 * @return A property delegate provider that manages the saving and restoring of the value.
 * @see encodeToSavedState
 * @see decodeFromSavedState
 */
public fun <T : Any> SavedStateRegistryOwner.saved(
    serializer: KSerializer<T>,
    init: () -> T,
): ReadWriteProperty<Any?, T> {
    return SavedStateReadWriteProperty(
        registry = savedStateRegistry,
        key = null,
        serializer = serializer,
        init = init
    )
}

/**
 * Returns a property delegate provider that manages the saving and restoring of a value of type [T]
 * within the [SavedStateRegistry] of this [SavedStateRegistryOwner].
 *
 * @sample androidx.savedstate.serialization.savedStateRegistryOwner_saved_withKey
 * @param key The [String] key to use for storing the value in the [SavedStateRegistry].
 * @param init The function to provide the initial value of the property.
 * @return A property delegate provider that manages the saving and restoring of the value.
 * @see encodeToSavedState
 * @see decodeFromSavedState
 * @see serializer
 */
public inline fun <reified T : Any> SavedStateRegistryOwner.saved(
    key: String,
    noinline init: () -> T,
): ReadWriteProperty<Any?, T> = saved(key = key, serializer = serializer(), init = init)

/**
 * Returns a property delegate provider that manages the saving and restoring of a value of type [T]
 * within the [SavedStateRegistry] of this [SavedStateRegistryOwner].
 *
 * This is a convenience overload that uses the [serializer] function to obtain the serializer for
 * the reified type [T].
 *
 * The name of the property will be used as the key for storing the value in the
 * [SavedStateRegistry].
 *
 * @sample androidx.savedstate.serialization.savedStateRegistryOwner_saved
 * @param init The function to provide the initial value of the property.
 * @return A property delegate provider that manages the saving and restoring of the value.
 * @see encodeToSavedState
 * @see decodeFromSavedState
 * @see serializer
 */
public inline fun <reified T : Any> SavedStateRegistryOwner.saved(
    noinline init: () -> T,
): ReadWriteProperty<Any?, T> = saved(serializer = serializer(), init = init)

private class SavedStateReadWriteProperty<T : Any>(
    private val registry: SavedStateRegistry,
    private val key: String?,
    private val serializer: KSerializer<T>,
    private val init: () -> T,
) : ReadWriteProperty<Any?, T> {

    private lateinit var value: T

    private fun lazyInit(thisRef: Any?, property: KProperty<*>) {
        if (::value.isInitialized) return

        val classNamePrefix = if (thisRef != null) thisRef::class.qualifiedName + "." else ""
        val qualifiedKey = key ?: (classNamePrefix + property.name)

        val restoredState = registry.consumeRestoredStateForKey(qualifiedKey)
        val initialValue =
            if (restoredState != null && restoredState.read { !isEmpty() }) {
                decodeFromSavedState(serializer, restoredState)
            } else {
                init()
            }

        registry.registerSavedStateProvider(qualifiedKey) {
            encodeToSavedState(serializer, this.value)
        }

        this.value = initialValue
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
