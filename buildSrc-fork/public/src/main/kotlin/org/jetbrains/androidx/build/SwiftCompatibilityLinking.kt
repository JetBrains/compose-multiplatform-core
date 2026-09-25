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
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.KonanTarget

/**
 * Adds the active Xcode toolchain's Swift library directory to iOS native links.
 *
 * Kotlin/Native does not currently add this directory itself (KT-69793). It is resolved from the active
 * toolchain at final link time; Swift auto-link metadata selects the needed runtime and compatibility libraries.
 */
fun Project.configureSwiftCompatibilityLinking() {
    if (!OperatingSystem.current().isMacOsX) return

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        // KMP target configuration may query native freeCompilerArgs. Register the xcrun-backed provider
        // afterwards so configuring an unrelated target, such as Wasm, does not resolve the Xcode toolchain.
        afterEvaluate {
            extensions
                .getByType<KotlinMultiplatformExtension>()
                .targets
                .withType<KotlinNativeTarget>()
                .all { target -> target.configureSwiftCompatibilityLinking() }
        }
    }
}

private fun KotlinNativeTarget.configureSwiftCompatibilityLinking() {
    val sdkName =
        when (konanTarget) {
            KonanTarget.IOS_ARM64 -> "iphoneos"
            KonanTarget.IOS_X64,
            KonanTarget.IOS_SIMULATOR_ARM64 -> "iphonesimulator"
            else -> return
        }
    val swiftCompatibilityLibraryDir =
        project.providers
            .exec { spec -> spec.commandLine("xcrun", "--sdk", sdkName, "--show-toolchain-path") }
            .standardOutput
            .asText
            .map { toolchainPath ->
                File(toolchainPath.trim())
                    .resolve("usr/lib/swift/$sdkName")
                    .absolutePath
            }

    binaries.withType<NativeBinary>().all { binary ->
        binary.linkTaskProvider.configure { linkTask ->
            linkTask.toolOptions.freeCompilerArgs.addAll(
                swiftCompatibilityLibraryDir.map { libraryDir ->
                    listOf("-linker-option", "-L$libraryDir")
                }
            )
        }
    }
}
