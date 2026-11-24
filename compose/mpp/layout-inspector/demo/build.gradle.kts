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

import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
}

val macosDemo = kotlin.macosArm64 {
    binaries.executable(setOf(NativeBuildType.DEBUG)) {
        entryPoint = "androidx.compose.mpp.layoutinspector.demo.main"
        freeCompilerArgs += listOf("-linker-option", "-framework", "-linker-option", "Metal")
    }
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose:runtime:runtime"))
        }
        macosArm64Main.dependencies {
            implementation(project(":compose:mpp:layout-inspector:agent"))
            implementation(project(":compose:foundation:foundation"))
            implementation(project(":compose:foundation:foundation-layout"))
            implementation(project(":compose:ui:ui"))
            implementation(libs.kotlinCoroutinesCore)
        }
        jvmTest.dependencies {
            implementation(project(":compose:mpp:layout-inspector:client"))
            implementation(kotlin("test"))
            implementation(libs.kotlinCoroutinesCore)
        }
    }
}

tasks.named<Test>("jvmTest") {
    exclude("**/MacosDemoIntegrationTest*")
}

val isAppleSiliconMac = providers.systemProperty("os.name").get() == "Mac OS X" &&
    providers.systemProperty("os.arch").get() == "aarch64"

if (isAppleSiliconMac) {
    val executable = macosDemo.binaries.getExecutable(NativeBuildType.DEBUG)
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("test")

    tasks.register<Test>("macosDemoIntegrationTest") {
        group = "verification"
        description = "Runs the JVM inspector client against the native macOS demo window."
        dependsOn(executable.linkTaskProvider, compilation.compileTaskProvider)
        testClassesDirs = compilation.output.classesDirs
        classpath = compilation.output.allOutputs + compilation.runtimeDependencyFiles!!
        include("**/MacosDemoIntegrationTest*")
        systemProperty("layoutInspector.macosDemoExecutable", executable.outputFile.absolutePath)
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Launches a live macOS application") { true }
    }
}
