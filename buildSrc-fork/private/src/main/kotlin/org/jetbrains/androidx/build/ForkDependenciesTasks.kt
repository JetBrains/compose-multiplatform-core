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
import org.jetbrains.androidx.build.util.ParsedBuildScript
import org.jetbrains.androidx.build.util.ParsedBuildScript.Dependency
import org.jetbrains.androidx.build.util.ParsedBuildScript.Line

@CacheableTask
internal abstract class VerifyForkDependenciesTask : DefaultTask() {
    init {
        group = "verification"
        description = "Verifies commonMain dependencies in build-fork.gradle files, " +
            "to be aligned with build.gradle dependencies."
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
        get() = project.layout.buildDirectory.file("fork-dependencies-verification.marker")
            .get().asFile

    @TaskAction
    fun verify() {
        val originalText = buildFile.readText()
        val forkText = forkFile.readText()
        val expectedForkText = updatedForkText(originalText, forkText)
        if (expectedForkText != forkText) {
            throw GradleException(
                buildString {
                    appendLine("Fork dependencies are out of date.")
                    appendLine("Run ./gradlew jbUpdateForkDependencies to update them.")
                    appendLine()
                    appendLine("Diff:")
                    append(textDiff(forkText, expectedForkText))
                }.trimEnd()
            )
        }
    }
}

fun textDiff(one: String, two: String): String {
    val oneLines = one.lines().toSet()
    val twoLines = two.lines().toSet()
    val diffLines = oneLines.filter { it !in twoLines }.map { "-$it" } +
        twoLines.filter { it !in oneLines }.map { "+$it" }
    return diffLines.joinToString("\n")
}

@DisableCachingByDefault(because = "Updates source files.")
internal abstract class UpdateForkDependenciesTask : DefaultTask() {
    init {
        group = "verification"
        description = "Updates commonMain dependencies in build-fork.gradle files," +
            "to align them with build.gradle dependencies."
        onlyIf { forkFile.exists() }
        outputs.upToDateWhen { false }
    }

    private val buildFile: File get() = project.scriptFile("build")
    private val forkFile: File get() = project.scriptFile("build-fork")

    @TaskAction
    fun update() {
        val originalText = buildFile.readText()
        val forkText = forkFile.readText()
        val expectedForkText = updatedForkText(originalText, forkText)
        if (expectedForkText != forkText) {
            forkFile.writeText(expectedForkText)
        }
    }
}

private fun Project.scriptFile(name: String): File =
    layout.projectDirectory.file("$name.gradle.kts").asFile.takeIf(File::exists)
        ?: layout.projectDirectory.file("$name.gradle").asFile

internal fun Project.configureForkDependenciesTasks() {
    tasks.register("jbVerifyForkDependencies", VerifyForkDependenciesTask::class.java)
    tasks.register("jbUpdateForkDependencies", UpdateForkDependenciesTask::class.java)
}

internal fun updatedForkText(
    originalText: String,
    forkText: String,
): String {
    val original = ParsedBuildScript(originalText)
    val fork = ParsedBuildScript(forkText)
    return fork.withSourceSets { forkedSourceSet ->
        val originalSourceSet = original.sourceSetOf(forkedSourceSet.name)
        if (originalSourceSet == null || forkedSourceSet.hasMarker(SUPPRESS_VERIFICATION_MARKER)) {
            forkedSourceSet.lines
        } else {
            updatedForkLines(originalSourceSet.lines, forkedSourceSet.lines)
        }
    }.text
}

private fun updatedForkLines(originalLines: List<Line>, forkLines: List<Line>): List<Line> {
    fun modifiedOriginalLine(originalLine: Line): Line? {
        if (originalLine !is Line.Dependency) return originalLine

        val forkLine = forkLines.filterIsInstance<Line.Dependency>().firstOrNull {
            it.hasSameArtifactAs(originalLine) || it.hasSameAssociatedProjectAs(originalLine)
        }

        return when {
            forkLine == null ->
                originalLine

            forkLine.hasMarker(SUPPRESS_VERIFICATION_MARKER) ->
                null

            forkLine.hasSameArtifactAs(originalLine) ->
                originalLine.withMaxVersionFrom(forkLine)

            forkLine.hasSameAssociatedProjectAs(originalLine) ->
                originalLine.copy(
                    comments = forkLine.comments,
                    inlineComment = forkLine.inlineComment,
                    dependency = forkLine.dependency
                )

            else -> error("Should not happen")
        }
    }

    fun modifiedForkLine(forkLine: Line): Line? = when {
        forkLine !is Line.Dependency -> forkLine
        forkLine.hasMarker(SUPPRESS_VERIFICATION_MARKER) -> forkLine
        else -> null
    }

    return originalLines.mapNotNull(::modifiedOriginalLine)
        .plus(Line.Blank)
        .plus(forkLines.mapNotNull(::modifiedForkLine))
        .collapseBlankLines()
}

private const val SUPPRESS_VERIFICATION_MARKER = "jbVerifyForkDependencies: suppress"

private fun List<Line>.collapseBlankLines() = this
    .dropWhile { it is Line.Blank }
    .collapseInnerBlankLines()
    .dropLastWhile { it is Line.Blank }

private fun List<Line>.collapseInnerBlankLines() =
    filterIndexed { index, line -> line !is Line.Blank || getOrNull(index - 1) !is Line.Blank }

private fun Line.Dependency.withMaxVersionFrom(other: Line.Dependency): Line.Dependency {
    val dependency = dependency as Dependency.Artifact
    val otherDependency = other.dependency as Dependency.Artifact
    return copy(dependency = dependency.withMaxVersionFrom(otherDependency))
}

private fun Dependency.Artifact.withMaxVersionFrom(
    forked: Dependency.Artifact
): Dependency.Artifact {
    val originalVersion = Version.parseOrNull(version)
    val forkedVersion = Version.parseOrNull(forked.version)

    return if (
        originalVersion != null &&
        forkedVersion != null &&
        forkedVersion > originalVersion
    ) {
        copy(version = forked.version)
    } else {
        this
    }
}

private fun Line.Dependency.hasSameArtifactAs(other: Line.Dependency): Boolean {
    if (dependency !is Dependency.Artifact) return false
    if (other.dependency !is Dependency.Artifact) return false
    return dependency.group == other.dependency.group &&
        dependency.module == other.dependency.module
}

private fun Line.Dependency.hasSameAssociatedProjectAs(other: Line.Dependency) =
    dependency.associatedProjectPath != null &&
        dependency.associatedProjectPath == other.dependency.associatedProjectPath

private val Dependency.associatedProjectPath
    get() = when (this) {
        is Dependency.Artifact -> projectPathForCoordinates(group, module)
        is Dependency.Project -> path
        is Dependency.LibsReference -> null
    }
