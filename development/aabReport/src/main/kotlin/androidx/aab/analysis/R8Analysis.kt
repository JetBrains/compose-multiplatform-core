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

import androidx.aab.BundleInfo
import androidx.aab.R8JsonFileInfo
import androidx.aab.analysis.R8Issues.getPrimaryOptimizationIssue
import kotlin.math.roundToInt

data class R8Analysis(
    val mappingPresent: Boolean,
    val markerUsingD8Dex: Boolean,
    val markerUsingR8Dex: Boolean,
    val r8JsonFileExpected: Boolean,
    val r8JsonFileInfo: R8JsonFileInfo?,
    val dexSha256ChecksumsMatching: Set<String>,
    val dexSha256ChecksumsR8JsonOnly: Set<String>,
    val dexSha256ChecksumsDexOnly: Set<String>,
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

    fun csvEntries(): List<String> {
        return listOf(
            (r8JsonFileInfo?.getScore()).toString(),
            markerUsingD8Dex.toString(),
            markerUsingR8Dex.toString(),
            getDexMatchRatio().toString(),
        )
    }

    companion object {
        val CSV_TITLES =
            listOf("r8_score", "r8_marker_d8dex", "r8_marker_r8dex", "r8_ratio_json_shas_match_dex")

        fun BundleInfo.getR8Analysis(): R8Analysis {
            val metadataJsonShas = r8JsonFileInfo?.dexShas?.toSet() ?: emptySet()
            val dexShas = dexInfo.map { it.sha256 }.toSet()
            return R8Analysis(
                mappingPresent = mappingFileInfo != null,
                markerUsingD8Dex =
                    dexInfo.any { dex ->
                        dex.r8Markers.any { it.compiler == "D8" && it.map["backend"] == "dex" }
                    },
                markerUsingR8Dex =
                    dexInfo.any { dex ->
                        dex.r8Markers.any { it.compiler == "R8" && it.map["backend"] == "dex" }
                    },
                r8JsonFileExpected =
                    (this.appMetadataPropsInfoBundleMetadata ?: this.appMetadataPropsInfoMetaInf)
                        ?.agpAtLeast(8, 8) ?: false,
                r8JsonFileInfo = r8JsonFileInfo,
                dexSha256ChecksumsMatching = metadataJsonShas.intersect(dexShas),
                dexSha256ChecksumsDexOnly = dexShas - metadataJsonShas,
                dexSha256ChecksumsR8JsonOnly = metadataJsonShas - dexShas,
            )
        }
    }
}
