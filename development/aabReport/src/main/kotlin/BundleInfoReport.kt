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

fun BundleInfo.reportProblems() {
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
    if (r8MetadataFileInfo == null) {
        println(
            """
                NOTE - MISSING R8 METADATA
                    No r8 metadata is present in at ${BundlePaths.R8_METADATA_LOCATION}
                    IMPACT:
                          This tool will not be able to report high level optimization quality
                          metrics, or identify if important optimizations are missing.
                    SUGGESTION:
                          Ensure your app is using a sufficiently recent version of AGP (8.8+), or
                          if you're using a separate build system, manually extract this from R8,
                          for example with R8Command.Builder.setBuildMetadataConsumer(), and place
                          this in the app bundle at ${BundlePaths.R8_METADATA_LOCATION}.
                """
                .trimIndent()
        )
    }
    if (
        r8MetadataFileInfo != null &&
            r8MetadataFileInfo.dexShas.toSet() != dexInfo.map { it.sha256 }.toSet()
    ) {
        println(
            """
                NOTE - R8 DEX CHECKSUMS DO NOT MATCH DEX FILES
                    R8 metadata dex shas present in at ${BundlePaths.R8_METADATA_LOCATION} do not match dex files
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
    if (mappingFileInfo == null && r8MetadataFileInfo == null) {
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
        r8MetadataFileInfo != null &&
            (!r8MetadataFileInfo.shrinkingEnabled ||
                !r8MetadataFileInfo.obfuscationEnabled ||
                !r8MetadataFileInfo.optimizationEnabled)
    ) {
        println(
            """
                WARNING - R8 - PRIMARY OPTIMIZATION
                    Application missing one of the following primary optimization flags from R8:
                      shrinking enabled    = ${r8MetadataFileInfo.shrinkingEnabled}
                      obfuscation enabled  = ${r8MetadataFileInfo.obfuscationEnabled}
                      optimization enabled = ${r8MetadataFileInfo.optimizationEnabled}
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

    if (dotVersionFiles.isEmpty() && appBundleDependencies == null) {
        println(
            """
                NOTE - MISSING VERSION INFO
                    No library version metadata present in either location:
                      legacy = .../META-INF/*.version
                      standard = ${BundlePaths.DEPENDENCIES_PB_LOCATION}
                    IMPACT:
                          This tool will skip several verifications, and library developers will not
                          be able to notify this app of high impact security/crash issues via e.g.
                          Play SDK Console.
                    SUGGESTION:
                          Either ensure this app is using a sufficiently recent version of
                          AGP (TODO: VERSION), or if using a separate build system, ensure that your
                          build embeds library information into ${BundlePaths.DEPENDENCIES_PB_LOCATION}.
                """
                .trimIndent()
        )
    }

    // TODO: compose version
    // TODO: don't embed compose tooling (androidx.compose.ui:ui-tooling)
}
