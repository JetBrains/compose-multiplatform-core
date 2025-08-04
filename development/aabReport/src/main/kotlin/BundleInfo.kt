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

package androidx.bundle

import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.collections.map

class MappingFileInfo()

data class R8MetadataFileInfo(
    val dexShas: Set<String>,
    val optimizationEnabled: Boolean,
    val obfuscationEnabled: Boolean,
    val shrinkingEnabled: Boolean,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(src: InputStream): R8MetadataFileInfo {
            val gson = Gson()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val metadata = gson.fromJson<Map<String, Any>>(src.bufferedReader().readText(), mapType)

            val options = (metadata["options"] as Map<String, Any>?)!!

            return R8MetadataFileInfo(
                dexShas =
                    (metadata["dexFiles"] as List<Map<String, Any>>)
                        .map { it["checksum"] as String }
                        .toSet(),
                optimizationEnabled = options["isObfuscationEnabled"] as Boolean,
                obfuscationEnabled = options["isObfuscationEnabled"] as Boolean,
                shrinkingEnabled = options["isShrinkingEnabled"] as Boolean,
            )
        }
    }
}

data class DexInfo(
    /** Entry name (relative path) within the containing bundle */
    val entryName: String,

    /**
     * crc32 of whole dex file
     *
     * Despite being 4 bytes this is *NOT* the dex-embedded checksum. It's the one embedded in
     * profiles to verify the dex they match with.
     */
    val crc32: String,

    /** Sha256 of whole file */
    val sha256: String,
) {
    companion object {
        fun from(entryName: String, src: InputStream): DexInfo {
            val crc = CRC32()
            val sha256 = MessageDigest.getInstance("SHA-256")

            // Process the stream in chunks, updating both hashes in the same loop.
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE) // Typically 8192
            generateSequence { src.read(buffer).takeIf { it != -1 } }
                .forEach { bytesRead ->
                    // Feed the same chunk of data to both algorithms
                    crc.update(buffer, 0, bytesRead)
                    sha256.update(buffer, 0, bytesRead)
                }

            // Finalize the SHA-256 hash and format it as a hex string.
            val sha256Bytes = sha256.digest()
            val sha256Hex = sha256Bytes.joinToString("") { "%02x".format(it) }
            val crc32Hex = crc.value.toInt().toHexString()

            // 4. Return the results in the data class.
            return DexInfo(entryName = entryName, crc32 = crc32Hex, sha256 = sha256Hex)
        }
    }
}

data class ProfileDexInfo(
    val profileKeySize: Int,
    val typeIdSetSize: Int,
    val hotMethodRegionSize: Long,
    val dexChecksumCrc32: String,
    val numMethodIds: Long,
    val profileKey: String,
) {

    // lovingly lifted from
    // profgen/src/main/kotlin/com/android/tools/profgen/ArtProfileSerializer.kt and friends
    companion object {
        internal fun byteArrayOf(vararg chars: Char) =
            ByteArray(chars.size) { chars[it].code.toByte() }

        val MAGIC_PROF = byteArrayOf('p', 'r', 'o', '\u0000')
        val VERSION_P = byteArrayOf('0', '1', '0', '\u0000')

        internal fun InputStream.readAndCheckProfileVersion() {
            val fileMagic = read(MAGIC_PROF.size)
            check(fileMagic.contentEquals(MAGIC_PROF))
            val version = read(VERSION_P.size)
            check(version.contentEquals(VERSION_P))
        }

        /**
         * Attempts to read {@param length} bytes from the input stream. If not enough bytes are
         * available it throws [IllegalStateException].
         */
        internal fun InputStream.read(length: Int): ByteArray {
            val buffer = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val result = read(buffer, offset, length - offset)
                if (result < 0) {
                    error("Not enough bytes to read: $length")
                }
                offset += result
            }
            return buffer
        }

        internal fun InputStream.readUInt8(): Int = readUInt(1).toInt()

        /** Reads the equivalent of an 16 bit unsigned integer (uint16_t in c++). */
        internal fun InputStream.readUInt16(): Int = readUInt(2).toInt()

        /** Reads the equivalent of an 32 bit unsigned integer (uint32_t in c++). */
        internal fun InputStream.readUInt32(): Long = readUInt(4)

        internal fun InputStream.readUInt(numberOfBytes: Int): Long {
            val buffer = read(numberOfBytes)
            // We use a long to cover for unsigned integer.
            var value: Long = 0
            for (k in 0 until numberOfBytes) {
                val next = buffer[k].toUByte().toLong()
                value += next shl k * java.lang.Byte.SIZE
            }
            return value
        }

        /**
         * Reads bytes from the stream and converts them to a string using UTF-8.
         *
         * @param size the number of bytes to read
         */
        internal fun InputStream.readString(size: Int): String =
            String(read(size), StandardCharsets.UTF_8)

        /**
         * Reads a compressed data region from the stream.
         *
         * @param compressedDataSize the size of the compressed data (bytes)
         * @param uncompressedDataSize the expected size of the uncompressed data (bytes)
         */
        internal fun InputStream.readCompressed(
            compressedDataSize: Int,
            uncompressedDataSize: Int,
        ): ByteArray {
            // Read the expected compressed data size.
            val inf = Inflater()
            val result = ByteArray(uncompressedDataSize)
            var totalBytesRead = 0
            var totalBytesInflated = 0
            val input = ByteArray(2048) // 2KB read window size;
            while (
                !inf.finished() && !inf.needsDictionary() && totalBytesRead < compressedDataSize
            ) {
                val bytesRead = read(input)
                if (bytesRead < 0) {
                    error(
                        "Invalid zip data. Stream ended after $totalBytesRead bytes. Expected $compressedDataSize bytes"
                    )
                }
                inf.setInput(input, 0, bytesRead)
                totalBytesInflated +=
                    inf.inflate(
                        result,
                        totalBytesInflated,
                        uncompressedDataSize - totalBytesInflated,
                    )
                totalBytesRead += bytesRead
            }
            if (totalBytesRead != compressedDataSize) {
                error(
                    "Didn't read enough bytes during decompression. expected=$compressedDataSize actual=$totalBytesRead"
                )
            }
            if (!inf.finished()) {
                error("Inflater did not finish")
            }
            return result
        }

        private fun InputStream.readUncompressedBody(numberOfDexFiles: Int): List<ProfileDexInfo> {
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyList()
            }
            // Read the dex file line headers.
            return List(numberOfDexFiles) {
                val profileKeySize = readUInt16()
                val typeIdSetSize = readUInt16()
                val hotMethodRegionSize = readUInt32()
                val dexChecksum = readUInt32()
                val numMethodIds = readUInt32()
                val profileKey = readString(profileKeySize)
                ProfileDexInfo(
                    profileKeySize = profileKeySize,
                    typeIdSetSize = typeIdSetSize,
                    hotMethodRegionSize = hotMethodRegionSize,
                    dexChecksumCrc32 = dexChecksum.toInt().toHexString(),
                    numMethodIds = numMethodIds,
                    profileKey = profileKey,
                )
            }

            // TODO: consider more verification here!
        }

        fun readFromProfile(src: InputStream): List<ProfileDexInfo> =
            with(src) {
                readAndCheckProfileVersion() // read 8
                val numberOfDexFiles = readUInt8()
                val uncompressedDataSize = readUInt32()
                val compressedDataSize = readUInt32()
                val uncompressedData =
                    readCompressed(compressedDataSize.toInt(), uncompressedDataSize.toInt())
                if (read() > 0) error("Content found after the end of file")

                val dataStream = uncompressedData.inputStream()

                dataStream.readUncompressedBody(numberOfDexFiles)
            }
    }
}

data class BundleInfo(
    // TODO: add AGP version here too
    val path: String,
    val profileDexInfo: List<ProfileDexInfo>,
    val dexInfo: List<DexInfo>,
    val mappingFileInfo: MappingFileInfo?,
    val r8MetadataFileInfo: R8MetadataFileInfo?,
    val dotVersionFiles: Map<String, String>, // map maven coordinates -> version number
    val appBundleDependencies: AppDependencies?,
) {
    companion object {
        fun from(path: String): BundleInfo {
            return FileInputStream(File(path)).use { from(path, it) }
        }

        fun from(path: String, inputStream: InputStream): BundleInfo {
            val dexInfo = mutableListOf<DexInfo>()
            val dotVersionFiles = mutableMapOf<String, String>()
            var mappingFileInfo: MappingFileInfo? = null
            var r8MetadataFileInfo: R8MetadataFileInfo? = null
            var appDependencies: AppDependencies? = null
            var profileDexInfo = emptyList<ProfileDexInfo>()
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry

                while (entry != null) {
                    if (VERBOSE && !entry.name.contains("/res/")) {
                        println(entry.name) // just for debugging
                    }
                    when {
                        entry.name.contains("/dex/classes") && entry.name.endsWith(".dex") -> {
                            dexInfo.add(DexInfo.from(entry.name, zis))
                        }

                        entry.name == BundlePaths.BASELINE_PROF_LOCATION -> {
                            profileDexInfo = ProfileDexInfo.readFromProfile(zis)
                        }

                        entry.name.endsWith(".version") && entry.name.contains("/META-INF/") -> {
                            dotVersionFiles[entry.name] = zis.bufferedReader().readText().trim()
                        }

                        entry.name == BundlePaths.R8_METADATA_LOCATION -> {
                            r8MetadataFileInfo = R8MetadataFileInfo.fromJson(zis)
                        }

                        entry.name == BundlePaths.DEPENDENCIES_PB_LOCATION -> {
                            appDependencies = AppDependencies.ADAPTER.decode(zis)
                        }

                        entry.name == BundlePaths.PROGUARD_MAP_LOCATION -> {
                            mappingFileInfo = MappingFileInfo()
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (VERBOSE) {
                appDependencies?.run {
                    // print all contained libraries
                    library.forEach {
                        it.maven_library?.run {
                            println("LIB: ${groupId}:${artifactId}:${version}")
                        }
                    }
                }
            }

            return BundleInfo(
                path = path,
                profileDexInfo = profileDexInfo,
                dexInfo = dexInfo,
                mappingFileInfo = mappingFileInfo,
                r8MetadataFileInfo = r8MetadataFileInfo,
                dotVersionFiles = dotVersionFiles,
                appBundleDependencies = appDependencies,
            )
        }
    }
}
