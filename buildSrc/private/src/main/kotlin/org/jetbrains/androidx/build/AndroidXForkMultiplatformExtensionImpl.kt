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
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.tomlj.Toml


private fun KotlinJsTest.passTestFlagsToEnvironment() {
    listOf(
        "jetbrains.androidx.web.tests.enableChrome",
        "jetbrains.androidx.web.tests.enableChromium",
        "jetbrains.androidx.web.tests.enableFirefox",
        "jetbrains.androidx.web.tests.enableSafari",
    ).forEach { propertyName ->
        if (project.findProperty(propertyName)?.toString()?.toBoolean() == true) {
            environment(propertyName, "1")
        }
    }
}

private fun Project.onTargetConfigured(
    type: KotlinPlatformType,
    body: KotlinMultiplatformExtension.() -> Unit
) {
    val multiplatformExtension = multiplatformExtension ?: return
    multiplatformExtension.targets.all { target: KotlinTarget ->
        if (target.platformType == type) {
            multiplatformExtension.body()
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
fun Project.configureTargetsForComposeMultiplatform() {
    val toml = Toml.parse(
        project.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath()
    )
    val skikoVersion = toml.getTable("versions")!!.getString("skiko")!!
    val skikoWasm = project.configurations.findByName("skikoWasm")
        ?: project.configurations.create("skikoWasm")

    onTargetConfigured(KotlinPlatformType.js) {
        js {
            browser {
                testTask {
                    // We need to set up at least one browser here due to kotlin tooling limitations
                    // Actual browser configuration is set in mpp/karma.config.d/js/config.js
                    it.passTestFlagsToEnvironment()
                    it.useKarma {
                        // At least one browser is needed due to Kotlin tooling limitations
                        useChrome()
                        useFirefox()
                        useSafari()
                        useConfigDirectory(rootProject.projectDir.resolve("mpp/karma.config.d/js"))
                    }
                }
            }
        }

        val resourcesDir = project.layout.buildDirectory.asFile.get().resolve("resources/skiko-js")

        // Below code helps configure the tests for k/wasm targets
        project.dependencies {
            skikoWasm("org.jetbrains.skiko:skiko-js-wasm-runtime:${skikoVersion}")
        }

        val fetchSkikoWasmRuntime = project.tasks.register("fetchSkikoJsWasmRuntime", Copy::class.java) {
            it.destinationDir = project.file(resourcesDir)
            it.from(skikoWasm.map { artifact ->
                project.zipTree(artifact)
                    .matching { pattern ->
                        pattern.include("skiko.wasm", "skiko.mjs", "js-reexport-symbols.mjs")
                    }
            })
        }

        project.tasks.getByName("jsTestProcessResources").apply {
            dependsOn(fetchSkikoWasmRuntime)
        }

        sourceSets.getByName("jsTest").also {
            it.resources.setSrcDirs(it.resources.srcDirs)
            it.resources.srcDirs(fetchSkikoWasmRuntime.map { it.destinationDir })
        }
    }
    onTargetConfigured(KotlinPlatformType.wasm) {
        wasmJs {
            browser {
                testTask {
                    // We need to set up at least one browser here due to kotlin tooling limitations
                    // Actual browser configuration is set in mpp/karma.config.d/wasm/config.js
                    it.passTestFlagsToEnvironment()
                    it.useKarma {
                        useChrome()
                        useFirefox()
                        useSafari()
                        useConfigDirectory(
                            project.rootProject.projectDir.resolve("mpp/karma.config.d/wasm")
                        )
                    }
                }
            }
        }

        val resourcesDir = project.layout.buildDirectory.asFile.get().resolve("resources/skiko-wasm")

        // Below code helps configure the tests for k/wasm targets
        project.dependencies {
            skikoWasm("org.jetbrains.skiko:skiko-js-wasm-runtime:${skikoVersion}")
        }

        val fetchSkikoWasmRuntime = project.tasks.register("fetchSkikoWasmRuntime", Copy::class.java) {
            it.destinationDir = project.file(resourcesDir)
            it.from(skikoWasm.map { artifact ->
                project.zipTree(artifact)
                    .matching { pattern ->
                        pattern.include("skiko.wasm", "skiko.mjs")
                    }
            })
        }

        project.tasks.getByName("wasmJsTestProcessResources").apply {
            dependsOn(fetchSkikoWasmRuntime)
        }

        sourceSets.getByName("wasmJsTest").also {
            it.resources.setSrcDirs(it.resources.srcDirs)
            it.resources.srcDirs(fetchSkikoWasmRuntime.map { it.destinationDir })
        }
    }
}

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
            val uikitInstrumentedTest = sourceSets.create("uikitInstrumentedTest")

            fun KotlinNativeTargetWithSimulatorTests.configureTestRun() {
                val testCompilation = compilations.create("instrumentedTest") {
                    compilerOptions {
                        // Generate K/N test runner for kotlin.test @Test support
                        freeCompilerArgs.add("-tr")
                    }

                    it.associateWith(compilations.getByName("test"))
                    it.defaultSourceSet.dependsOn(uikitInstrumentedTest)
                }
                binaries.framework("InstrumentedTest", setOf(DEBUG)) {
                    compilation = testCompilation
                    baseName = "InstrumentedTest"
                    isStatic = true
                }
            }
            testableTargets.getByName(
                "uikitX64",
                KotlinNativeTargetWithSimulatorTests::class,
                KotlinNativeTargetWithSimulatorTests::configureTestRun
            )
            testableTargets.getByName(
                "uikitSimArm64",
                KotlinNativeTargetWithSimulatorTests::class,
                KotlinNativeTargetWithSimulatorTests::configureTestRun
            )
        }
    }
}