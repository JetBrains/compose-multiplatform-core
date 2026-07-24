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
import com.intellij.ide.plugins.ActionDescriptorName
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.androidx.build.JetBrainsPublication.projectPathForCoordinates
import org.jetbrains.androidx.build.JetBrainsPublication.shouldPublish
import org.jetbrains.androidx.build.JetBrainsPublication.toAndroidXGroup
import org.jetbrains.androidx.build.JetBrainsPublication.toForkGroup

@CacheableTask
internal abstract class VerifyForkDependenciesTask : DefaultTask() {
    init {
        group = "verification"
        description = "Verifies fork dependency declarations for this project."
        onlyIf { forkFile.exists() }
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val buildFile: File get() = project.scriptFile("build")

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val forkFile: File get() = project.scriptFile("build-fork")

    // needed for CacheableTask to work properly
    @get:OutputFile
    val verificationMarker: File
        get() = project.layout.buildDirectory.file("fork-dependencies-verification.marker").get().asFile

    @TaskAction
    fun verify() {
        if (problematicDependencies(buildFile.readText(), forkFile.readText()).isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Fork dependencies are out of date.")
                    appendLine("Run ./gradlew ${project.path}:jbUpdateForkDependencies to update them.")
                }.trimEnd()
            )
        }
    }
}

@DisableCachingByDefault(because = "Updates source files.")
internal abstract class UpdateForkDependenciesTask : DefaultTask() {
    init {
        group = "verification"
        description = "Adds fork dependency verification suppressions for this project."
        onlyIf { forkFile.exists() }
        outputs.upToDateWhen { false }
    }

    private val buildFile: File get() = project.scriptFile("build")
    private val forkFile: File get() = project.scriptFile("build-fork")

    @TaskAction
    fun update() {
        val forkText = forkFile.readText()
        val dependencies = problematicDependencies(buildFile.readText(), forkText)
        if (dependencies.isNotEmpty()) {
            forkFile.writeText(forkText.withUpdatedForkDependencies(dependencies))
        }
    }
}

internal fun Project.configureForkDependenciesTasks() {
    val verifyTask = tasks.register("jbVerifyForkDependencies", VerifyForkDependenciesTask::class.java)
    tasks.register("jbUpdateForkDependencies", UpdateForkDependenciesTask::class.java)
    tasks.configureEach { task ->
        if (task.name.startsWith("compile")) {
            task.dependsOn(verifyTask)
        }
    }
}

private fun problematicDependencies(
    buildScript: String,
    forkScript: String,
): Map<String, List<Dependency>> {
    val originalDependencies = declaredDependencies(buildScript)
    val forkDependencies = declaredDependencies(forkScript)
    return originalDependencies.mapNotNull { (sourceSetName, originalSourceSetDependencies) ->
        val forkSourceSetDependencies = forkDependencies[sourceSetName] ?: return@mapNotNull null
        val expectedDependencies = originalSourceSetDependencies.updatedForFork(forkSourceSetDependencies)
        (sourceSetName to expectedDependencies)
            .takeUnless { expectedDependencies == forkSourceSetDependencies }
    }.toMap()
}

private fun String.withUpdatedForkDependencies(dependencies: Map<String, List<Dependency>>): String {
    val sourceSetsBlock = extractBlock(this, "sourceSets {") ?: return this
    val offset = indexOf(sourceSetsBlock)
    val replacements = MAIN_SOURCE_SET_REFERENCE.findAll(sourceSetsBlock)
        .mapNotNull { match ->
            val declarations = dependencies[match.groupValues[1]] ?: return@mapNotNull null
            val sourceSetBlock = extractBlock(sourceSetsBlock, match.value).orEmpty()
            val dependenciesBlock = if (".dependencies" in match.value) {
                sourceSetBlock
            } else {
                extractBlock(sourceSetBlock, "dependencies {").orEmpty()
            }
            val blockOffset = sourceSetsBlock.indexOf(dependenciesBlock, match.range.last + 1)
            val contentStart = offset + blockOffset
            val contentEnd = contentStart + dependenciesBlock.length
            val indentation = dependenciesBlock.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.takeWhile(Char::isWhitespace)
                ?: ""
            contentStart to contentEnd to declarations.joinToString(
                separator = "\n$indentation",
                prefix = "\n$indentation",
                postfix = "\n${dependenciesBlock.substringAfterLast('\n')}",
            )
        }
        .toList()
    return replacements.asReversed().fold(this) { script, (range, declarations) ->
        script.substring(0, range.first) + declarations + script.substring(range.second)
    }
}

private fun declaredDependencies(script: String): Map<String, List<Dependency>> {
    val sourceSetsBlock = extractBlock(script, "sourceSets {") ?: return emptyMap()
    return MAIN_SOURCE_SET_REFERENCE.findAll(sourceSetsBlock).mapNotNull { match ->
        val precedingLine = sourceSetsBlock.substring(0, match.range.first).trimEnd().substringAfterLast('\n')
        if (precedingLine.trim() == "// $FORK_DEPENDENCIES_SUPPRESSION") {
            return@mapNotNull null
        }
        val sourceSetName = match.groupValues[1]
        val sourceSetBlock = extractBlock(sourceSetsBlock, match.value).orEmpty()
        val dependenciesBlock = if (".dependencies" in match.value) {
            sourceSetBlock
        } else {
            extractBlock(sourceSetBlock, "dependencies {").orEmpty()
        }
        sourceSetName to parseDependencies(dependenciesBlock)
    }.toMap()
}

private fun Project.scriptFile(name: String): File =
    layout.projectDirectory.file("$name.gradle.kts").asFile.takeIf(File::exists)
        ?: layout.projectDirectory.file("$name.gradle").asFile

private fun parseDependencies(block: String): List<Dependency> =
    block.lineSequence().mapNotNull { line ->
        line.trim().takeIf { declaration -> dependencyCall(declaration) != null }
    }.map(Dependency::Declaration).toList()

private fun extractBlock(text: String, marker: String): String? {
    val start = text.indexOf(marker)
    if (start < 0) return null

    var depth = 1
    var index = start + marker.length
    while (index < text.length) {
        when (text[index]) {
            '{' -> depth++
            '}' -> depth--
        }
        if (depth == 0) return text.substring(start + marker.length, index)
        index++
    }
    return null
}

private val MAIN_SOURCE_SET_REFERENCE = Regex("""(?:val\s+)?(\w+Main)(?:\.dependencies|\s+by\s+\w+)?\s*\{""")
private const val FORK_DEPENDENCIES_SUPPRESSION = "jbVerifyForkDependencies: suppress"

private fun parseDependency(declaration: String): ParsedDependency? {
    val (type, text) = dependencyCall(declaration) ?: return null
    val trimmed = text.trim()
    if (trimmed.startsWith("project(")) {
        val (_, projectPath) = dependencyCall(trimmed) ?: return null
        val path = projectPath.trim().trim('"', '\'')
        return ParsedDependency.Project(type, path)
    }

    val notation = trimmed.trim('"', '\'')
    val (group, module, version) = notation.split(':').takeIf { it.size == 3 } ?: return null

    return ParsedDependency.Artifact(
        type = type,
        group = group,
        module = module,
        version = version,
    )
}

private fun List<Dependency>.updatedForFork(
    forkDependencies: List<Dependency>,
): List<Dependency> {
    val forkArtifacts = forkDependencies.mapNotNull { dependency ->
        (parseDependency(dependency.declaration) as? ParsedDependency.Artifact)
            ?.key()
            ?.let { it to dependency }
    }.toMap()
    val forkProjects = forkDependencies.mapNotNull { dependency ->
        (parseDependency(dependency.declaration) as? ParsedDependency.Project)
            ?.let { it.path to dependency }
    }.toMap()

    return map { originalDependency ->
        val original = parseDependency(originalDependency.declaration) ?: return@map originalDependency
        when (original) {
            is ParsedDependency.Project -> forkProjects[original.path] ?: originalDependency
            is ParsedDependency.Artifact -> {
                val projectPath = projectPathForCoordinates(original.group, original.module)
                forkProjects[projectPath]
                    ?: forkArtifacts[original.key()]?.takeIf { candidate ->
                        candidate.isVersionAtLeast(original)
                    }
                    ?: original.asForkDependency()
            }
        }
    }
}

private fun ParsedDependency.Artifact.key(): ArtifactKey? =
    toAndroidXGroup(group)?.let { ArtifactKey(it, module) }

private fun Dependency.isVersionAtLeast(original: ParsedDependency.Artifact): Boolean {
    val candidate = parseDependency(declaration) as? ParsedDependency.Artifact ?: return false
    if (candidate.type != original.type) return false
    val candidateVersion = Version.parseOrNull(candidate.version) ?: return false
    val originalForkVersion = Version.parseOrNull(original.forkVersion()) ?: return false
    return candidateVersion >= originalForkVersion
}

private fun ParsedDependency.Artifact.asForkDependency(): Dependency {
    val forkGroup = projectPathForCoordinates(group, module)
        ?.takeIf(JetBrainsPublication::shouldPublish)
        ?.let { toForkGroup(group) ?: JetBrainsPublication.mavenGroupFor(it) }
        ?: group
    val version = if (forkGroup != group) forkVersion() else version
    return Dependency.Declaration("$type(\"$forkGroup:$module:$version\")")
}

private fun ParsedDependency.Artifact.forkVersion(): String =
    Version.parseOrNull(version)?.copy(patch = 0)?.toString() ?: version

private fun dependencyCall(declaration: String): Pair<String, String>? {
    val trimmed = declaration.trim()
    val openParenthesis = trimmed.indexOf('(')
    if (openParenthesis <= 0 || !trimmed.substring(0, openParenthesis).trim().matches(Regex("\\w+"))) {
        return null
    }

    var depth = 0
    var quote: Char? = null
    for (index in openParenthesis until trimmed.length) {
        val char = trimmed[index]
        if (quote != null) {
            if (char == quote && trimmed.getOrNull(index - 1) != '\\') quote = null
            continue
        }
        if (char == '\'' || char == '"') {
            quote = char
        } else if (char == '(') {
            depth++
        } else if (char == ')') {
            depth--
            if (depth == 0) {
                if (trimmed.substring(index + 1).isNotBlank()) return null
                return trimmed.substring(0, openParenthesis).trim() to
                    trimmed.substring(openParenthesis + 1, index)
            }
        }
    }
    return null
}

private data class ArtifactKey(val group: String, val module: String)

private sealed interface Dependency {
    val declaration: String

    data class Declaration(
        override val declaration: String,
    ) : Dependency {
        override fun toString(): String = declaration
    }
}

private sealed interface ParsedDependency {
    val type: String

    data class Artifact(
        override val type: String,
        val group: String,
        val module: String,
        val version: String,
    ) : ParsedDependency

    data class Project(
        override val type: String,
        val path: String,
    ) : ParsedDependency
}
