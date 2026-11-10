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

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get()
    id("org.jetbrains.compose") version "1.13.0-beta02"
}

kotlin {
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BenchmarksApp"
            isStatic = true
        }
    }

    macosArm64 {
        binaries.executable {
            entryPoint = "org.jetbrains.compose.benchmarks.app.main"
        }
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "benchmarks-app.js"
            }
        }
        binaries.executable()
    }
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "benchmarks-app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose:mpp:newbenchmarks:benchmarks-runtime"))
            implementation(project(":compose:mpp:newbenchmarks:benchmarks-report"))
            implementation(project(":compose:runtime:runtime"))
            implementation(project(":compose:ui:ui"))
            implementation(project(":compose:foundation:foundation"))
            implementation(project(":compose:material:material"))
            implementation("org.jetbrains.compose.components:components-resources:1.13.0-beta02") {
                exclude(group = "org.jetbrains.compose.runtime")
                exclude(group = "org.jetbrains.compose.ui")
            }
        }
        jvmMain.dependencies {
            implementation(libs.skikoAwtRuntime)
            implementation(libs.kotlinCoroutinesSwing)
        }
        webMain.dependencies {
            implementation(libs.kotlinXw3c)
        }
    }
}

compose {
    resources {
        packageOfResClass = "compose_benchmarks.benchmarks.generated.resources"
    }
    desktop {
        application {
            mainClass = "org.jetbrains.compose.benchmarks.app.Main_jvmKt"
            buildTypes.release.proguard {
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }
}
