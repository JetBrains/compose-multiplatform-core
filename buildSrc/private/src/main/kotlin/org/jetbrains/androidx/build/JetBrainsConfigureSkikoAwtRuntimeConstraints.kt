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

import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

fun Project.configureSkikoAwtRuntimeConstraints() {
    val currentOs = DefaultNativePlatform.getCurrentOperatingSystem()
    val currentArch = DefaultNativePlatform.getCurrentArchitecture()

    val platformSuffix = when {
        currentOs.isMacOsX && currentArch.isArm64 -> "macos-arm64"
        currentOs.isMacOsX && currentArch.isAmd64 -> "macos-x64"
        currentOs.isLinux && currentArch.isArm64 -> "linux-arm64"
        currentOs.isLinux && currentArch.isAmd64 -> "linux-x64"
        currentOs.isWindows && currentArch.isArm64 -> "windows-arm64"
        currentOs.isWindows && currentArch.isAmd64 -> "windows-x64"
        else -> error("Unsupported platform: OS=${currentOs.name}, Arch=${currentArch.name}")
    }

    // Use dependency substitution to replace the universal dependency with platform-specific one
    // during resolution, without affecting what gets published
    project.configurations.configureEach { configuration ->
        if (configuration.isCanBeResolved) {
            configuration.resolutionStrategy.dependencySubstitution {
                it.all { substitution ->
                    val requested = substitution.requested
                    if (requested is ModuleComponentSelector &&
                        requested.group == "org.jetbrains.skiko" &&
                        requested.module == "skiko-awt-runtime") {
                        // Keep the same version, just change the module name to platform-specific
                        substitution.useTarget(
                            "${requested.group}:skiko-awt-runtime-${platformSuffix}:${requested.version}",
                            "Platform-specific variant selection based on current machine"
                        )
                    }
                }
            }
        }
    }
}
