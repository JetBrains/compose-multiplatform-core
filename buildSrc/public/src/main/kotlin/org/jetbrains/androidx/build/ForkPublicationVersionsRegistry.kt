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

// Lives in `:buildSrc:public`, not `:private`, so that BUILD SCRIPTS can call it. The four
// `-all-desktop` publication aggregators are `.kts`, where an extension's type has to resolve at
// script-compile time, and they are precisely the scripts that need a publication version. Same
// reasoning as RedirectVersionsRegistry in this package.
package org.jetbrains.androidx.build

import androidx.build.getSupportRootFolder
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * Loads the `[versions]` table of `libraryversions.toml` (repo root) once per build: the version
 * each library is on for this branch.
 */
abstract class CatalogVersionsService : BuildService<CatalogVersionsService.Parameters> {
    interface Parameters : BuildServiceParameters {
        var tomlFileName: String
        var tomlFileContents: Provider<String>
    }

    /** Catalog key (e.g. `LIFECYCLE`) -> version. */
    val versions: Map<String, String> by lazy {
        val parsed = Toml.parse(parameters.tomlFileContents.get())
        if (parsed.hasErrors()) {
            val issues = parsed.errors().joinToString("\n") {
                "${parameters.tomlFileName}:${it.position()}: ${it.message}"
            }
            throw GradleException("${parameters.tomlFileName} has issues.\n$issues")
        }
        val table: TomlTable = parsed.getTable("versions")
            ?: throw GradleException(
                "${parameters.tomlFileName} is missing the [versions] table"
            )
        table.keySet().associateWith { key ->
            table.getString(listOf(key))
                ?: throw GradleException(
                    "${parameters.tomlFileName}: [versions] \"$key\" must be a string",
                )
        }
    }

    companion object {
        private const val TOML_FILE_NAME = "libraryversions.toml"

        fun registerOrGet(project: Project): Provider<CatalogVersionsService> {
            val file = project.objects.fileProperty()
                .fileValue(File(project.getSupportRootFolder(), TOML_FILE_NAME))
            val contents = project.providers.fileContents(file).asText
            return project.gradle.sharedServices.registerIfAbsent(
                "catalogVersionsService",
                CatalogVersionsService::class.java,
            ) { spec ->
                spec.parameters.tomlFileName = TOML_FILE_NAME
                spec.parameters.tomlFileContents = contents
            }
        }
    }
}

/** Property that makes a publish produce snapshots. Passed by `fleet/publishToMavenLocal.sh`. */
const val FORK_SNAPSHOT_PROPERTY = "jetbrains.publication.snapshot"

/**
 * The version [library] is published under by this fork.
 *
 * Overrides arrive through [JetBrainsVersionsService] rather than a direct `findProperty` read:
 * its [JetBrainsVersions] init block scans *every* `jetbrains.publication.version.*` property and
 * fails on one naming an unregistered library, which is what catches a typo in a `-P` flag.
 * Reading this library's property directly would let a misspelled flag be ignored in silence.
 *
 * Self-registering, because the publication aggregators are plain java/shadow projects that never
 * apply [JetBrainsAndroidXImplPlugin] — yet they need exactly this value.
 */
fun Project.forkPublicationVersion(library: String): String {
    val override = JetBrainsVersionsService.versions(this).libraryToVersion[library]
    val snapshot = (findProperty(FORK_SNAPSHOT_PROPERTY) as? String).toBoolean()
    val catalog = CatalogVersionsService.registerOrGet(this).get().versions
    return ForkPublicationVersions.resolve(library, override, catalog, snapshot).toString()
}
