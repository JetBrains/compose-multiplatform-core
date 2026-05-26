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

import java.io.File
import java.net.URI
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.Exec

/**
 * AndroidX artifacts whose Maven artifacts can be replaced with artifacts built from sources.
 *
 * The source projects are not included in this build. Instead, the compatibility stub remains the
 * Gradle project dependency boundary, and its external AndroidX artifact dependency is resolved
 * from a local Maven repository populated by invoking the source build's Gradle wrapper.
 */
object AospSourceArtifacts {
    const val USE_SOURCES_INSTEAD_OF_AOSP_ARTIFACTS_PROPERTY =
        "jetbrains.androidx.use.sources.instead.of.aosp.artifacts"

    private const val KOTLIN_MULTIPLATFORM_PUBLICATION = "KotlinMultiplatform"

    val supportedArtifacts =
        listOf(
            AospSourceArtifact(
                groupId = "androidx.compose.runtime",
                artifactId = "runtime",
                projectPath = ":compose:runtime:runtime",
                sourceProjectPath = ":compose:runtime:runtime",
                sourceVersionKey = "COMPOSE",
            )
        )

    fun isEnabled(project: Project): Boolean =
        project.providers
            .gradleProperty(USE_SOURCES_INSTEAD_OF_AOSP_ARTIFACTS_PROPERTY)
            .map { it.toBoolean() }
            .getOrElse(false)

    fun artifactForModule(group: String?, name: String): AospSourceArtifact? =
        supportedArtifacts.firstOrNull { it.matchesModule(group, name) }

    fun artifactForProjectPath(path: String): AospSourceArtifact? =
        supportedArtifacts.firstOrNull { it.projectPath == path }

    fun publicationForCompileTask(project: Project, taskName: String): String? {
        val lowerName = taskName.lowercase()
        if (!lowerName.contains("compile")) return null

        return when {
            lowerName.contains("metadata") || lowerName.contains("commonmain") ->
                KOTLIN_MULTIPLATFORM_PUBLICATION
            lowerName.contains("iossimulatorarm64") -> "IosSimulatorArm64"
            lowerName.contains("iosarm64") -> "IosArm64"
            lowerName.contains("iosx64") -> "IosX64"
            lowerName.contains("tvosimulatorarm64") -> "TvosSimulatorArm64"
            lowerName.contains("tvosarm64") -> "TvosArm64"
            lowerName.contains("watchossimulatorarm64") -> "WatchosSimulatorArm64"
            lowerName.contains("watchosarm64") -> "WatchosArm64"
            lowerName.contains("watchosarm32") -> "WatchosArm32"
            lowerName.contains("macosarm64") -> "MacosArm64"
            lowerName.contains("macosx64") -> "MacosX64"
            lowerName.contains("linuxarm64") -> "LinuxArm64"
            lowerName.contains("linuxx64") -> "LinuxX64"
            lowerName.contains("mingwx64") -> "MingwX64"
            lowerName.contains("wasmjs") -> "WasmJs"
            lowerName.contains("js") -> "Js"
            lowerName.contains("android") -> "Android"
            lowerName.contains("desktop") || lowerName.contains("jvm") -> "Desktop"
            project.usesAndroidKotlinPlugin() -> "Android"
            lowerName == "compilejava" || lowerName == "compilekotlin" -> "Desktop"
            else -> null
        }
    }

    fun tasksForPublication(artifact: AospSourceArtifact, publication: String): List<String> =
        buildList {
            add(
                "${artifact.sourceProjectPath}:publish${KOTLIN_MULTIPLATFORM_PUBLICATION}" +
                    "PublicationToMavenRepository"
            )
            if (publication != KOTLIN_MULTIPLATFORM_PUBLICATION) {
                add("${artifact.sourceProjectPath}:publish${publication}PublicationToMavenRepository")
            }
        }

    private fun Project.usesAndroidKotlinPlugin(): Boolean =
        plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("com.android.library") ||
            plugins.hasPlugin("com.android.application")
}

data class AospSourceArtifact(
    val groupId: String,
    val artifactId: String,
    val projectPath: String,
    val sourceProjectPath: String,
    val sourceVersionKey: String,
) {
    fun matchesModule(group: String?, name: String): Boolean =
        group == groupId && (name == artifactId || name.startsWith("$artifactId-"))

    fun sourceVersion(project: Project): String {
        val libraryVersions = project.rootProject.projectDir.parentFile.resolve("libraryversions.toml")
        val text = libraryVersions.readText()
        val versionRegex = Regex("""(?m)^\s*${Regex.escape(sourceVersionKey)}\s*=\s*"([^"]+)"""")
        return versionRegex.find(text)?.groupValues?.get(1)
            ?: error("Cannot find $sourceVersionKey in ${libraryVersions.absolutePath}")
    }
}

fun Project.configureAospSourceArtifactReplacement() {
    if (!AospSourceArtifacts.isEnabled(rootProject)) return

    configurations.configureEach { configuration ->
        configuration.resolutionStrategy.eachDependency { details ->
            val artifact =
                AospSourceArtifacts.artifactForModule(
                    details.requested.group,
                    details.requested.name,
                ) ?: return@eachDependency
            details.useVersion(artifact.sourceVersion(this@configureAospSourceArtifactReplacement))
            details.because(
                "${AospSourceArtifacts.USE_SOURCES_INSTEAD_OF_AOSP_ARTIFACTS_PROPERTY} " +
                    "uses artifacts built from local sources"
            )
        }
    }

    registerAospSourceArtifactPublishTasks()
    configureCompileTasksToPublishAospSourceArtifacts()
}

private fun Project.registerAospSourceArtifactPublishTasks() {
    val artifact = AospSourceArtifacts.artifactForProjectPath(path) ?: return
    val supportRoot = rootProject.projectDir.parentFile
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val outDirProvider =
        providers.environmentVariable("OUT_DIR").orElse(
            provider {
                if (extensions.extraProperties.has("outDir")) {
                    (extensions.extraProperties.get("outDir") as File).absolutePath
                } else {
                    supportRoot.resolve("out").absolutePath
                }
            }
        )

    val publications =
        listOf(
            "KotlinMultiplatform",
            "Desktop",
            "Android",
            "Js",
            "WasmJs",
            "IosX64",
            "IosArm64",
            "IosSimulatorArm64",
            "TvosArm64",
            "TvosSimulatorArm64",
            "WatchosArm64",
            "WatchosArm32",
            "WatchosSimulatorArm64",
            "MacosX64",
            "MacosArm64",
            "LinuxX64",
            "LinuxArm64",
            "MingwX64",
        )

    publications.forEach { publication ->
        val taskName = "publish${publication}PublicationToMavenRepo"
        if (tasks.findByName(taskName) != null) return@forEach

        tasks.register(taskName, Exec::class.java) { task ->
            task.group = "publishing"
            task.description =
                "Publishes ${artifact.groupId}:${artifact.artifactId} $publication from " +
                    "the source build into the local Maven repo."
            task.workingDir = supportRoot
            val sourceTasks = AospSourceArtifacts.tasksForPublication(artifact, publication)
            task.doFirst {
                task.environment("OUT_DIR", outDirProvider.get())
                task.commandLine(gradleCommandForSourceBuild(supportRoot, isWindows) + sourceTasks)
            }
        }
    }
}

private fun Project.configureCompileTasksToPublishAospSourceArtifacts() {
    afterEvaluate {
        val usedArtifacts = directlyUsedAospSourceArtifacts().toMutableSet()
        AospSourceArtifacts.artifactForProjectPath(path)?.let { usedArtifacts.add(it) }
        if (usedArtifacts.isEmpty()) return@afterEvaluate

        tasks.configureEach { task ->
            val publication =
                AospSourceArtifacts.publicationForCompileTask(this@configureCompileTasksToPublishAospSourceArtifacts, task.name)
                    ?: return@configureEach
            usedArtifacts.forEach { artifact ->
                task.dependsOn("${artifact.projectPath}:publish${publication}PublicationToMavenRepo")
            }
        }
    }
}

private fun Project.directlyUsedAospSourceArtifacts(): Set<AospSourceArtifact> =
    configurations
        .flatMap { configuration -> configuration.dependencies }
        .filterIsInstance<ExternalModuleDependency>()
        .mapNotNull { dependency ->
            AospSourceArtifacts.artifactForModule(dependency.group, dependency.name)
        }
        .toSet()

private fun Project.gradleCommandForSourceBuild(supportRoot: File, isWindows: Boolean): List<String> {
    val sourceWrapper = supportRoot.resolve(if (isWindows) "gradlew.bat" else "gradlew")
    if (sourceWrapper.isFile && supportRoot.wrapperDistributionIsAvailable()) {
        return gradleWrapperCommand(sourceWrapper, isWindows)
    }

    val currentWrapper = rootProject.projectDir.resolve(if (isWindows) "gradlew.bat" else "gradlew")
    return gradleWrapperCommand(currentWrapper, isWindows) + listOf("-p", supportRoot.absolutePath)
}

private fun gradleWrapperCommand(wrapper: File, isWindows: Boolean): List<String> =
    if (isWindows) {
        listOf("cmd", "/c", wrapper.absolutePath)
    } else {
        listOf(wrapper.absolutePath)
    }

private fun File.wrapperDistributionIsAvailable(): Boolean {
    val wrapperDir = resolve("gradle/wrapper")
    val propertiesFile = wrapperDir.resolve("gradle-wrapper.properties")
    if (!propertiesFile.isFile) return true

    val properties = Properties()
    propertiesFile.inputStream().use { properties.load(it) }
    val distributionUrl = properties.getProperty("distributionUrl") ?: return true
    val normalizedUrl = distributionUrl.replace("\\:", ":")

    return when {
        normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://") -> true
        normalizedUrl.startsWith("file:") -> File(URI(normalizedUrl)).isFile
        else -> wrapperDir.resolve(normalizedUrl).canonicalFile.isFile
    }
}

private fun File.resolve(path: String): File = File(this, path)
