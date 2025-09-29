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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = NavBackStack.Serializer::class)
public actual class NavBackStack<T : NavKey>
public actual constructor(private val base: SnapshotStateList<T>) :
    MutableList<T> by base, StateObject by base, RandomAccess by base {

    public actual constructor() : this(base = mutableStateListOf())

    public actual constructor(vararg elements: T) : this(base = mutableStateListOf(*elements))

    /**
     * A [KSerializer] for [NavBackStack].
     *
     * Delegates to [NavBackStackSerializer] to encode/decode the underlying [SnapshotStateList].
     * This indirection allows [NavBackStack] itself to be annotated with `@Serializable` while
     * still preserving Compose integration and delegating to [MutableList] and [StateObject].
     *
     * On Android, the default surrogate uses reflection to resolve subtypes of [NavKey]. On
     * non-Android platforms, use [NavBackStackSerializer] with an explicit
     * [SavedStateConfiguration] instead.
     *
     * @sample androidx.navigation3.runtime.samples.NavBackStack_Serializer
     */
    public class Serializer<T : NavKey> : KSerializer<NavBackStack<T>> {

        private val surrogate = NavBackStackSerializer<NavKey>()

        override val descriptor: SerialDescriptor
            get() = surrogate.descriptor

        override fun deserialize(decoder: Decoder): NavBackStack<T> {
            @Suppress("UNCHECKED_CAST")
            val base = decoder.decodeSerializableValue(surrogate) as SnapshotStateList<T>
            return NavBackStack(base)
        }

        override fun serialize(encoder: Encoder, value: NavBackStack<T>) {
            @Suppress("UNCHECKED_CAST")
            encoder.encodeSerializableValue(surrogate, value.base as SnapshotStateList<NavKey>)
        }
    }
}
