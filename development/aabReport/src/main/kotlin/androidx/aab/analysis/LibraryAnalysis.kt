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

import androidx.aab.ApkInfo
import androidx.aab.BundleInfo

data class LibraryAnalysis(val hasDotVersionFiles: Boolean, val hasAppBundleDependencies: Boolean) :
    ScoreReporter {
    // TODO: compose version
    // TODO: don't embed compose tooling, perfetto tracing binary
    override fun getSubScore(): SubScore {
        return SubScore(
            label = "Library Version Analysis",
            score = 0,
            maxScore = 0,
            issues =
                if (!hasDotVersionFiles && !hasAppBundleDependencies) {
                    listOf(LibraryVersionIssues.MissingMetadata)
                } else {
                    emptyList()
                },
        )
    }

    fun csvEntries() = listOf(hasDotVersionFiles.toString(), hasAppBundleDependencies.toString())

    companion object {
        val CSV_TITLES = listOf("libraries_hasDot", "libraries_hasProto")

        fun ApkInfo.getLibraryAnalysis(): LibraryAnalysis {
            return LibraryAnalysis(
                hasDotVersionFiles = dotVersionFiles.isNotEmpty(),
                hasAppBundleDependencies = false,
            )
        }

        fun BundleInfo.getLibraryAnalysis(): LibraryAnalysis {
            return LibraryAnalysis(
                hasDotVersionFiles = dotVersionFiles.isNotEmpty(),
                hasAppBundleDependencies = appBundleDependencies != null,
            )
        }
    }
}
