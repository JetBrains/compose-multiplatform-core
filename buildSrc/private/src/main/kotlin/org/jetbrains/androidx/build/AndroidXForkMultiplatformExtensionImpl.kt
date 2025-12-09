/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.build.multiplatformExtension
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class AndroidXForkMultiplatformExtensionImpl @Inject constructor(
    private val project: Project
) : AndroidXForkMultiplatformExtension {
    override fun configureDarwinFlags() {
        val darwinFlags = listOf(
            "-linker-option", "-framework", "-linker-option", "Metal",
            "-linker-option", "-framework", "-linker-option", "CoreText",
            "-linker-option", "-framework", "-linker-option", "CoreGraphics",
            "-linker-option", "-framework", "-linker-option", "CoreServices"
        )
        val iosFlags = listOf("-linker-option", "-framework", "-linker-option", "UIKit")

        fun KotlinNativeTarget.configureFreeCompilerArgs() {
            val isIOS = konanTarget == KonanTarget.IOS_X64 ||
                konanTarget == KonanTarget.IOS_SIMULATOR_ARM64 ||
                konanTarget == KonanTarget.IOS_ARM64

            binaries.forEach {
                val flags = mutableListOf<String>().apply {
                    addAll(darwinFlags)
                    if (isIOS) addAll(iosFlags)
                }

                // TODO: Remove when the issue is fixed in KGP
                // https://youtrack.jetbrains.com/issue/KT-74564
                // it.freeCompilerArgs += flags
                //
                // Fixes problem when instrumented tests compilation is not properly applied to
                // the framework configuration.
                it.linkTaskProvider.configure {
                    @Suppress("DEPRECATION")
                    it.kotlinOptions.freeCompilerArgs += flags
                }
            }
        }
        project.multiplatformExtension!!.run {
            macosX64 { configureFreeCompilerArgs() }
            macosArm64 { configureFreeCompilerArgs() }
            iosX64 { configureFreeCompilerArgs() }
            iosArm64 { configureFreeCompilerArgs() }
            iosSimulatorArm64 { configureFreeCompilerArgs() }
        }
    }

    override fun iosInstrumentedTest() {
        project.multiplatformExtension!!.run {
            val iosInstrumentedTest = sourceSets.create("iosInstrumentedTest")
            iosInstrumentedTest.kotlin.srcDir("src/uikitInstrumentedTest/kotlin")

            fun KotlinNativeTargetWithSimulatorTests.configureTestRun() {
                val testCompilation = compilations.create("instrumentedTest") {
                    compilerOptions {
                        // Generate K/N test runner for kotlin.test @Test support
                        freeCompilerArgs.add("-tr")
                    }

                    it.associateWith(compilations.getByName("test"))
                    it.defaultSourceSet.dependsOn(iosInstrumentedTest)
                }
                binaries.framework("InstrumentedTest", setOf(DEBUG)) {
                    compilation = testCompilation
                    baseName = "InstrumentedTest"
                    isStatic = true
                }
            }
            testableTargets.getByName(
                "iosX64",
                KotlinNativeTargetWithSimulatorTests::class,
                KotlinNativeTargetWithSimulatorTests::configureTestRun
            )
            testableTargets.getByName(
                "iosSimulatorArm64",
                KotlinNativeTargetWithSimulatorTests::class,
                KotlinNativeTargetWithSimulatorTests::configureTestRun
            )
        }
    }
}