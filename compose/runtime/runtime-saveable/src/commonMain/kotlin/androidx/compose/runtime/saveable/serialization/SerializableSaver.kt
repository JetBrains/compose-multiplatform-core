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

package androidx.compose.runtime.saveable.serialization

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * The [Saver] implementation which allows to represent your class as a [SavedState] value to be
 * saved and restored using Kotlinx Serialization.
 *
 * This function infers the [KSerializer] for [Serializable] using the provided
 * [SavedStateConfiguration].
 *
 * You can use it as a parameter for [rememberSaveable].
 *
 * @param Serializable The [Serializable] to save and restore.
 * @param configuration The configuration for saving state (defaults to
 *   [SavedStateConfiguration.DEFAULT]).
 * @return A [Saver] for [Serializable].
 */
internal inline fun <reified Serializable : Any> serializableSaver(
    configuration: SavedStateConfiguration = SavedStateConfiguration.DEFAULT,
): Saver<Serializable, SavedState> {
    return serializableSaver(configuration.serializersModule.serializer(), configuration)
}

/**
 * The [Saver] implementation which allows to represent your class as a [SavedState] value to be
 * saved and restored using Kotlinx Serialization with a custom [KSerializer].
 *
 * This function explicitly uses the provided [serializer] for encoding and decoding.
 *
 * You can use it as a parameter for [rememberSaveable].
 *
 * @param Serializable The [Serializable] to save and restore.
 * @param serializer The [KSerializer] to use for encoding and decoding.
 * @param configuration The configuration for saving state (defaults to
 *   [SavedStateConfiguration.DEFAULT]).
 * @return A [Saver] for [Serializable].
 */
internal fun <Serializable : Any> serializableSaver(
    serializer: KSerializer<Serializable>,
    configuration: SavedStateConfiguration = SavedStateConfiguration.DEFAULT,
): Saver<Serializable, SavedState> {
    return Saver(
        save = { original -> encodeToSavedState(serializer, original, configuration) },
        restore = { savedState -> decodeFromSavedState(serializer, savedState, configuration) }
    )
}

/**
 * The [Saver] implementation which allows to represent your class as a [SavedState] value to be
 * saved and restored automatically choosing between [autoSaver] and Kotlinx Serialization based on
 * the object type.
 *
 * This function infers the [KSerializer] for [Serializable] using the provided
 * [SavedStateConfiguration].
 *
 * You can use it as a parameter for [rememberSaveable].
 *
 * @param Serializable The [Serializable] to save and restore.
 * @return A [Saver] for [Serializable].
 */
@PublishedApi
internal fun <Serializable : Any> serializableSaver(
    serializer: () -> KSerializer<Serializable>,
): Saver<Serializable, Any> {
    return Saver(
        save = { original ->
            if (original is SavedState || !canBeSaved(original)) {
                // Use KTX serialization to support `@Serializable` types.
                //
                // It's important to note that `SavedState` types *always* go through the KTX
                // serialization path. During the `restore` flow, limited type information
                // prevents distinguishing between a `SavedState` object passed in by the user
                // and one created internally by `decodeFromSavedState`. Routing all
                // `SavedState` objects through serialization ensures consistent handling.
                encodeToSavedState(serializer(), original)
            } else {
                original
            }
        },
        restore = { savedState ->
            val result =
                if (savedState is SavedState) {
                    // The stored value is SavedState, meaning it was serialized
                    // using KTX Serialization in the 'save' block.
                    decodeFromSavedState(serializer(), savedState)
                } else {
                    savedState
                }

            // This cast is safe but the compiler can't fully guarantee it based
            // on the 'Any' type used in the `Saver` signature.
            @Suppress("UNCHECKED_CAST") (result as Serializable)
        },
    )
}
