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

package androidx.aab

import androidx.aab.cli.VERBOSE
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile

/** Bundle information captured from `.dex` files (Not just classesXXX.dex, any .dex files) */
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

    /** Size of bytes in the uncompressed dex */
    val uncompressedSize: Long,

    /** Size of bytes in the zip container */
    val compressedSize: Long,

    /** r8 map id, if present in the dex strings */
    val r8MapId: String?,

    /** r8 markers */
    val r8Markers: List<R8Marker>,
) {
    init {
        require(uncompressedSize >= 0) { "Uncompressed size must be non-negative" }
    }

    data class R8Marker(val compiler: String, val map: Map<String, String>) {
        companion object {
            // """~~R8{"backend":"dex","compilation-mode":"release","has-checksums":false,"min-api":21,"pg-map-id":"17647d6605bb91237bf2b0766cb45b010d6e01aa899f20d7d6b08253bc38712e","r8-mode":"full","sha-1":"4ce18528a68a4b7401548810621405baaf439a48","version":"8.12.13-dev"}"""
            fun from(markerString: String): R8Marker {
                val entries = markerString.substringAfter('{').substringBefore('}').split(',')
                return R8Marker(
                    compiler = markerString.substring(2, markerString.indexOf("{")),
                    map =
                        entries.associate { it ->
                            val kv = it.split(':')
                            kv.first().removeSurrounding("\"") to kv.last().removeSurrounding("\"")
                        },
                )
            }
        }
    }

    companion object {

        fun from(entryName: String, compressedSize: Long, src: InputStream): DexInfo {
            val crc = CRC32()
            val sha256 = MessageDigest.getInstance("SHA-256")

            val bytes = src.readAllBytes()
            crc.update(bytes)
            sha256.update(bytes)
            val dexFile = DexBackedDexFile(Opcodes.getDefault(), bytes)

            val r8Markers = mutableListOf<R8Marker>()
            var r8MapId: String? = null
            dexFile.stringSection.forEach {
                if (it.startsWith("~~") && it.endsWith("}")) {
                    r8Markers.add(R8Marker.from(it))
                    // println(r8Markers.last())
                } else if (it.startsWith("r8-map-id-")) {
                    r8MapId = it
                }
            }

            // Finalize the SHA-256 hash and format it as a hex string.
            val sha256Bytes = sha256.digest()
            val sha256Hex = sha256Bytes.joinToString("") { "%02x".format(it) }
            val crc32Hex = crc.value.toInt().toHexString()

            // 4. Return the results in the data class.
            return DexInfo(
                entryName = entryName,
                crc32 = crc32Hex,
                sha256 = sha256Hex,
                uncompressedSize = bytes.size.toLong(),
                compressedSize = compressedSize,
                r8MapId = r8MapId,
                r8Markers = r8Markers,
            )
        }

        val CSV_TITLES =
            listOf("dex_totalSizeMb") +
                if (VERBOSE) {
                    listOf("dex_names", "dex_sortedChecksumsSha256", "dex_sortedChecksumsCrc32")
                } else {
                    emptyList()
                }

        fun List<DexInfo>.csvEntries(): List<String> {
            return listOf((this.sumOf { it.uncompressedSize } / (1024.0 * 1024)).toString()) +
                if (VERBOSE)
                    listOf(
                        joinToString(INTERNAL_CSV_SEPARATOR) { it.entryName },
                        // NOTE: we individually sort each of these, so they aren't associated with
                        // each other, but they are easy to compare when joined
                        this.map { it.sha256 }.sorted().joinToString(INTERNAL_CSV_SEPARATOR),
                        this.map { it.crc32 }.sorted().joinToString(INTERNAL_CSV_SEPARATOR),
                    )
                else {
                    emptyList()
                }
        }
    }
}
