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
import org.jetbrains.androidx.build.JetBrainsPublication.isEquivalentForkGroupFor
import org.jetbrains.androidx.build.JetBrainsPublication.projectPathForCoordinates

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
                    appendLine("Problematic fork files:")
                    appendLine(forkFile.invariantSeparatorsPath)
                    appendLine("Run ./gradlew ${project.path}:jbUpdateForkDependencies to update the build file.")
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
        (sourceSetName to originalSourceSetDependencies)
            .takeUnless { originalSourceSetDependencies.isSatisfiedByFork(forkSourceSetDependencies) }
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
                separator = "\n",
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
    DEPENDENCY_LINE.findAll(block).map { match ->
        Dependency.Declaration(match.value.trim())
    }.toList()

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
private val DEPENDENCY_LINE = Regex("""(\w+)\s*\(\s*(.+?)\s*\)""")
private const val FORK_DEPENDENCIES_SUPPRESSION = "jbVerifyForkDependencies: suppress"

private fun parseDependency(declaration: String): ParsedDependency? {
    val match = DEPENDENCY_LINE.matchEntire(declaration) ?: return null
    val type = match.groupValues[1]
    val text = match.groupValues[2]
    val trimmed = text.trim()
    if (trimmed.startsWith("project(")) {
        val path = trimmed.substringAfter("project(").substringBeforeLast(")").trim().trim('"', '\'')
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

private fun Dependency.isSatisfiedByFork(forkDependency: Dependency): Boolean {
    if (this == forkDependency) return true

    val original = parseDependency(declaration) ?: return false
    val fork = parseDependency(forkDependency.declaration) ?: return false
    return original.type == fork.type && when (original) {
        is ParsedDependency.Artifact -> when (fork) {
            is ParsedDependency.Artifact ->
                original.module == fork.module &&
                    isEquivalentForkGroupFor(original.group, fork.group) &&
                    fork.version.isCompatibleWith(original.version)
            is ParsedDependency.Project ->
                projectPathForCoordinates(original.group, original.module) == fork.path
        }
        is ParsedDependency.Project -> fork is ParsedDependency.Project && original.path == fork.path
    }
}

private fun String.isCompatibleWith(originalVersion: String): Boolean {
    if (this == originalVersion) return true

    val forkVersion = Version.parseOrNull(this) ?: return false
    val original = Version.parseOrNull(originalVersion) ?: return false
    return forkVersion >= original
}

private fun List<Dependency>.isSatisfiedByFork(
    forkDependencies: List<Dependency>,
): Boolean = size == forkDependencies.size && zip(forkDependencies).all { (originalDependency, forkDependency) ->
    originalDependency.isSatisfiedByFork(forkDependency)
}

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
