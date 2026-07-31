/*
 * Copyright 2023 The Android Open Source Project
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

@file:OptIn(ExperimentalWasmDsl::class)

import java.util.*
import kotlin.collections.map
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm("desktop")
    js {
        outputModuleName = "mpp-demo"
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }
    wasmJs {
        outputModuleName = "mpp-demo"
        browser {
            // https://youtrack.jetbrains.com/issue/KT-68614
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "demo.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    open = mapOf(
                        "app" to mapOf(
                            "name" to "google-chrome",
                        )
                    )
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }
    macosArm64() {
        binaries {
            executable() {
                entryPoint = "androidx.compose.mpp.demo.main"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal"
                )
                // TODO: the current release binary surprises LLVM, so disable checks for now.
                freeCompilerArgs += "-Xdisable-phases=VerifyBitcode"
            }
        }
    }
    iosArm64("iosArm64") {
        binaries {
            executable() {
                entryPoint = "androidx.compose.mpp.demo.main"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal",
                    "-linker-option", "-framework", "-linker-option", "CoreText",
                    "-linker-option", "-framework", "-linker-option", "CoreGraphics"
                )
                // TODO: the current compose binary surprises LLVM, so disable checks for now.
                freeCompilerArgs += "-Xdisable-phases=VerifyBitcode"
            }
        }
    }
    iosSimulatorArm64("iosSimArm64") {
        binaries {
            executable() {
                entryPoint = "androidx.compose.mpp.demo.main"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal",
                    "-linker-option", "-framework", "-linker-option", "CoreText",
                    "-linker-option", "-framework", "-linker-option", "CoreGraphics"
                )
                // TODO: the current compose binary surprises LLVM, so disable checks for now.
                freeCompilerArgs += "-Xdisable-phases=VerifyBitcode"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinCoroutinesCore)
                implementation(libs.kotlinSerializationCore)
                implementation(libs.skiko.skottie)

                implementation(project(":compose:foundation:foundation"))
                implementation(project(":compose:foundation:foundation-layout"))
                implementation(project(":compose:material3:material3"))
                implementation(project(":compose:material3:material3-window-size-class"))
                implementation(project(":compose:material3:adaptive:adaptive"))
                implementation(project(":compose:material3:adaptive:adaptive-layout"))
                implementation(project(":compose:material3:adaptive:adaptive-navigation"))
                implementation(project(":compose:material:material"))
                implementation(project(":compose:runtime:runtime"))
                implementation(project(":compose:ui:ui"))
                implementation(project(":compose:ui:ui-geometry"))
                implementation(project(":compose:ui:ui-graphics"))
                implementation(project(":compose:ui:ui-text"))
                implementation(project(":compose:ui:ui-backhandler"))
                implementation(project(":compose:ui:ui-skiko"))
                implementation(project(":lifecycle:lifecycle-common"))
                implementation(project(":lifecycle:lifecycle-runtime"))
                implementation(project(":lifecycle:lifecycle-runtime-compose"))
                implementation(project(":lifecycle:lifecycle-viewmodel-compose"))
                implementation(project(":lifecycle:lifecycle-viewmodel-savedstate"))
                implementation(project(":navigation:navigation-common"))
                implementation(project(":navigation:navigation-compose"))
                implementation(project(":navigation:navigation-runtime"))

                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3") {
                    // exclude dependencies, because they override local projects when we build 0.0.0-* version
                    // (see https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-core-desktop/1.6.11/material-icons-core-desktop-1.6.11.module)
                    exclude("org.jetbrains.compose.runtime")
                    exclude("org.jetbrains.compose.ui")
                }
            }
        }

        val skikoMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.skiko)
            }
        }

        val desktopMain by getting {
            dependsOn(skikoMain)
            dependencies {
                implementation(libs.kotlinCoroutinesSwing)
                implementation(libs.skikoAwtRuntime)
                implementation(libs.skikoSkottieAwtRuntime)
            }
        }

        val webMain by getting {
            dependsOn(skikoMain)

            dependencies {
                implementation(libs.kotlinSerializationJson)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                api(libs.kotlinXw3c)
            }
        }

        val nativeMain by getting { dependsOn(skikoMain) }
        val darwinMain by creating { dependsOn(nativeMain) }
        val macosMain by getting { dependsOn(darwinMain) }
        val iosMain by getting { dependsOn(darwinMain) }
    }

    targets.withType<KotlinJsIrTarget>().all { configureSkikoWebRuntime(project, this) }
}

enum class Target(val simulator: Boolean, val key: String) {
    IOS_ARM64(false, "iosArm64"),
    IOS_SIM_ARM64(true, "iosSimArm64"),
}

if (System.getProperty("os.name") == "Mac OS X") {
// Create Xcode integration tasks.
    val sdkName: String? = System.getenv("SDK_NAME")

    val target = sdkName.orEmpty().let {
        when {
            it.startsWith("iphoneos") -> Target.IOS_ARM64
            it.startsWith("iphonesimulator") -> {
                if (System.getProperty("os.arch") == "aarch64") {
                    Target.IOS_SIM_ARM64
                } else {
                    error("x64 host is not supported anymore!")
                }
            }

            else -> Target.IOS_SIM_ARM64
        }
    }

    val targetBuildDir: String? = System.getenv("TARGET_BUILD_DIR")
    val executablePath: String? = System.getenv("EXECUTABLE_PATH")
    val buildType = System.getenv("CONFIGURATION")
        ?.let { NativeBuildType.valueOf(it.uppercase(Locale.getDefault())) }
        ?: NativeBuildType.DEBUG

    val currentTarget = kotlin.targets[target.key] as KotlinNativeTarget
    val kotlinBinary = currentTarget.binaries.getExecutable(buildType)
    val xcodeIntegrationGroup = "Xcode integration"

    val packForXCode = if (sdkName == null || targetBuildDir == null || executablePath == null) {
        // The build is launched not by Xcode ->
        // We cannot create a copy task and just show a meaningful error message.
        tasks.register("packForXCode") {
            doLast {
                group = xcodeIntegrationGroup
                throw IllegalStateException("Please run the task from Xcode")
            }
        }
    } else {
        // Otherwise copy the executable into the Xcode output directory.
        tasks.register("packForXCode", Copy::class.java) {
            dependsOn(kotlinBinary.linkTaskProvider)

            group = xcodeIntegrationGroup
            destinationDir = file(targetBuildDir)

            val dsymSource = kotlinBinary.outputFile.absolutePath + ".dSYM"
            val dsymDestination = File(executablePath).parentFile.name + ".dSYM"
            val oldExecName = kotlinBinary.outputFile.name
            val newExecName = File(executablePath).name

            from(dsymSource) {
                into(dsymDestination)
                rename(oldExecName, newExecName)
            }

            from(kotlinBinary.outputFile) {
                rename { executablePath }
            }
        }
    }
}

tasks.register("runDesktop", JavaExec::class.java) {
    dependsOn(":compose:desktop:desktop:jvmJar")
    mainClass.set("androidx.compose.mpp.demo.Main_desktopKt")
    args = listOfNotNull(project.findProperty("args")?.toString())
    systemProperty("skiko.fps.enabled", "true")
    val compilation = kotlin.jvm("desktop").compilations["main"]
    classpath =
        compilation.output.allOutputs +
            compilation.runtimeDependencyFiles
}


project.tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-Xir-dce",
        "-Xwasm-generate-wat",
        "-Xwasm-enable-array-range-checks"
    )
}

private fun configureSkikoWebRuntime(
    project: Project,
    target: KotlinJsIrTarget,
) {
    val titledTargetName = target.name.replaceFirstChar { it.titlecase() }
    val mainCompilation = target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME)!!
    val runtimeDepsConfig = project.configurations.findByName(mainCompilation.runtimeDependencyConfigurationName)!!
    val skikoWebRuntimeJarFiles = runtimeDepsConfig.incoming.artifactView {
        @Suppress("UnstableApiUsage")
        withVariantReselection()
        attributes {
            runtimeDepsConfig.attributes.keySet().forEach {
                @Suppress("UNCHECKED_CAST")
                attribute(it as Attribute<Any>, runtimeDepsConfig.attributes.getAttribute(it) as Any)
            }
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "skiko-runtime"))
        }
    }.files
    val unpackedRuntimeDir = project.layout.buildDirectory.dir("compose/skiko-${target.name}-runtime")

    val unpackRuntime = project.tasks.register("unpackSkikoRuntimeFor$titledTargetName", Copy::class.java) {
        destinationDir = project.file(unpackedRuntimeDir)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(
            skikoWebRuntimeJarFiles.map { artifact -> project.zipTree(artifact) }
        )
    }

    target.compilations.all {
        if (target.wasmTargetType != null) {
            // Kotlin/Wasm uses ES module system to depend on skiko through skiko.mjs.
            // Further bundler could process all files by its own (both skiko.mjs and skiko.wasm) and then emits its own version.
            // So that’s why we need to provide skiko.mjs and skiko.wasm only for webpack, but not in the final dist.
            binaries.all {
                linkSyncTask.configure {
                    dependsOn(unpackRuntime)
                    from.from(unpackedRuntimeDir)
                }
            }
        } else {
            // Kotlin/JS depends on Skiko through global space.
            // Bundler cannot know anything about global externals, so that’s why we need to copy it to final dist
            project.tasks.named(processResourcesTaskName, ProcessResources::class.java) {
                from(unpackedRuntimeDir)
                dependsOn(unpackRuntime)
                exclude("META-INF")
            }
        }
    }
}

val demoBuildInfoDir = layout.buildDirectory.dir("generated/buildInfo/kotlin")

val generateDemoBuildInfo = tasks.register("generateDemoBuildInfo") {
    val outputDir = demoBuildInfoDir
    val repoDir = projectDir
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    doLast {
        fun exec(command: List<String>): String = try {
            val process = ProcessBuilder(command)
                .directory(repoDir)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }

        fun git(vararg args: String) = exec(listOf("git") + args)

        fun esc(value: String) = value
            .replace("\\", "\\\\")
            .replace("$", "\\\$")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace("\t", "\\t")

        val branch = git("rev-parse", "--abbrev-ref", "HEAD")
        val hash = git("rev-parse", "--short", "HEAD")
        val author = git("log", "-1", "--format=%an")
        val message = git("log", "-1", "--format=%B")
        val buildTime = "%1\$tF %1\$tT %1\$tZ (%1\$tz)".format(Date())

        val outFile = outputDir.get()
            .file("androidx/compose/mpp/demo/BuildInfo.kt").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            |package androidx.compose.mpp.demo
            |
            |internal object BuildInfo {
            |    const val branch: String = "${esc(branch)}"
            |    const val commitHash: String = "${esc(hash)}"
            |    const val author: String = "${esc(author)}"
            |    const val buildTime: String = "${esc(buildTime)}"
            |    const val commitMessage: String = "${esc(message)}"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateDemoBuildInfo)
