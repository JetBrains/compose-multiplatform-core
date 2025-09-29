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

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Creates a [KSerializer] for a [SnapshotStateList] of [T] representing a back stack.
 *
 * This **no-configuration overload** uses an **Android-only reflective** strategy for polymorphic
 * element serialization. It does **not** require a [SerializersModule] on the [Encoder]/[Decoder];
 * the serializer is self-contained and resolves each element’s serializer reflectively at runtime.
 *
 * ### Platform behavior
 * - **Android:** Uses reflection for polymorphic dispatch and works with any `@Serializable`
 *   subtype of [T] (open or closed hierarchies). This path is convenient when keys are disparate
 *   classes spread across modules and central registration would create undue coupling.
 * - **Non-Android:** **Unsupported.** This overload will crash at runtime because no reflective
 *   implementation is available. Any call to [NavBackStackSerializer] with no configuration —
 *   including usage through `rememberSerializable` without explicitly providing a
 *   [SavedStateConfiguration] — will fail.
 *
 * ### Caveats
 * - Reflective lookup does **not** preserve generic type parameters (e.g., `List<Foo>` and
 *   `List<Bar>` are treated as `List<*>`). If you need precise generic handling or want
 *   cross-platform behavior, use the configuration overload with an explicit [SerializersModule].
 *
 * @sample androidx.navigation3.runtime.samples.NavBackStackSerializer_withReflection
 * @param T Element type stored in the back stack.
 * @return A [KSerializer] for a [SnapshotStateList] of [T] suitable for back stack persistence on
 *   Android.
 * @throws IllegalStateException if called on a non-Android platform, since reflection-based
 *   serialization is unsupported outside Android.
 */
@Suppress("FunctionName") // Factory function.
public fun <T : Any> NavBackStackSerializer(): KSerializer<SnapshotStateList<T>> {
    return SnapshotStateListSerializer(
        // Uses the reflective polymorphic strategy: no subtype registration or
        // SerializersModule needed. At runtime, reflection resolves `.serializer()`
        // for each element type automatically.
        elementSerializer = ReflectivePolymorphicSerializer()
    )
}

/**
 * A [KSerializer] that enables polymorphic serialization for navigation back stack entries on
 * Android using reflection, without requiring subtypes to be pre-registered.
 *
 * ## The Problem This Solves
 * Standard `kotlinx.serialization` polymorphism requires registering all possible subtypes in a
 * `SerializersModule`. For navigation, where screen destinations are often defined across different
 * modules, this is impractical and creates tight coupling.
 *
 * ## How It Works
 * This serializer circumvents the registration requirement by storing the fully-qualified class
 * name of the object alongside its serialized data. During deserialization, it uses reflection
 * (`Class.forName`) to find the class and its default serializer.
 *
 * This is an internal implementation detail and should not be used directly.
 */
@OptIn(InternalSerializationApi::class)
private class ReflectivePolymorphicSerializer<T : Any> : KSerializer<T> {

    override val descriptor =
        buildClassSerialDescriptor("PolymorphicData") {
            element(elementName = "type", serialDescriptor<String>())
            element(elementName = "payload", buildClassSerialDescriptor("Any"))
        }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): T {
        return decoder.decodeStructure(descriptor) {
            val className = decodeStringElement(descriptor, decodeElementIndex(descriptor))
            val classRef = Class.forName(className).kotlin
            val serializer = classRef.serializer()

            decodeSerializableElement(descriptor, decodeElementIndex(descriptor), serializer) as T
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeStructure(descriptor) {
            val className = value::class.java.name
            encodeStringElement(descriptor, index = 0, className)
            val serializer = value::class.serializer() as KSerializer<T>
            encodeSerializableElement(descriptor, index = 1, serializer, value)
        }
    }
}
