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

@file:JvmName("AndroidBrushFamilyExtensions")

package androidx.ink.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.rendering.android.TextureBitmapStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

@ExperimentalInkCustomBrushApi
public fun interface BrushFamilyDecodeCallback {
    /**
     * Called for each texture used by a BrushFamily when that BrushFamily is decoded. In the
     * implementation of this method, the returned String should be mapped to [bitmap] (or a client-
     * provided replacement) in the supporting [TextureBitmapStore].
     *
     * @param clientTextureId the ID for this texture in the serialized form of the BrushFamily.
     * @param bitmap the bitmap corresponding to clientTextureId in the serialized form of the
     *   BrushFamily. Null indicates that the serialized form did not store a bitmap for
     *   clientTextureId.
     */
    public fun onDecodeTexture(clientTextureId: String, bitmap: Bitmap?): String
}

/**
 * Write the gzip-compressed serialized representation of the [BrushFamily] to the given
 * [OutputStream].
 */
@ExperimentalInkCustomBrushApi
public fun BrushFamily.encode(output: OutputStream, textureBitmapStore: TextureBitmapStore) {
    val textureIdToNativeBitmaps: MutableMap<String, ByteArray> = mutableMapOf()

    for (coat in coats) {
        for (layer in coat.paint.textureLayers) {
            if (textureIdToNativeBitmaps.containsKey(layer.clientTextureId)) continue

            val bitmap = textureBitmapStore[layer.clientTextureId] ?: continue

            val pngBytes =
                ByteArrayOutputStream().use { outputStream ->
                    // Encode bitmap as PNG bytes. PNG is lossless, so the quality value is ignored.
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.toByteArray()
                }
            textureIdToNativeBitmaps[layer.clientTextureId] = pngBytes
        }
    }
    GZIPOutputStream(output).use {
        it.write(
            BrushSerializationNative.serializeBrushFamily(nativePointer, textureIdToNativeBitmaps)
        )
    }
}

/**
 * Read a serialized [BrushFamily] from the given [InputStream] and parse it into a [BrushFamily],
 * throwing an exception if parsing was not successful. The serialized representation is
 * gzip-compressed `ink.proto.BrushFamily` binary proto messages, the same as written to
 * [OutputStream] by [BrushFamily.encode]. Java callers should use
 * [AndroidBrushFamilySerialization.decodeOrThrow].
 *
 * [getClientTextureId] is called synchronously as part of this function call, on the same thread.
 */
@SuppressWarnings("ExecutorRegistration")
@ExperimentalInkCustomBrushApi
public fun BrushFamily.Companion.decodeOrThrow(
    input: InputStream,
    getClientTextureId: BrushFamilyDecodeCallback,
): BrushFamily = decode(input, getClientTextureId, throwOnParseError = true)!!

/**
 * Read a serialized [BrushFamily] from the given [InputStream] and parse it into a [BrushFamily],
 * returning `null` if parsing was not successful. The serialized representation is gzip-compressed
 * `ink.proto.BrushFamily` binary proto messages, the same as written to [OutputStream] by
 * [BrushFamily.encode]. Java callers should use [AndroidBrushFamilySerialization.decodeOrNull].
 *
 * [getClientTextureId] is called synchronously as part of this function call, on the same thread.
 */
@SuppressWarnings("ExecutorRegistration")
@ExperimentalInkCustomBrushApi
public fun BrushFamily.Companion.decodeOrNull(
    input: InputStream,
    getClientTextureId: BrushFamilyDecodeCallback,
): BrushFamily? = decode(input, getClientTextureId, throwOnParseError = false)

// Using an explicit singleton object instead of @file:JvmName to put the static interface intended
// for use from Java in a class because otherwise there are multiple top-level functions with the
// same name and signature on the Kotlin side. If one of those were used from Kotlin, it chooses and
// overload arbitrarily, which leads to potentially very confusing behavior (e.g. decodeOrNull might
// work by coincidence at one point and then suddenly stop working when more overloads are added).

@ExperimentalInkCustomBrushApi
public object AndroidBrushFamilySerialization {
    /**
     * Write the gzip-compressed serialized representation of the [BrushFamily] to the given
     * [OutputStream]. Kotlin callers should use [BrushFamily.encode] instead.
     */
    @JvmStatic
    public fun encode(
        brushFamily: BrushFamily,
        output: OutputStream,
        textureBitmapStore: TextureBitmapStore,
    ) {
        brushFamily.encode(output, textureBitmapStore)
    }

    /**
     * Read a serialized [BrushFamily] from the given [InputStream] and parse it into a
     * [BrushFamily], throwing an exception if parsing was not successful. The serialized
     * representation is gzip-compressed `ink.proto.BrushFamily` binary proto messages, the same as
     * written to [OutputStream] by [encode]. Kotlin callers should use
     * [BrushFamily.Companion.decodeOrThrow] instead.
     *
     * [getClientTextureId] is called synchronously as part of this function call, on the same
     * thread.
     */
    @SuppressWarnings("ExecutorRegistration")
    @JvmStatic
    public fun decodeOrThrow(
        input: InputStream,
        getClientTextureId: BrushFamilyDecodeCallback,
    ): BrushFamily = BrushFamily.decodeOrThrow(input, getClientTextureId)

    /**
     * Read a serialized [BrushFamily] from the given [InputStream] and parse it into a
     * [BrushFamily], returning `null` if parsing was not successful. The serialized representation
     * is gzip-compressed `ink.proto.BrushFamily` binary proto messages, the same as written to
     * [OutputStream] by [encode]. Kotlin callers should use [BrushFamily.Companion.decodeOrNull]
     * instead.
     *
     * [getClientTextureId] is called synchronously as part of this function call, on the same
     * thread.
     */
    @SuppressWarnings("ExecutorRegistration")
    @JvmStatic
    public fun decodeOrNull(
        input: InputStream,
        getClientTextureId: BrushFamilyDecodeCallback,
    ): BrushFamily? = BrushFamily.decodeOrNull(input, getClientTextureId)
}

/**
 * A helper for the public functions for decoding a [BrushFamily] from an [InputStream] providing
 * the serialized representation put to an [OutputStream] by [BrushFamily.encode]. The serialized
 * representation is gzip-compressed `ink.proto.BrushFamily` binary proto messages.
 *
 * @param input The [InputStream] to read the serialized [BrushFamily] from.
 * @param getClientTextureId The [BrushFamilyDecodeCallback] to call with the decoded textures.
 * @param throwOnParseError Configuration flag for whether to throw (`true`) or return null
 *   (`false`) when the underlying parsing fails. If an exception is thrown, it should have a
 *   descriptive error message.
 */
@ExperimentalInkCustomBrushApi
private fun decode(
    input: InputStream,
    getClientTextureId: BrushFamilyDecodeCallback,
    throwOnParseError: Boolean,
): BrushFamily? {
    val decompressed =
        try {
            DecompressedBytes(input)
        } catch (e: IOException) {
            if (throwOnParseError) {
                throw e
            }
            return null
        }
    val convertPngBytesToAndroidBitmapAndCallAndroidCallback: (String, ByteArray?) -> String =
        { textureId: String, pngBytes: ByteArray? ->
            val bitmap: Bitmap? = pngBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            getClientTextureId.onDecodeTexture(textureId, bitmap)
        }

    val nativePointer =
        BrushSerializationNative.newBrushFamilyFromProto(
            brushFamilyDirectByteBuffer = null,
            brushFamilyByteArray = decompressed.bytes,
            offset = 0,
            length = decompressed.size,
            throwOnParseError = throwOnParseError,
            callback = convertPngBytesToAndroidBitmapAndCallAndroidCallback,
        )
    if (nativePointer == 0L) {
        check(!throwOnParseError) {
            "throwOnParseError is set and the native call returned a zero memory address."
        }
        return null
    }
    return BrushFamily.wrapNative(nativePointer)
}
