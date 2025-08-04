#!/usr/bin/env kotlin
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

/**
 * One time setup:
 * 1) download kotlin executable e.g. https://github.com/JetBrains/kotlin/releases/tag/v2.2.0
 *    (scroll to bottom of page)
 * 2) download wire compiler see https://square.github.io/wire/#generating-code-with-wire
 * 3) java -jar ~/Downloads/wire-compiler-5.3.5-jar-with-dependencies.jar --proto_path=./proto \
 *    --kotlin_out=./protokt app_bundle_dependencies_metadata.proto
 *
 * To run this script, e.g.:
 * > kotlin development/aabReport.main.kts ~/macrobenchmark-target-release.aab
 */
@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.squareup.wire:wire-runtime-jvm:5.3.5")
@file:Import("protokt/com/android/tools/build/libraries/metadata/Int32Value.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/LibraryDependencies.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/ModuleDependencies.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/IvyRepo.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/Repository.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/MavenRepo.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/Library.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/AppDependencies.kt")
@file:Import("protokt/com/android/tools/build/libraries/metadata/MavenLibrary.kt")
@file:OptIn(ExperimentalStdlibApi::class)

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
import kotlin.collections.set
import kotlin.system.exitProcess

val VERBOSE = false

private val BASELINE_PROF_LOCATION =
    "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof"
private val R8_METADATA_LOCATION = "BUNDLE-METADATA/com.android.tools/r8.json"
private val DEPENDENCIES_PB_LOCATION =
    "BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb"
private val PROGUARD_MAP_LOCATION =
    "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"

if (args.isEmpty()) {
    println("Expected one or more android app bundle files to be passed.")
    println("Usage:")
    println("     ./<path-to-script> <path-to-aab> [<path-to-aab2>...]")
    exitProcess(1)
}

@Suppress("UNCHECKED_CAST")
data class R8Info(
    val hasMappingFile: Boolean,
    val hasMetadata: Boolean,
    val dexShas: List<String>?,
    val optimizationEnabled: Boolean?,
    val obfuscationEnabled: Boolean?,
    val shrinkingEnabled: Boolean?,
) {
    constructor(
        hasMappingFile: Boolean,
        r8Metadata: Map<String, Any>?,
    ) : this(
        hasMappingFile = hasMappingFile,
        hasMetadata = r8Metadata != null,
        dexShas =
            (r8Metadata?.get("dexFiles") as List<Map<String, Any>>?)?.map {
                it["checksum"] as String
            },
        optimizationEnabled =
            (r8Metadata?.get("options") as Map<String, Any>?)?.get("isObfuscationEnabled")
                as Boolean,
        obfuscationEnabled =
            (r8Metadata?.get("options") as Map<String, Any>?)?.get("isObfuscationEnabled")
                as Boolean,
        shrinkingEnabled =
            (r8Metadata?.get("options") as Map<String, Any>?)?.get("isShrinkingEnabled") as Boolean,
    )
}

data class VersionInfo(
    val dotVersionFiles: Map<String, String>,
    val appDependencies: AppDependencies?,
)

data class BundleInfo(
    // TODO: add AGP version here too
    val path: String,
    val profileDexInfo: List<ProfileDexInfo>,
    val dexInfo: List<DexInfo>,
    val r8Info: R8Info,
    val versionInfo: VersionInfo,
) {
    fun reportProblems() {
        println("Analysis for bundle at $path")

        val dexCount = dexInfo.size
        val mismatchedChecksumCount =
            (dexInfo.map { it.crc32 }.toSet() - profileDexInfo.map { it.dexChecksumCrc32 }).size

        if (profileDexInfo.isEmpty()) {
            println(
                """
                WARNING - MISSING BASELINE PROFILES
                    No baseline profiles present in this app bundle.
                    IMPACT:
                          This will significantly reduce launch application performance as every
                          time your application is updated it will have its entire compilation state
                          reset entirely, exposing users to worst case JIT performance.
                    SUGGESTION:
                          Many standard Jetpack and 3rd party Android libraries have embedded
                          library profiles for years. Ensure that you're using a recent enough
                          version of AGP:
                          https://developer.android.com/topic/performance/baselineprofiles/overview#recommended-versions
                          Or if you're using a separate build system, ensure that library profiles
                          (embedded in both jars and aars) are merged, and compiled by profgen
                          (or profgen-cli).
            """
                    .trimIndent()
            )
        } else if (mismatchedChecksumCount == dexCount) {
            // TODO: check R8 dex SHAs as well, to more clearly explain cause
            println(
                """
                ERROR - BASELINE PROFILES FULLY CORRUPTED
                    All baseline profiles embedded in the app bundle are corrupted.
                    IMPACT:
                          This will significantly reduce launch application performance as every
                          time your application is updated it will have its entire compilation state
                          reset entirely, exposing users to worst case JIT performance.
                          Baseline profiles should alleviate this problem, but this app's profiles
                          will be ignored by the Android Runtime due to not matching any dex file.
                    SUGGESTION:
                          Ensure that the dex files produced by R8 (or in an unoptimized app, D8)
                          are directly embedded in the bundle. If you're using a dex post-processing
                          tool, verify that it is correctly consuming and translating baseline
                          profiles along with dex code.
            """
                    .trimIndent()
            )
        } else if (mismatchedChecksumCount > 0) {
            println(
                """
                ERROR - BASELINE PROFILES PARTIALLY CORRUPTED
                    Some baseline profiles embedded in the app bundle are corrupted.
                    $mismatchedChecksumCount / $dexCount dex files referenced in profile not found.
                    IMPACT:
                          This will significantly reduce launch application performance as every
                          time your application is updated it will have its entire compilation state
                          reset entirely, exposing users to worst case JIT performance.
                          Baseline profiles should alleviate this problem, but this app's profiles
                          will be ignored by the Android Runtime due to not matching any dex file.
                    SUGGESTION:
                          Ensure that the dex files produced by R8 (or in an unoptimized app, D8)
                          are directly embedded in the bundle. If you're using a dex post-processing
                          tool, verify that it is correctly consuming and translating baseline
                          profiles along with dex code.
            """
                    .trimIndent()
            )
        }
        if (!r8Info.hasMetadata) {
            println(
                """
                NOTE - MISSING R8 METADATA
                    No r8 metadata is present in at $R8_METADATA_LOCATION
                    IMPACT:
                          This tool will not be able to report high level optimization quality
                          metrics, or identify if important optimizations are missing.
                    SUGGESTION:
                          Ensure your app is using a sufficiently recent version of AGP (8.8+), or
                          if you're using a separate build system, manually extract this from R8,
                          for example with R8Command.Builder.setBuildMetadataConsumer(), and place
                          this in the app bundle at $R8_METADATA_LOCATION.
                """
                    .trimIndent()
            )
        }
        if (r8Info.hasMetadata && r8Info.dexShas?.toSet() != dexInfo.map { it.sha256 }.toSet()) {
            println(
                """
                NOTE - R8 DEX CHECKSUMS DO NOT MATCH DEX FILES
                    R8 metadata dex shas present in at $R8_METADATA_LOCATION do not match dex files
                    present in the bundle.
                    IMPACT:
                          This tool will not be able to verify any optimizations performed by R8 are
                          actually respected in the output dex.
                    SUGGESTION:
                          Ensure dex files produced by R8 are embedded into the app bundle, and any
                          and all expected optimizations from R8 are preserved.
                """
                    .trimIndent()
            )
        }
        if (!r8Info.hasMetadata && !r8Info.hasMappingFile) {
            // TODO: consider looking for dex R8 marker with backend=dex as fallback
            println(
                """
                WARNING - LIKELY UNOPTIMIZED - MISSING MAPPING FILE AND R8 METADATA
                    It is likely that this application was not optimized with R8
                    IMPACT:
                          This will significantly reduce performance and increase memory usage of
                          this application.
                    SUGGESTION:
                          Enable R8: https://d.android.com/r8
            """
                    .trimIndent()
            )
        } else if (
            r8Info.hasMetadata && r8Info.shrinkingEnabled == false ||
                r8Info.obfuscationEnabled == false ||
                r8Info.optimizationEnabled == false
        ) {
            println(
                """
                WARNING - R8 - PRIMARY OPTIMIZATION
                    Application missing one of the following primary optimization flags from R8:
                      shrinking enabled    = ${r8Info.shrinkingEnabled}
                      obfuscation enabled  = ${r8Info.obfuscationEnabled}
                      optimization enabled = ${r8Info.optimizationEnabled}
                    IMPACT:
                          This will significantly reduce performance and increase memory usage of
                          this application.
                    SUGGESTION:
                          Avoid using any of the top level -dont*** flags in R8.
                          When building with AGP, You can see your full R8 configuration at a path
                          like: ".../build/outputs/mapping/release/configuration.txt"
            """
                    .trimIndent()
            )
        }

        if (versionInfo.dotVersionFiles.isEmpty() && versionInfo.appDependencies == null) {
            println(
                """
                NOTE - MISSING VERSION INFO
                    No library version metadata present in either location:
                      legacy = .../META-INF/*.version
                      standard = $DEPENDENCIES_PB_LOCATION
                    IMPACT:
                          This tool will skip several verifications, and library developers will not
                          be able to notify this app of high impact security/crash issues via e.g.
                          Play SDK Console.
                    SUGGESTION:
                          Either ensure this app is using a sufficiently recent version of
                          AGP (TODO: VERSION), or if using a separate build system, ensure that your
                          build embeds library information into $DEPENDENCIES_PB_LOCATION.
                """
                    .trimIndent()
            )
        }

        // TODO compose version
        // TODO dont embed compose tooling
        // https://maven.google.com/web/index.html?q=tooling#androidx.compose.ui:ui-tooling
    }
}

// lovingly lifted from
// http://google3/third_party/java_src/android_profgen/profgen/src/main/kotlin/com/android/tools/profgen/ArtProfileSerializer.kt;l=907;rcl=609787628

internal fun byteArrayOf(vararg chars: Char) = ByteArray(chars.size) { chars[it].code.toByte() }

val MAGIC_PROF = byteArrayOf('p', 'r', 'o', '\u0000')
val VERSION_P = byteArrayOf('0', '1', '0', '\u0000')

internal fun InputStream.readAndCheckProfileVersion() {
    val fileMagic = read(MAGIC_PROF.size)
    check(fileMagic.contentEquals(MAGIC_PROF))
    val version = read(VERSION_P.size)
    check(version.contentEquals(VERSION_P))
}

/**
 * Attempts to read {@param length} bytes from the input stream. If not enough bytes are available
 * it throws [IllegalStateException].
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
 * Reads bytes from the stream and coverts them to a string using UTF-8.
 *
 * @param size the number of bytes to read
 */
internal fun InputStream.readString(size: Int): String = String(read(size), StandardCharsets.UTF_8)

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
    while (!inf.finished() && !inf.needsDictionary() && totalBytesRead < compressedDataSize) {
        val bytesRead = read(input)
        if (bytesRead < 0) {
            error(
                "Invalid zip data. Stream ended after $totalBytesRead bytes. Expected $compressedDataSize bytes"
            )
        }
        inf.setInput(input, 0, bytesRead)
        totalBytesInflated +=
            inf.inflate(result, totalBytesInflated, uncompressedDataSize - totalBytesInflated)
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

data class ProfileDexInfo(
    val profileKeySize: Int,
    val typeIdSetSize: Int,
    val hotMethodRegionSize: Long,
    val dexChecksumCrc32: String,
    val numMethodIds: Long,
    val profileKey: String,
)

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

fun readProfileInfo(src: InputStream): List<ProfileDexInfo> =
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

fun bundleInfoFrom(path: String): BundleInfo {
    return FileInputStream(File(path)).use { bundleInfoFrom(path, it) }
}

private fun bundleInfoFrom(path: String, inputStream: InputStream): BundleInfo {
    val dexInfo = mutableListOf<DexInfo>()
    val dotVersionFiles = mutableMapOf<String, String>()
    var hasMappingFile = false
    var r8Metadata: Map<String, Any>? = null
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
                entry.name == BASELINE_PROF_LOCATION -> {
                    profileDexInfo = readProfileInfo(zis)
                }
                entry.name.endsWith(".version") && entry.name.contains("/META-INF/") -> {
                    dotVersionFiles[entry.name] = zis.bufferedReader().readText().trim()
                }
                entry.name == R8_METADATA_LOCATION -> {
                    val gson = Gson()
                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                    r8Metadata = gson.fromJson(zis.bufferedReader().readText(), mapType)
                }
                entry.name == DEPENDENCIES_PB_LOCATION -> {
                    appDependencies = AppDependencies.ADAPTER.decode(zis)
                }
                entry.name == PROGUARD_MAP_LOCATION -> {
                    hasMappingFile = true
                }
            }
            entry = zis.nextEntry
        }
    }

    if (VERBOSE) {
        appDependencies?.run {
            // print all contained libraries
            library?.forEach {
                it.maven_library?.run { println("LIB: ${groupId}:${artifactId}:${version}") }
            }
        }
    }

    return BundleInfo(
        path = path,
        profileDexInfo = profileDexInfo,
        dexInfo = dexInfo,
        r8Info = R8Info(hasMappingFile = hasMappingFile, r8Metadata = r8Metadata),
        versionInfo =
            VersionInfo(dotVersionFiles = dotVersionFiles, appDependencies = appDependencies),
    )
}

args.forEach { // TODO: parallelize
    val bundleInfo = bundleInfoFrom(it)
    bundleInfo.reportProblems()
}
