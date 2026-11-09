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

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Exec

plugins {
    id("org.jetbrains.compose") version "1.13.0-beta02"
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get()
    id("kotlin-multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

version = "1.0-SNAPSHOT"

kotlin {
    jvm("desktop")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "benchmarks"
            isStatic = true
        }
    }

    macosArm64 {
        binaries.executable {
            entryPoint = "main"
        }
    }

    wasmJs {
        outputModuleName = "mpp-benchmarks"
        browser {
            commonWebpackConfig {
                outputFileName = "benchmarks.js"
            }
        }
        d8 {
            compilerOptions.freeCompilerArgs.add("-Xwasm-attach-js-exception")
        }
        binaries.executable()
        binaries.configureEach {
            compilation.compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }
    }

    js {
        outputModuleName = "mpp-benchmarks"
        browser {
            commonWebpackConfig {
                outputFileName = "benchmarks.js"
            }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose:runtime:runtime"))
            implementation(project(":compose:ui:ui"))
            implementation(project(":compose:foundation:foundation"))
            implementation(project(":compose:material:material"))
            implementation("org.jetbrains.compose.components:components-resources:1.13.0-beta02") {
                exclude(group = "org.jetbrains.compose.runtime")
                exclude(group = "org.jetbrains.compose.ui")
            }

            implementation(libs.skiko)
            implementation(libs.kotlinCoroutinesCore)
            implementation(libs.kotlinSerializationJson)
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }

        webMain.dependencies {
            implementation(libs.kotlinXw3c)
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("io.ktor:ktor-client-js:3.6.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinCoroutinesSwing)
                implementation(libs.skikoAwtRuntime)
                implementation("io.ktor:ktor-server-core:3.6.1")
                implementation("io.ktor:ktor-server-netty:3.6.1")
                implementation("io.ktor:ktor-server-content-negotiation:3.6.1")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("io.ktor:ktor-client-java:3.6.1")
                implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
                implementation("io.ktor:ktor-server-cors:3.6.1")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "compose_benchmarks.benchmarks.generated.resources"
}

val runArguments = providers.gradleProperty("runArguments").orElse("")
val versionInfo = "versionInfo=\"local (Kotlin ${libs.versions.kotlin.get()})\""
val benchmarkArguments = runArguments.map { arguments ->
    (arguments.split(" ").filter { it.isNotEmpty() } + versionInfo)
        .map { it.replace(" ", "%20") }
}

compose.desktop {
    application {
        mainClass = "Main_desktopKt"
        args.addAll(benchmarkArguments.get())
    }
}

apply(from = "gradle/wasm-d8-runtime.gradle.kts")

tasks.withType<Exec>().matching { it.name.startsWith("runReleaseExecutableMacos") }.configureEach {
    args(benchmarkArguments.get())
}

tasks.named<KotlinWebpack>("wasmJsBrowserProductionRun") {
    val query = benchmarkArguments.get()
        .mapIndexed { index, arg -> "arg$index=$arg" }
        .joinToString("&")
    devServerProperty = (devServerProperty.orNull ?: KotlinWebpackConfig.DevServer()).copy(
        open = "http://localhost:8080?$query"
    )
}

tasks.named<D8Exec>("wasmJsD8DevelopmentRun") {
    dependsOn("prepareWasmJsD8DevelopmentRuntime")
    inputFileProperty.set(layout.buildDirectory.file("d8/development/launcher.mjs"))
}

tasks.named<D8Exec>("wasmJsD8ProductionRun") {
    dependsOn("prepareWasmJsD8ProductionRuntime")
    inputFileProperty.set(layout.buildDirectory.file("d8/production/launcher.mjs"))
}

tasks.withType<D8Exec>().configureEach {
    args(benchmarkArguments.get())
}

tasks.withType<BinaryenExec>().configureEach {
    binaryenArguments.add("-g")
}

val benchmarkRepositoryDir = rootProject.layout.projectDirectory.asFile
val benchmarkGradlew = rootProject.layout.projectDirectory.file("gradlew").asFile

tasks.register("runBrowserAndSaveStats") {
    group = "application"
    description = "Runs the benchmark results server and the Wasm browser benchmarks."
    val repositoryDir = benchmarkRepositoryDir
    val gradlew = benchmarkGradlew
    val arguments = runArguments
    doLast {
        var serverProcess: Process? = null
        var clientProcess: Process? = null
        try {
            serverProcess = ProcessBuilder(
                gradlew.absolutePath,
                ":compose:mpp:benchmarks:run",
                "-PrunArguments=runServer=true saveStatsToJSON=true",
                "--console=plain"
            ).directory(repositoryDir).inheritIO().start()

            clientProcess = ProcessBuilder(
                gradlew.absolutePath,
                ":compose:mpp:benchmarks:wasmJsBrowserProductionRun",
                "-PrunArguments=" + arguments.get() + " saveStatsToJSON=true",
                "--console=plain"
            ).directory(repositoryDir).inheritIO().start()

            serverProcess.waitFor()
        } finally {
            serverProcess?.destroy()
            clientProcess?.destroy()
        }
    }
}
