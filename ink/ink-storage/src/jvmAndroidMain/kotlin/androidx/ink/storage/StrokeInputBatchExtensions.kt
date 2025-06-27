/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:JvmName("StrokeInputBatchExtensions")

package androidx.ink.storage

import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.StrokeInputBatch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/**
 * Write the gzip-compressed serialized representation of the [CodedStrokeInputBatch] to the given
 * [OutputStream].
 */
public fun StrokeInputBatch.encode(output: OutputStream) {
    check(nativePointer != 0L) { "the StrokeInputBatch is already closed" }
    GZIPOutputStream(output).use {
        it.write(StrokeInputBatchSerializationNative.serialize(nativePointer))
    }
}

/**
 * Read a serialized [CodedStrokeInputBatch] from the given [InputStream] and parse it into an
 * [ImmutableStrokeInputBatch], throwing an exception if parsing was not successful. The serialized
 * representation is gzip-compressed `ink.proto.CodedStrokeInputBatch` binary proto messages, the
 * same as written to [OutputStream] by [StrokeInputBatch.encode]. Java callers should use
 * [StrokeInputBatchSerialization.decodeOrThrow].
 */
public fun StrokeInputBatch.Companion.decodeOrThrow(input: InputStream): ImmutableStrokeInputBatch =
    decode(input, throwOnParseError = true)!!

/**
 * Read a serialized [CodedStrokeInputBatch] from the given [InputStream] and parse it into an
 * [ImmutableStrokeInputBatch], returning `null` if parsing was not successful. The serialized
 * representation is gzip-compressed `ink.proto.CodedStrokeInputBatch` binary proto messages, the
 * same as written to [OutputStream] by [StrokeInputBatch.encode]. Java callers should use
 * [StrokeInputBatchSerialization.decodeOrNull].
 */
public fun StrokeInputBatch.Companion.decodeOrNull(input: InputStream): ImmutableStrokeInputBatch? =
    decode(input, throwOnParseError = false)

// Using an explicit singleton object instead of @file:JvmName to put the static interface intended
// for use from Java in a class because otherwise there are multiple top-level functions with the
// same name and signature on the Kotlin side. If one of those were used from Kotlin, it chooses and
// overload arbitrarily, which leads to potentially very confusing behavior (e.g. decodeOrNull might
// work by coincidence at one point and then suddenly stop working when more overloads are added).

public object StrokeInputBatchSerialization {
    /**
     * Write the gzip-compressed serialized representation of the [CodedStrokeInputBatch] to the
     * given [OutputStream].
     */
    @JvmStatic
    public fun encode(strokeInputBatch: StrokeInputBatch, output: OutputStream): Unit =
        strokeInputBatch.encode(output)

    /**
     * Read a serialized [CodedStrokeInputBatch] from the given [InputStream] and parse it into an
     * [ImmutableStrokeInputBatch], throwing an exception if parsing was not successful. The
     * serialized representation is gzip-compressed `ink.proto.CodedStrokeInputBatch` binary proto
     * messages, the same as written to [OutputStream] by [encode]. Kotlin callers should use
     * [StrokeInputBatch.Companion.decodeOrThrow] instead.
     */
    @JvmStatic
    public fun decodeOrThrow(input: InputStream): ImmutableStrokeInputBatch =
        StrokeInputBatch.decodeOrThrow(input)

    /**
     * Read a serialized [CodedStrokeInputBatch] from the given [InputStream] and parse it into an
     * [ImmutableStrokeInputBatch], returning `null` if parsing was not successful. The serialized
     * representation is gzip-compressed `ink.proto.CodedStrokeInputBatch` binary proto messages,
     * the same as written to [OutputStream] by [encode]. Kotlin callers should use
     * [StrokeInputBatch.Companion.decodeOrNull] instead.
     */
    @JvmStatic
    public fun decodeOrNull(input: InputStream): ImmutableStrokeInputBatch? =
        StrokeInputBatch.decodeOrNull(input)
}

/**
 * A helper for the public functions for decoding a [CodedStrokeInputBatch] from an [InputStream]
 * providing the serialized representation put to an [OutputStream] by [StrokeInputBatch.encode].
 * The serialized representation is gzip-compressed `ink.proto.CodedStrokeInputBatch` binary proto
 * messages.
 *
 * @param throwOnParseError Configuration flag for whether to throw (`true`) or return null
 *   (`false`) when the underlying parsing fails. If an exception is thrown, it should have a
 *   descriptive error message.
 */
private fun decode(input: InputStream, throwOnParseError: Boolean): ImmutableStrokeInputBatch? {
    val decompressed =
        try {
            DecompressedBytes(input)
        } catch (e: IOException) {
            if (throwOnParseError) {
                throw e
            }
            return null
        }
    val nativePointer =
        StrokeInputBatchSerializationNative.newFromProto(
            directByteBuffer = null,
            byteArray = decompressed.bytes,
            offset = 0,
            length = decompressed.size,
            throwOnParseError = throwOnParseError,
        )
    if (nativePointer == 0L) {
        check(!throwOnParseError) {
            "throwOnParseError is set and the native call returned a zero memory address."
        }
        return null
    }
    return ImmutableStrokeInputBatch.wrapNative(nativePointer)
}
