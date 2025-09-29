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

import java.util.zip.ZipInputStream

/**
 * Bundle information captured from `proguard.map`
 *
 * Currently, it just includes information from the header
 */
data class MappingFileInfo(
    val compiler: String?,
    val compilerVersion: String?,
    val minApi: String?,
    val mappingVersion: String?,
    val pgMapId: String?,
    val pgMapHash: String?,
) {
    // Summary type of patterns we see in mapping files
    enum class Type {
        Minimal,
        R8,
        D8,
        Other,
        R8Incomplete,
        D8Incomplete,
        OtherIncomplete,
    }

    fun getType(): Type {
        if (
            this ==
                MappingFileInfo(
                    mappingVersion = this.mappingVersion,
                    compiler = null,
                    compilerVersion = null,
                    minApi = null,
                    pgMapId = null,
                    pgMapHash = null,
                )
        ) {
            return Type.Minimal
        }
        if (
            compiler == null ||
                compilerVersion == null ||
                minApi == null ||
                mappingVersion == null ||
                pgMapId == null ||
                pgMapHash == null
        ) {
            return when (compiler) {
                "R8" -> Type.R8Incomplete
                "D8" -> Type.D8Incomplete
                else -> Type.OtherIncomplete
            }
        }
        return when (compiler) {
            "R8" -> Type.R8
            "D8" -> Type.D8
            else -> Type.Other
        }
    }

    companion object {
        const val BUNDLE_LOCATION =
            "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"

        val CSV_TITLES = listOf("mapping_file_version", "mapping_file_type")

        fun MappingFileInfo?.csvEntries(): List<String> {
            return listOf(this?.mappingVersion.toString(), this?.getType().toString())
        }

        private fun List<String>.findAfterPrefix(prefix: String): String? {
            return firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        }

        private fun List<String>.findMappingVersion(): String? {
            val prefix = """# {"id":"com.android.tools.r8.mapping","version":""""
            return firstOrNull { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.substringBefore("\"")
        }

        private fun List<String>.findMappingVersionSQ(): String? {
            val prefix = """# {'id':'com.android.tools.r8.mapping','version':'"""
            return firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.substringBefore("'")
        }

        fun from(zis: ZipInputStream): MappingFileInfo {
            val reader = zis.bufferedReader()

            val lines = mutableListOf<String>()
            while (true) {
                val line = reader.readLine()?.trim()
                if (line == null || !line.startsWith('#')) break

                lines.add(line)
            }
            /**
             * Example preamble:
             * ```
             * # compiler: R8
             * # compiler_version: 8.10.18
             * # min_api: 22
             * # common_typos_disable
             * # {"id":"com.android.tools.r8.mapping","version":"2.2"}
             * # pg_map_id: 4be8256
             * # pg_map_hash: SHA-256 4be82561ad6ce216e1334c2143d280d8f812fcb21d7b3179135be0392f39fe15
             * ```
             */
            return MappingFileInfo(
                compiler = lines.findAfterPrefix("# compiler:"),
                compilerVersion = lines.findAfterPrefix("# compiler_version:"),
                minApi = lines.findAfterPrefix("# min_api:"),
                mappingVersion = lines.findMappingVersion() ?: lines.findMappingVersionSQ(),
                pgMapId = lines.findAfterPrefix("# pg_map_id:"),
                pgMapHash = lines.findAfterPrefix("# pg_map_hash:"),
            )
        }
    }
}
