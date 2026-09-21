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

// The redirect-version registry lives in `:buildSrc:public`, not `:private`, so that BUILD SCRIPTS
// can read it. `:private` is deliberately kept off every script's classpath, which is fine for a
// Groovy script (`project.redirectVersions.get(...)` is a dynamic lookup) but not for a Kotlin DSL
// one, where the extension's type has to resolve at script-compile time. The publication
// aggregators are `.kts`, and they are precisely the scripts that need these versions.
package org.jetbrains.androidx.build

import java.io.File
import androidx.build.getSupportRootFolder
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * Loads the artifact-redirection version registry from `redirectversions.toml` (repo root) once per
 * build. The `[versions]` table maps a redirect-coordinate group prefix (e.g. `androidx.compose`) to
 * the `androidx.*` version the redirect points at.
 */
abstract class RedirectVersionsService : BuildService<RedirectVersionsService.Parameters> {
    interface Parameters : BuildServiceParameters {
        var tomlFileName: String
        var tomlFileContents: Provider<String>
    }

    /** Group prefix (e.g. `androidx.compose`) -> redirect version. */
    val versions: Map<String, String> by lazy {
        val parsed = Toml.parse(parameters.tomlFileContents.get())
        if (parsed.hasErrors()) {
            val issues =
                parsed.errors().joinToString("\n") {
                    "${parameters.tomlFileName}:${it.position()}: ${it.message}"
                }
            throw GradleException("${parameters.tomlFileName} has issues.\n$issues")
        }
        val table: TomlTable =
            parsed.getTable("versions")
                ?: throw GradleException("${parameters.tomlFileName} is missing the [versions] table")
        // tomlj treats a dotted String key as a path lookup, so the dotted group keys must be read
        // via the literal single-segment List overload (getString(listOf(key))), not getString(key).
        table.keySet().associateWith { key ->
            table.getString(listOf(key))
                ?: throw GradleException(
                    "${parameters.tomlFileName}: [versions] \"$key\" must be a string",
                )
        }
    }

    companion object {
        private const val TOML_FILE_NAME = "redirectversions.toml"

        /**
         * Public because `:buildSrc:private`'s hierarchical lookup
         * ([findArtifactRedirectionVersion]) resolves the same registry, and it now sits on the
         * other side of a module boundary.
         */
        fun registerOrGet(project: Project): Provider<RedirectVersionsService> {
            val file =
                project.objects.fileProperty()
                    .fileValue(File(project.getSupportRootFolder(), TOML_FILE_NAME))
            val contents = project.providers.fileContents(file).asText
            return project.gradle.sharedServices.registerIfAbsent(
                "redirectVersionsService",
                RedirectVersionsService::class.java,
            ) { spec ->
                spec.parameters.tomlFileName = TOML_FILE_NAME
                spec.parameters.tomlFileContents = contents
            }
        }
    }
}

/**
 * Project extension exposing the `redirectversions.toml` registry to build scripts (Groovy):
 * `project.redirectVersions.get("androidx.navigationevent")`. The key is an **exact** group; a
 * missing key fails fast — a build script asking for a redirect version it never registered is
 * always a bug.
 */
open class RedirectVersions(private val service: Provider<RedirectVersionsService>) {
    /** Exact lookup; throws if [key] is not in `redirectversions.toml`. */
    fun get(key: String): String =
        service.get().versions[key]
            ?: throw GradleException(
                "[artifactRedirection] no redirect version for '$key'. Add it to the [versions] " +
                    "table in redirectversions.toml.",
            )

    /** Exact lookup; null if [key] is not registered. */
    fun findOrNull(key: String): String? = service.get().versions[key]
}

/**
 * Registers the [RedirectVersions] extension (`project.redirectVersions`). Idempotent.
 *
 * Applied automatically by [JetBrainsAndroidXImplPlugin], so an AndroidX module already has it.
 * Public because the publication aggregators do not apply that plugin - they are plain
 * java/shadow projects - yet they are exactly the scripts that need the redirect versions, to know
 * which upstream `androidx.*` release to pull the split-package modules from.
 */
fun Project.registerRedirectVersionsExtension() {
    if (extensions.findByName("redirectVersions") == null) {
        extensions.create(
            "redirectVersions",
            RedirectVersions::class.java,
            RedirectVersionsService.registerOrGet(this),
        )
    }
}
