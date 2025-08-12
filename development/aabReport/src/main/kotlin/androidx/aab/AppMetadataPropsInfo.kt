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

/**
 * Bundle information captured from `app-metadata.properties` (in either BUNDLE-METADATA/ or META-INF/)
 *
 * This primarily lists AGP version
 */
data class AppMetadataPropsInfo(
    val appMetadataVersion: String,
    val androidGradlePluginVersion: String,
) {
    fun agpAtLeast(targetMajor: Int, targetMinor: Int): Boolean {
        val components = androidGradlePluginVersion.split(".")
        val currentMajor = components[0].toInt()
        val currentMinor = components[1].toInt()
        return currentMajor > targetMajor ||
            (currentMajor == targetMajor && currentMinor >= targetMinor)
    }

    companion object {
        const val BUNDLE_LOCATION_META_INF =
            "base/root/META-INF/com/android/build/gradle/app-metadata.properties"
        const val BUNDLE_LOCATION_METADATA =
            "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"

        fun from(src: InputStream): AppMetadataPropsInfo {
            var appMetadataVersion = ""
            var androidGradlePluginVersion = ""
            src.bufferedReader().readText().lines().forEach {
                val entries = it.trim().split("=")
                if (entries.size == 2) {
                    if (entries[0] == "appMetadataVersion") {
                        appMetadataVersion = entries[1]
                    } else if (entries[0] == "androidGradlePluginVersion") {
                        androidGradlePluginVersion = entries[1]
                    }
                }
            }
            return AppMetadataPropsInfo(
                appMetadataVersion = appMetadataVersion,
                androidGradlePluginVersion = androidGradlePluginVersion,
            )
        }

        val CSV_TITLES_META_INF =
            listOfNotNull(
                "appMetadataPropsLegacy_agpVersion",
                if (VERBOSE) "appMetadataPropsLegacy_version" else null,
            )
        val CSV_TITLES_BUNDLE =
            listOfNotNull(
                "appMetadataProps_agpVersion",
                if (VERBOSE) "appMetadataProps_version" else null,
            )

        fun AppMetadataPropsInfo?.csvEntries(): List<String> {
            return listOf(this?.androidGradlePluginVersion.toString()) +
                if (VERBOSE) {
                    listOf(this?.appMetadataVersion.toString())
                } else {
                    emptyList()
                }
        }
    }
}
