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

@file:Suppress("UNUSED_VARIABLE")

package androidx.savedstate

import androidx.annotation.Sampled
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Sampled
fun encode() {
    @Serializable data class User(val id: Int, val name: String)
    val user = User(123, "foo")
    val savedState = encodeToSavedState(user)
}

@Sampled
fun encodeWithExplicitSerializer() {
    class UUIDSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("UUIDSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): UUID {
            return UUID.fromString(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: UUID) {
            encoder.encodeString(value.toString())
        }
    }
    encodeToSavedState(UUIDSerializer(), UUID.randomUUID())
}

val userSavedState = savedState {
    putInt("id", 123)
    putString("name", "foo")
}

val uuidSavedState = savedState { putString("", UUID.randomUUID().toString()) }

@Sampled
fun decode() {
    @Serializable data class User(val id: Int, val name: String)
    val user = decodeFromSavedState<User>(userSavedState)
}

@Sampled
fun decodeWithExplicitSerializer() {
    class UUIDSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("UUIDSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): UUID {
            return UUID.fromString(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: UUID) {
            encoder.encodeString(value.toString())
        }
    }
    val uuid = decodeFromSavedState(UUIDSerializer(), uuidSavedState)
}
