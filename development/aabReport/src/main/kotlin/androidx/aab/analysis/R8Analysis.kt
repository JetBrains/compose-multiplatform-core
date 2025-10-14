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

package androidx.aab.analysis

import androidx.aab.*
import androidx.aab.analysis.R8Issues.getPrimaryOptimizationIssue
import androidx.aab.cli.OutputContext
import androidx.aab.cli.outputContext
import kotlin.math.roundToInt

data class PackageStats(val packagePrefix: String, var classesSeen: Int, var obfClassesSeen: Int)

/**
 * Tracks stats respecting minification/obfuscation heuristics.
 *
 * Note that throughout aabReport, we only consider dex top level classes (that is classes that in
 * the dex file are not inner classes).
 */
data class MinificationStats(
    val minifiedClassesLowerAccuracy: Double,
    val minifiedClassesLengthAccuracy: Double,
    val minifiedRate: Double,
) {
    companion object {
        fun fromMappingAndDex(
            appOutputDir: OutputContext.AppOutputDir,
            mappingFileInfo: MappingFileInfo?,
            dexInfo: List<DexInfo>,
        ): MinificationStats? {
            if (mappingFileInfo == null) return null

            val classInfo = dexInfo.flatMap { it.classInfo }

            val allClassesInDex = classInfo.map { it.fullName }.toSet()
            val prunedMappingFileInfo =
                mappingFileInfo.dexClassNameToOriginalName.filter { it.key in allClassesInDex }

            var isObfuscatedCount = 0
            var isObfuscatedLowerCaseHits = 0
            var isObfuscatedAppearsMinifiedHits = 0

            val packagePrefixInfo = mutableMapOf<String, PackageStats>()
            classInfo.forEach { clazz ->
                val isObfuscatedAccordingToMappingFile =
                    (prunedMappingFileInfo[clazz.fullName]?.wasRemapped ?: false)

                val packageName =
                    prunedMappingFileInfo[clazz.fullName]?.originalName?.substringBeforeLast(".")
                        ?: clazz.packageName
                val packagePrefix =
                    if (packageName.startsWith("com.google")) {
                        // need more package sections to point at what's not being optimized
                        packageName.split(".").take(4).joinToString(".")
                    } else {
                        packageName.split(".").take(2).joinToString(".")
                    }
                packagePrefixInfo
                    .computeIfAbsent(packagePrefix) { PackageStats(packagePrefix, 0, 0) }
                    .apply {
                        classesSeen += 1
                        if (isObfuscatedAccordingToMappingFile) obfClassesSeen++
                    }

                if (clazz.startsWithLowerCase == isObfuscatedAccordingToMappingFile) {
                    isObfuscatedLowerCaseHits++
                }
                if (clazz.classNameAppearsMinified == isObfuscatedAccordingToMappingFile) {
                    isObfuscatedAppearsMinifiedHits++
                }
                if (isObfuscatedAccordingToMappingFile) {
                    isObfuscatedCount++
                }

                if (isObfuscatedAccordingToMappingFile) {
                    appOutputDir.obfuscatedClasses?.appendText(
                        "${clazz.fullName.padEnd(100)} -> ${prunedMappingFileInfo[clazz.fullName]}\n"
                    )
                } else {
                    appOutputDir.unobfuscatedClasses?.appendText(
                        "${clazz.fullName.padEnd(100)} -> ${prunedMappingFileInfo[clazz.fullName]}\n"
                    )
                }
            }

            outputContext.dumpPackagePrefixInfoToFile(appOutputDir.outputDir, packagePrefixInfo)

            if (isObfuscatedCount > 0.25 * classInfo.count()) {
                // only register to global stats if app looks somewhat obfuscated
                outputContext.registerPackagePrefixInfo(packagePrefixInfo)
            }

            return MinificationStats(
                minifiedClassesLowerAccuracy = isObfuscatedLowerCaseHits * 1.0 / classInfo.size,
                minifiedClassesLengthAccuracy =
                    isObfuscatedAppearsMinifiedHits * 1.0 / classInfo.size,
                minifiedRate = isObfuscatedCount * 1.0 / classInfo.size,
            )
        }
    }
}

data class R8Analysis(
    val mappingPresent: Boolean,
    val compilerMarker: Compiler,
    val compilerJson: Compiler,
    val r8JsonFileExpected: Boolean,
    val r8JsonFileInfo: R8JsonFileInfo?,
    val dexSha256ChecksumsMatching: Set<String>,
    val dexSha256ChecksumsR8JsonOnly: Set<String>,
    val dexSha256ChecksumsDexOnly: Set<String>,
    val minificationStats: MinificationStats?,
) : ScoreReporter {
    fun R8JsonFileInfo.getScore(): Int {
        return (50 *
                ((if (this.shrinkingEnabled) 0.3 else 0.0) +
                    (if (this.optimizationEnabled) 0.5 else 0.0) +
                    (if (this.obfuscationEnabled) 0.2 else 0.0)))
            .roundToInt()
    }

    override fun getSubScore(): SubScore {
        val issues =
            listOfNotNull(
                if (dexSha256ChecksumsR8JsonOnly.isNotEmpty()) R8Issues.DexChecksumsMismatched
                else null,
                if (!mappingPresent && r8JsonFileInfo == null) R8Issues.NoMappingFileOrJsonMetadata
                else null,
                if (r8JsonFileExpected && r8JsonFileInfo == null) R8Issues.MissingR8JsonMetadata
                else null,
                r8JsonFileInfo?.getPrimaryOptimizationIssue(),
            )

        return SubScore(
            label = "R8 / Dex Optimization",
            score = r8JsonFileInfo?.getScore(),
            maxScore = 50,
            issues = issues,
        )
    }

    fun getDexMatchRatio(): Double? {
        return if (
            r8JsonFileInfo != null &&
                (dexSha256ChecksumsR8JsonOnly.isNotEmpty() ||
                    dexSha256ChecksumsMatching.isNotEmpty())
        ) {
            dexSha256ChecksumsMatching.size * 1.0 /
                (dexSha256ChecksumsR8JsonOnly.size + dexSha256ChecksumsMatching.size)
        } else {
            null
        }
    }

    fun csvEntries() =
        listOf(
            (r8JsonFileInfo?.getScore()).toString(),
            compilerMarker.toString(),
            compilerJson.toString(),
            getDexMatchRatio().toString(),
            (minificationStats?.minifiedClassesLowerAccuracy).toString(),
            (minificationStats?.minifiedClassesLengthAccuracy).toString(),
            (minificationStats?.minifiedRate).toString(),
        )

    companion object {
        val CSV_TITLES =
            listOf(
                "r8_score",
                "r8_compilerFromMarker",
                "r8_compilerFromJson",
                "r8_ratio_json_shas_match_dex",
                "r8_minifiedClassesLowerAccuracy",
                "r8_minifiedClassesLengthAccuracy",
                "r8_minifiedRate",
            )

        fun ApkInfo.getR8Analysis(): R8Analysis {
            return R8Analysis(
                mappingPresent = false,
                compilerMarker = Compiler.fromMarkers(dexInfo),
                compilerJson = Compiler.Unknown,
                r8JsonFileExpected = false,
                r8JsonFileInfo = null,
                dexSha256ChecksumsDexOnly = emptySet(),
                dexSha256ChecksumsMatching = emptySet(),
                dexSha256ChecksumsR8JsonOnly = emptySet(),
                minificationStats = null,
            )
        }

        fun BundleInfo.getR8Analysis(): R8Analysis {
            val metadataJsonShas = r8JsonFileInfo?.dexShas?.toSet() ?: emptySet()
            val dexShas = dexInfo.map { it.sha256 }.toSet()

            return R8Analysis(
                mappingPresent = mappingFileInfo != null,
                compilerMarker = Compiler.fromMarkers(dexInfo),
                // technically, should capture all *8.json files, but in comparison to dex markers,
                // unlikely to be Both
                compilerJson = r8JsonFileInfo?.compiler ?: Compiler.Unknown,
                r8JsonFileExpected =
                    (this.appMetadataPropsInfoBundleMetadata ?: this.appMetadataPropsInfoMetaInf)
                        ?.agpAtLeast(8, 8) ?: false,
                r8JsonFileInfo = r8JsonFileInfo,
                dexSha256ChecksumsMatching = metadataJsonShas.intersect(dexShas),
                dexSha256ChecksumsDexOnly = dexShas - metadataJsonShas,
                dexSha256ChecksumsR8JsonOnly = metadataJsonShas - dexShas,
                minificationStats =
                    MinificationStats.fromMappingAndDex(
                        outputContext.outputDirForApp(this.path.substringAfterLast("/")),
                        mappingFileInfo,
                        dexInfo,
                    ),
            )
        }
    }
}
