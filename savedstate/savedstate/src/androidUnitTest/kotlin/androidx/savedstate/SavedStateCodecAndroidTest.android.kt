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

import android.util.Size
import androidx.kruth.assertThat
import androidx.savedstate.SavedStateCodecTestUtils.encodeDecode
import java.util.UUID
import kotlin.test.Test
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@ExperimentalSerializationApi
internal class SavedStateCodecAndroidTest : RobolectricTest() {
    @Test
    fun javaAndAndroidClasses() {
        val uuid = UUID.randomUUID()

        uuid.encodeDecode(UUIDSerializer()) {
            assertThat(size()).isEqualTo(1)
            assertThat(getString("")).isEqualTo(uuid.toString())
        }
        Size(128, 256).encodeDecode(SizeSerializer()) {
            assertThat(size()).isEqualTo(2)
            assertThat(getInt("width")).isEqualTo(128)
            assertThat(getInt("height")).isEqualTo(256)
        }

        @Serializable
        data class MyModel(
            @Serializable(with = UUIDSerializer::class) val uuid: UUID,
            @Serializable(with = SizeSerializer::class) val size: Size
        )
        val uuid2 = UUID.randomUUID()
        MyModel(uuid2, Size(3, 5)).encodeDecode {
            assertThat(size()).isEqualTo(2)
            assertThat(getString("uuid")).isEqualTo(uuid2.toString())
            getSavedState("size").read {
                assertThat(size()).isEqualTo(2)
                assertThat(getInt("width")).isEqualTo(3)
                assertThat(getInt("height")).isEqualTo(5)
            }
        }
    }
}

private class UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("UUIDSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

private class SizeSerializer : KSerializer<Size> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("SizeDescriptor") {
                element("width", PrimitiveSerialDescriptor("width", PrimitiveKind.INT))
                element("height", PrimitiveSerialDescriptor("height", PrimitiveKind.INT))
            }

    override fun deserialize(decoder: Decoder): Size {
        return decoder.decodeStructure(descriptor) {
            var width = 0
            var height = 0
            while (true) {
                when (decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> width = decodeIntElement(descriptor, 0)
                    1 -> height = decodeIntElement(descriptor, 1)
                    else -> error("what?")
                }
            }
            Size(width, height)
        }
    }

    override fun serialize(encoder: Encoder, value: Size) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.width)
            encodeIntElement(descriptor, 1, value.height)
        }
    }
}
