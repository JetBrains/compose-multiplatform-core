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
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.KonanTarget

fun Project.configureSwiftCompatibilityLinking() {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType(KotlinNativeTarget::class.java)
            .all { target -> target.configureSwiftCompatibilityLinking() }
    }
}

private fun KotlinNativeTarget.configureSwiftCompatibilityLinking() {
    if (System.getProperty("os.name") != "Mac OS X") return

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
