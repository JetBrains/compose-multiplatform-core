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
     * Libraries whose `libraryversions.toml` entry is NOT the version they publish under.
     *
     * For most of what this fork publishes the catalog entry *is* the publication version:
     * `org.jetbrains.androidx.lifecycle` tracks androidx one-for-one, so catalog `2.11.0` and
     * published `2.11.0` are the same number. Compose is the exception. Its catalog entry is the
     * **artifact-redirection target** — the AOSP `androidx.compose` release the redirects point at,
     * bumped by commits literally titled `artifactRedirection.version.androidx.compose=…` — while
     * `org.jetbrains.compose.*` is a product line of its own that upstream's release tooling
     * supplies through `-Pjetbrains.publication.version.COMPOSE`. The two run close enough together
     * to look interchangeable and are not: with the catalog at `1.12.0-beta01`, the published line
     * was already at `1.13.0-alpha01`.
     *
     * So Compose gets its baseline here instead. **Bump it when the branch rebases onto a newer
     * `jb-main`** — nothing derives it, because there is nothing in the tree to derive it from.
     */
    private val PUBLICATION_BASELINES = mapOf(
        "COMPOSE" to "1.13.0-alpha01",
    )

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
        // The baseline is what this library's published line is on: its catalog entry, unless the
        // catalog entry means something else for it (see PUBLICATION_BASELINES).
        val declared = PUBLICATION_BASELINES[library]
        val baseline = Version(declared ?: catalogValue)
        val baselineSource =
            if (declared != null) "ForkPublicationVersions.PUBLICATION_BASELINES"
            else "libraryversions.toml"
        val base =
            if (override == null) baseline
            else checkedOverride(library, override, baseline, baselineSource)
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

    private fun checkedOverride(
        library: String,
        override: String,
        baseline: Version,
        baselineSource: String,
    ): Version {
        val version = Version(override)
        verifyVersionFormat(version)
        if (version.major != baseline.major || version.minor != baseline.minor) {
            throw IllegalArgumentException(
                "Fork publication version '$override' for $library is outside the branch's " +
                    "${baseline.major}.${baseline.minor} line — $baselineSource says " +
                    "'$baseline'. Move the override onto the current line, or drop it to publish " +
                    "the branch's own version."
            )
        }
        if (version < baseline) {
            throw IllegalArgumentException(
                "Fork publication version '$override' for $library is below the branch's version " +
                    "'$baseline' from $baselineSource. A fork release continues the line, it " +
                    "never precedes it."
            )
        }
        return version
    }
}
