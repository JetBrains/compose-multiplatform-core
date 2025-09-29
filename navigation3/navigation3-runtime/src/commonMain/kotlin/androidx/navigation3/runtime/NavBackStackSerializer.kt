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

package androidx.navigation3.runtime

import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Creates a [KSerializer] for a [SnapshotStateList] of [T] representing a back stack.
 *
 * This **configuration overload** resolves element serializers **from the provided
 * [SavedStateConfiguration.serializersModule]**, making it suitable for both open and closed
 * polymorphism on all platforms.
 *
 * Use this serializer when wiring a back stack ([SnapshotStateList] of [T]) into saved state (e.g.,
 * with [rememberSerializable] or any API that accepts a [KSerializer]).
 *
 * **Important:** The element serializer is looked up via the **[SerializersModule] on the
 * [Encoder]/[Decoder]**, not stored inside the [KSerializer] returned here. That means:
 * - When you obtain a serializer with this overload, you must also pass the **same**
 *   [SavedStateConfiguration] (or at least its [SerializersModule]) to your encode/decode calls.
 *   Otherwise polymorphic dispatch will fail at runtime with “Serializer for subclass … is not
 *   found”.
 * - This behavior applies **even on Android** when you pass `SavedStateConfiguration.DEFAULT`:
 *   since you supplied a configuration, element serializers are resolved **from its module** (no
 *   reflection is used in this path).
 *
 * ### Platform behavior
 * - **Android & Non-Android:** Always resolves element serializers from
 *   [SavedStateConfiguration.serializersModule]. Works for both **closed** (sealed) and **open**
 *   (interface / non-sealed) hierarchies, provided the module is configured accordingly.
 *
 * ### Closed vs open polymorphism
 * - **Closed hierarchies** (sealed): all subtypes are known, so serializers can be generated; you
 *   may still use a module, but it isn’t strictly required.
 * - **Open hierarchies** (interfaces, non-sealed): require a [SerializersModule] that registers the
 *   base + subclasses for polymorphic dispatch.
 *
 * @sample androidx.navigation3.runtime.samples.NavBackStackSerializer_withSerializersModule
 * @param T Element type stored in the back stack.
 * @param configuration Controls how element serializers are resolved.
 * @return A [KSerializer] for a [SnapshotStateList] of [T] suitable for back stack persistence.
 */
@Suppress("FunctionName") // Factory function.
public inline fun <reified T : Any> NavBackStackSerializer(
    configuration: SavedStateConfiguration
): KSerializer<SnapshotStateList<T>> {
    return SnapshotStateListSerializer(
        // Resolve the element serializer from the provided configuration’s serializersModule.
        // Important: you MUST pass the same module to your encode/decode calls. Polymorphic
        // dispatch depends on the Encoder/Decoder’s module, not on this KSerializer.
        elementSerializer = configuration.serializersModule.serializer<T>()
    )
}
