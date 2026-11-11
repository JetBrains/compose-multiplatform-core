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
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
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
            implementation(project(":benchmarks-runtime"))
            implementation(project(":benchmarks-report"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.resources)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
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
