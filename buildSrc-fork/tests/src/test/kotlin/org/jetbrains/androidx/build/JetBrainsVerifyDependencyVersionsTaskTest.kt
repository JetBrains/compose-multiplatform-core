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
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JetBrainsVerifyDependencyVersionsTaskTest {
    @get:Rule
        val temporaryFolder = TemporaryFolder()

    @Test
    fun `fails when dependency is less stable`() {
        testProject(
            version = "1.0.0-alpha01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-SNAPSHOT")
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0-beta01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-alpha01")
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0-rc01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-beta01")
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-rc01")
            """.trimIndent(),
        ).buildAndFail()
    }

    @Test
    fun `succeeds when dependency is as stable`() {
        testProject(
            version = "1.0.0-alpha01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-alpha02")
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0-beta01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-beta02")
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0-rc01",
            dependencies = """
                implementation("androidx.example:dependency:1.0.0-rc02")
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0",
            dependencies = """
                implementation("androidx.example:dependency:1.0.1")
            """.trimIndent(),
        ).build()
    }

    @Test
    fun `fails when constraint is less stable`() {
        testProject(
            version = "1.0.0-alpha01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-SNAPSHOT")
                }
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0-beta01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-alpha01")
                }
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0-rc01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-beta01")
                }
            """.trimIndent(),
        ).buildAndFail()
        testProject(
            version = "1.0.0",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-rc01")
                }
            """.trimIndent(),
        ).buildAndFail()
    }

    @Test
    fun `succeeds when constraint is as stable`() {
        testProject(
            version = "1.0.0-alpha01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-alpha02")
                }
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0-beta01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-beta02")
                }
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0-rc01",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.0-rc02")
                }
            """.trimIndent(),
        ).build()
        testProject(
            version = "1.0.0",
            dependencies = """
                constraints {
                    implementation("androidx.example:constrained:1.0.1")
                }
            """.trimIndent(),
        ).build()
    }

    private fun testProject(version: String, dependencies: String): GradleRunner {
        val root = temporaryFolder.newFolder()
        root.resolve("compose/ui/ui").mkdirs()
        val pluginClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .joinToString(",\n") { "\"${File(it).invariantSeparatorsPath}\"" }
        root.resolve("settings.gradle").writeText("include(\":compose:ui:ui\")")
        root.resolve("build.gradle").writeText(
            """
                import static org.jetbrains.androidx.build.JetBrainsVerifyDependencyVersionsTaskKt.configureDependencyVerification

                buildscript {
                    dependencies {
                        classpath(files([$pluginClasspath]))
                    }
                }

                project(":compose:ui:ui") {
                    version = "$version"
                    apply plugin: "org.jetbrains.kotlin.multiplatform"

                    kotlin {
                        jvm()

                        sourceSets {
                            jvmMain.dependencies {
                                $dependencies
                            }
                        }
                    }

                    configureDependencyVerification(project)
                }
            """.trimIndent(),
        )

        return GradleRunner.create()
            .withProjectDir(root)
            .withArguments(":compose:ui:ui:jbVerifyDependencyVersions", "--stacktrace")
    }
}
