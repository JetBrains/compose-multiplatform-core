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

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("kotlin-multiplatform")
}

kotlin {
    jvm()
    macosArm64()
    iosSimulatorArm64 {
        providers.gradleProperty("layoutInspector.simulatorUdid").orNull?.let {
            testRuns["test"].deviceId = it
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":compose:mpp:layout-inspector:session"))
                api(libs.kotlinCoroutinesCore)
                implementation(libs.ktorClientCore)
                implementation(libs.ktorClientCio)
                implementation(libs.ktorClientWebsockets)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":compose:mpp:layout-inspector:agent"))
        }
        jvmTest.dependencies {
            implementation(libs.ktorServerCio)
            implementation(libs.ktorServerWebsockets)
        }
    }
}

// The fixture is an iOS test executable, never part of the published agent library.
tasks.named<Test>("jvmTest") {
    exclude("**/SimulatorIntegrationTest*")
    doFirst {
        val testTask = this as Test
        testTask.systemProperty("layoutInspector.desktopFixtureClasspath", testTask.classpath.asPath)
    }
}

val agentProject = evaluationDependsOn(":compose:mpp:layout-inspector:agent")
val simulatorAgentTarget = agentProject.extensions.getByType<KotlinMultiplatformExtension>()
    .targets.getByName("iosSimulatorArm64") as KotlinNativeTarget
val simulatorAgentBinary = simulatorAgentTarget.binaries.getTest(NativeBuildType.DEBUG)

tasks.register<Test>("simulatorIntegrationTest") {
    group = "verification"
    description = "Runs the JVM client against an agent inside a booted Apple Silicon iOS Simulator."
    dependsOn(simulatorAgentBinary.linkTaskProvider)
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("test")
    dependsOn(compilation.compileTaskProvider)
    testClassesDirs = compilation.output.classesDirs
    classpath = compilation.output.allOutputs + compilation.runtimeDependencyFiles!!
    include("**/SimulatorIntegrationTest*")
    systemProperty("layoutInspector.simulatorAgentExecutable", simulatorAgentBinary.outputFile.absolutePath)
    systemProperty("layoutInspector.simulatorUdid", providers.gradleProperty("layoutInspector.simulatorUdid").getOrElse("booted"))
    // The external simulator process must be exercised on every invocation.
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Uses a live iOS Simulator") { true }
}
