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

import groovy.json.JsonSlurper
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

private const val USE_AOSP_PROJECT_SOURCES = "useAOSPProjectSources"

private val aospSourceRoots =
    mapOf(
        AospCoordinate("androidx.compose.runtime", "runtime") to ":compose:runtime:runtime",
    )

private val aospTargetCompileTasks =
    mapOf(
        "compileKotlinDesktop" to "Desktop",
        "compileKotlinJs" to "Js",
        "compileKotlinWasmJs" to "WasmJs",
        "compileKotlinIosArm64" to "IosArm64",
        "compileKotlinIosSimulatorArm64" to "IosSimulatorArm64",
    )

private val metadataResolvingTaskNames =
    setOf(
        "compileKotlinMetadata",
        "compileCommonMainKotlinMetadata",
        "compileJvmAndAndroidMainKotlinMetadata",
        "compileNonAndroidMainKotlinMetadata",
        "compileNonJvmMainKotlinMetadata",
        "compileWebMainKotlinMetadata",
        "compileNativeMainKotlinMetadata",
        "compileIosMainKotlinMetadata",
        "kmpPartiallyResolvedDependenciesChecker",
    )

fun Project.configureAOSPProjectSources() {
    if (!providers.gradleProperty(USE_AOSP_PROJECT_SOURCES).map(String::toBoolean).getOrElse(false)) {
        return
    }

    val aospVersions = AospVersionCatalog(aospProjectDirectory().resolve("libraryversions.toml"))
    val rootVersionByCoordinate =
        aospSourceRoots.keys.associateWith { coordinate ->
            aospVersions.versionForGroup(coordinate.group)
                ?: throw GradleException("Cannot find AOSP version for ${coordinate.group}")
        }

    allprojects { subproject ->
        subproject.configurations.configureEach { configuration ->
            configuration.resolutionStrategy.dependencySubstitution { substitutions ->
                rootVersionByCoordinate.forEach { (coordinate, version) ->
                    substitutions
                        .substitute(substitutions.module(coordinate.notation))
                        .using(substitutions.module("${coordinate.notation}:$version"))
                        .because("use AOSP project sources")
                }
            }
        }
    }

    val prepareTask =
        tasks.register("prepareAOSPComposeRuntime", PrepareAOSPProjectSourcesTask::class.java) {
            it.rootCoordinatesToProjectPaths = aospSourceRoots.mapKeys { entry -> entry.key.notation }
            it.configureAospLocations(project)
        }

    gradle.taskGraph.whenReady { graph ->
        val requestedTargetNames =
            graph.allTasks
                .mapNotNull { task -> aospTargetCompileTasks[task.name] }
                .toCollection(linkedSetOf())
        prepareTask.configure {
            it.targetNames = requestedTargetNames
        }
    }

    val sourceRootProjects =
        aospSourceRoots.values.mapNotNull { path ->
            findProject(path)
        }

    sourceRootProjects.forEach { sourceRootProject ->
        sourceRootProject.tasks.configureEach { task ->
            if (
                task.name in metadataResolvingTaskNames ||
                    task.name.endsWith("DependenciesMetadata") ||
                    task.name in aospTargetCompileTasks.keys
            ) {
                task.dependsOn(prepareTask)
            }
        }
    }
}

@DisableCachingByDefault(because = "Runs a nested AOSP Gradle build and inspects its Maven output")
abstract class PrepareAOSPProjectSourcesTask : DefaultTask() {
    @get:Input
    var rootCoordinatesToProjectPaths: Map<String, String> = emptyMap()

    @get:Input
    var targetNames: Set<String> = emptySet()

    @get:Internal
    abstract val aospProjectDirectory: DirectoryProperty

    @get:Internal
    abstract val aospOutDirectory: DirectoryProperty

    @get:Internal
    abstract val aospRepositoryDirectory: DirectoryProperty

    @get:Internal
    abstract val forkProjectDirectory: DirectoryProperty

    @get:Input
    abstract val parentGradleCommand: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun prepare() {
        val roots =
            rootCoordinatesToProjectPaths.mapKeys { (notation, _) ->
                AospCoordinate.parse(notation)
            }
        val context =
            AospPrepareContext(
                aospProjectDir = aospProjectDirectory.get().asFile,
                aospOutDir = aospOutDirectory.get().asFile,
                aospRepository = aospRepositoryDirectory.get().asFile,
                forkProjectDir = forkProjectDirectory.get().asFile,
                parentGradleCommand = parentGradleCommand.get(),
                targetNames = targetNames,
                roots = roots,
                execOperations = execOperations,
            )
        context.prepare()
    }
}

private fun PrepareAOSPProjectSourcesTask.configureAospLocations(project: Project) {
    aospProjectDirectory.set(project.aospProjectDirectory())
    aospOutDirectory.set(project.aospOutDirectory())
    aospRepositoryDirectory.set(project.aospRepositoryDirectory())
    forkProjectDirectory.set(project.rootProject.projectDir)
    parentGradleCommand.set(project.parentGradleCommand())
}

private class AospPrepareContext(
    private val aospProjectDir: File,
    private val aospOutDir: File,
    private val aospRepository: File,
    private val forkProjectDir: File,
    private val parentGradleCommand: String,
    private val targetNames: Set<String>,
    private val roots: Map<AospCoordinate, String>,
    private val execOperations: ExecOperations,
) {
    private val versions = AospVersionCatalog(aospProjectDir.resolve("libraryversions.toml"))

    fun prepare() {
        val executedRequests = linkedSetOf<PublishRequest>()
        while (true) {
            val requests = collectPublishRequests()
            val missingMetadataRequests =
                requests.filter { request ->
                    request !in executedRequests &&
                        !moduleMetadataFile(request.coordinate, request.version).isFile
                }
            if (missingMetadataRequests.isEmpty()) {
                val remainingRequests = requests.filterNot(executedRequests::contains)
                runPublishRequests(remainingRequests)
                requests.forEach { request ->
                    checkPublishedModule(request.coordinate, request.version)
                }
                return
            }
            runPublishRequests(missingMetadataRequests)
            executedRequests += missingMetadataRequests
        }
    }

    private fun shouldBuildFromAosp(coordinate: AospCoordinate): Boolean {
        if (!coordinate.group.startsWith("androidx.")) return false
        val sourceVersion = versions.versionForGroup(coordinate.group) ?: return false
        val requestedVersion = coordinate.version ?: return false
        if (sourceVersion != requestedVersion) return false
        return aospProjectExists(coordinate.derivedProjectPath())
    }

    private fun collectPublishRequests(): Set<PublishRequest> {
        val requests = linkedSetOf<PublishRequest>()
        val processed = linkedSetOf<AospCoordinate>()
        val queue = ArrayDeque(roots.keys)
        while (queue.isNotEmpty()) {
            val coordinate = queue.removeFirst()
            if (!processed.add(coordinate)) continue

            val projectPath = roots[coordinate] ?: coordinate.derivedProjectPath()
            if (!aospProjectExists(projectPath)) continue

            val version = versions.versionForGroup(coordinate.group) ?: continue
            requests +=
                PublishRequest(
                    projectPath = projectPath,
                    coordinate = coordinate,
                    version = version,
                    taskPath = "$projectPath:publishKotlinMultiplatformPublicationToMavenRepository",
                )

            val rootModule = moduleMetadataFile(coordinate, version)
            val dependencies = linkedSetOf<AospCoordinate>()
            dependencies += rootModule.dependencies()

            targetNames.forEach { targetName ->
                val targetModule = rootModule.targetModuleNameFor(coordinate, targetName)
                if (targetModule != null) {
                    val targetCoordinate = coordinate.copy(module = targetModule)
                    val publicationName = publicationName(coordinate.module, targetModule)
                    requests +=
                        PublishRequest(
                            projectPath = projectPath,
                            coordinate = targetCoordinate,
                            version = version,
                            taskPath = "$projectPath:publish${publicationName}PublicationToMavenRepository",
                        )
                    dependencies += moduleMetadataFile(targetCoordinate, version).dependencies()
                }
            }

            dependencies
                .filter(::shouldBuildFromAosp)
                .filterNot(processed::contains)
                .forEach(queue::addLast)
        }
        return requests
    }

    private fun checkPublishedModule(coordinate: AospCoordinate, version: String) {
        val metadataFile = moduleMetadataFile(coordinate, version)
        if (!metadataFile.isFile) {
            throw GradleException("AOSP publication did not produce `$metadataFile`")
        }
    }

    private fun runPublishRequests(requests: List<PublishRequest>) {
        if (requests.isEmpty()) return
        requests.map { it.projectPath }.distinct().forEach(::ensureGeneratedApiLevelsFile)
        runAospGradle(requests.map { it.taskPath }.distinct())
    }

    private fun runAospGradle(taskPaths: List<String>) {
        execOperations.exec { spec ->
            spec.workingDir = forkProjectDir
            spec.commandLine(
                parentGradleCommand,
                "-p",
                "..",
                *taskPaths.toTypedArray(),
                "-x",
                "generateApi",
                "--no-configuration-cache",
            )
        }.assertNormalExitValue()
    }

    private fun ensureGeneratedApiLevelsFile(projectPath: String) {
        val apiLevelsFile =
            aospOutDir.resolve("androidx")
                .resolve(projectPath.removePrefix(":").replace(":", File.separator))
                .resolve("build/api/apiLevels.json")
        apiLevelsFile.parentFile.mkdirs()
        if (!apiLevelsFile.isFile) {
            apiLevelsFile.writeText("{}\n")
        }
    }

    private fun moduleMetadataFile(coordinate: AospCoordinate, version: String): File {
        val groupPath = coordinate.group.replace(".", File.separator)
        return aospRepository.resolve(groupPath)
            .resolve(coordinate.module)
            .resolve(version)
            .resolve("${coordinate.module}-$version.module")
    }

    private fun aospProjectExists(projectPath: String): Boolean =
        aospProjectDir.resolve(projectPath.removePrefix(":").replace(":", File.separator)).isDirectory
}

private data class PublishRequest(
    val projectPath: String,
    val coordinate: AospCoordinate,
    val version: String,
    val taskPath: String,
)

private fun File.dependencies(): Set<AospCoordinate> {
    if (!isFile) return emptySet()
    val root = JsonSlurper().parse(this) as? Map<*, *> ?: return emptySet()
    val variants = root["variants"] as? List<*> ?: return emptySet()
    return variants
        .flatMap { variant ->
            val variantMap = variant as? Map<*, *> ?: return@flatMap emptyList<Map<*, *>>()
            (variantMap["dependencies"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        }
        .mapNotNull { dependency ->
            val group = dependency["group"] as? String ?: return@mapNotNull null
            val module = dependency["module"] as? String ?: return@mapNotNull null
            val version = (dependency["version"] as? Map<*, *>)?.get("requires") as? String
            AospCoordinate(group, module, version)
        }
        .toSet()
}

private fun File.targetModuleNameFor(coordinate: AospCoordinate, targetName: String): String? {
    if (!isFile) return null
    val root = JsonSlurper().parse(this) as? Map<*, *> ?: return null
    val variants = root["variants"] as? List<*> ?: return null
    val availableModules =
        variants.mapNotNull { variant ->
            val variantMap = variant as? Map<*, *> ?: return@mapNotNull null
            val availableAt = variantMap["available-at"] as? Map<*, *> ?: return@mapNotNull null
            availableAt["module"] as? String
        }.toSet()

    val module = coordinate.module
    val candidates =
        when (targetName) {
            "Desktop" -> listOf("$module-desktop", "$module-jvm")
            "Js" -> listOf("$module-js")
            "WasmJs" -> listOf("$module-wasm-js")
            "IosArm64" -> listOf("$module-iosarm64")
            "IosSimulatorArm64" -> listOf("$module-iossimulatorarm64")
            else -> emptyList()
        }
    return candidates.firstOrNull { it in availableModules }
}

private fun publicationName(rootModule: String, targetModule: String): String {
    val suffix = targetModule.removePrefix(rootModule).removePrefix("-")
    return when (suffix) {
        "desktop" -> "Desktop"
        "jvm" -> "Jvm"
        "js" -> "Js"
        "wasm-js" -> "WasmJs"
        "iosarm64" -> "IosArm64"
        "iossimulatorarm64" -> "IosSimulatorArm64"
        else -> error("Unknown AOSP target module suffix `$suffix` for `$targetModule`")
    }
}

private data class AospCoordinate(
    val group: String,
    val module: String,
    val version: String? = null,
) {
    val notation: String
        get() = "$group:$module"

    fun derivedProjectPath(): String =
        ":" + group.removePrefix("androidx.").replace(".", ":") + ":$module"

    companion object {
        fun parse(notation: String): AospCoordinate {
            val parts = notation.split(":")
            require(parts.size == 2) { "Expected group:module notation, got `$notation`" }
            return AospCoordinate(parts[0], parts[1])
        }
    }
}

private class AospVersionCatalog(private val file: File) {
    private val versions: Map<String, String> by lazy {
        val regex = Regex("""^([A-Z0-9_]+)\s*=\s*"([^"]+)"""")
        sectionLines("versions").mapNotNull { line ->
            regex.find(line)?.let { it.groupValues[1] to it.groupValues[2] }
        }.toMap()
    }

    private val groupVersionKeys: Map<String, String> by lazy {
        val groupRegex = Regex("""group\s*=\s*"([^"]+)"""")
        val versionRegex = Regex("""atomicGroupVersion\s*=\s*"versions\.([^"]+)"""")
        sectionLines("groups").mapNotNull { line ->
            val group = groupRegex.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            val versionKey = versionRegex.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            group to versionKey
        }.toMap()
    }

    fun versionForGroup(group: String): String? =
        groupVersionKeys[group]?.let { versions[it] }

    private fun sectionLines(sectionName: String): List<String> {
        val header = "[$sectionName]"
        val lines = file.readLines()
        val start = lines.indexOf(header)
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { !it.startsWith("[") }
    }
}

private fun Project.parentGradleCommand(): String =
    if (System.getProperty("os.name").lowercase(Locale.US).contains("windows")) {
        "..\\gradlew.bat"
    } else {
        "../gradlew"
    }

private fun Project.aospProjectDirectory(): File =
    rootProject.projectDir.parentFile

private fun Project.aospOutDirectory(): File =
    aospProjectDirectory().parentFile.parentFile.resolve("out")

private fun Project.aospRepositoryDirectory(): File =
    aospOutDirectory().resolve("repository")
