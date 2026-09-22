/*
 * Copyright 2026 The Android Open Source Project
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

package org.jetbrains.androidx.build

import androidx.build.Version
import androidx.build.verifyVersionFormat

/**
 * Resolves the version each library this fork publishes is released under.
 *
 * The default is whatever version the branch is on, straight from `libraryversions.toml`, so a
 * dependency of the Compose fork (lifecycle, savedstate, navigationevent) publishes the version it
 * actually is. A library only carries an override once the fork has released something upstream
 * does not have, and the way to express that is to continue upstream's line — `2.11.0` to `2.11.1`,
 * or `1.12.0-beta01` to `1.12.0-beta02` — which is what the neighbouring JetBrains forks of these
 * artifacts already do. There is no vendor segment: the `org.jetbrains.fleet.*` group is what
 * distinguishes these artifacts from anyone else's.
 *
 * Pure by design — no Gradle types — so the precedence and the guard are unit-testable. The Gradle
 * wiring lives in [forkPublicationVersion].
 */
object ForkPublicationVersions {

    /**
     * Publication library name -> `libraryversions.toml` `[versions]` key.
     *
     * The two registries were named independently and two keys disagree, so this cannot be an
     * identity function. An unmapped library is a hard failure rather than a fallback: the
     * placeholder version this replaced (`9999.0.0-SNAPSHOT`) silently published nonsense.
     */
    private val CATALOG_KEYS = mapOf(
        "COMPOSE" to "COMPOSE",
        "COMPOSE_MATERIAL3" to "COMPOSE_MATERIAL3",
        "COMPOSE_MATERIAL3_ADAPTIVE" to "COMPOSE_MATERIAL3_ADAPTIVE",
        "LIFECYCLE" to "LIFECYCLE",
        "NAVIGATION" to "NAVIGATION",
        "NAVIGATION_3" to "NAVIGATION3",
        "NAVIGATION_EVENT" to "NAVIGATIONEVENT",
        "SAVEDSTATE" to "SAVEDSTATE",
    )

    /**
     * @param library a publication library name from [JetBrainsPublication.libraryToComponents]
     * @param override the value of `-Pjetbrains.publication.version.<library>`, or null
     * @param catalogVersions the `[versions]` table of `libraryversions.toml`
     * @param snapshot whether `-Pjetbrains.publication.snapshot=true` was passed
     */
    fun resolve(
        library: String,
        override: String?,
        catalogVersions: Map<String, String>,
        snapshot: Boolean,
    ): Version {
        val catalogKey = CATALOG_KEYS[library]
            ?: throw IllegalArgumentException(
                "No libraryversions.toml key is mapped for publication library '$library'. " +
                    "Add it to ForkPublicationVersions.CATALOG_KEYS."
            )
        val catalogValue = catalogVersions[catalogKey]
            ?: throw IllegalArgumentException(
                "libraryversions.toml has no [versions] entry '$catalogKey', needed to version " +
                    "publication library '$library'."
            )
        val catalog = Version(catalogValue)
        val base = if (override == null) catalog else checkedOverride(library, override, catalog)
        // AndroidX's own convention: a snapshot replaces the pre-release rather than extending it,
        // which keeps the coordinate stable across pre-release bumps and inside the grammar.
        // preReleaseIteration and buildMetadata are cleared so the copy does not keep a stale
        // `beta01`-derived iteration that `equals` would still see.
        return if (snapshot) {
            base.copy(preRelease = "SNAPSHOT", preReleaseIteration = null, buildMetadata = null)
        } else {
            base
        }
    }

    private fun checkedOverride(library: String, override: String, catalog: Version): Version {
        val version = Version(override)
        verifyVersionFormat(version)
        if (version.major != catalog.major || version.minor != catalog.minor) {
            throw IllegalArgumentException(
                "Fork publication version '$override' for $library is outside the branch's " +
                    "${catalog.major}.${catalog.minor} line — libraryversions.toml says " +
                    "'$catalog'. Move the override onto the current upstream line, or drop it to " +
                    "publish the branch's own version."
            )
        }
        if (version < catalog) {
            throw IllegalArgumentException(
                "Fork publication version '$override' for $library is below the branch's version " +
                    "'$catalog' from libraryversions.toml. A fork release continues upstream's " +
                    "line, it never precedes it."
            )
        }
        return version
    }
}
