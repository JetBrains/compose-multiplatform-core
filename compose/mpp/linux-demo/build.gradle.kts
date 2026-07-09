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

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
}

kotlin {
    linuxX64() {
        binaries {
            executable() {
                entryPoint = "main"
                linkerOpts(
                    "-L/usr/lib/x86_64-linux-gnu",
                    "-lstdc++",
                    "--allow-shlib-undefined",
                    "--unresolved-symbols=ignore-all"
                )
                @OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)
                disableNativeCache(
                    org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion.`2_3_20`,
                    "Linker errors"
                )
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":compose:runtime:runtime"))
                implementation(project(":compose:ui:ui"))
                implementation(project(":compose:foundation:foundation"))
                implementation(project(":compose:foundation:foundation-layout"))
                implementation(project(":compose:material3:material3"))
                implementation(libs.skiko)
                implementation(libs.kotlinCoroutinesCore)
            }
        }
    }
}
