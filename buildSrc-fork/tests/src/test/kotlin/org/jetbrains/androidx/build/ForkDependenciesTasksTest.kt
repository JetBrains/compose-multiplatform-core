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
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForkDependenciesTasksTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `don't fail if dependencies are the same, but with different DSL`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain {
                            dependencies {
                                api("androidx.lifecycle:lifecycle-common:2.10.0")
                                implementation("com.example:tool:1.5.0")
                                implementation("com.example:extra:1.0.0")
                            }
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `supports Kotlin build scripts`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        val commonMain by getting {
                            dependencies {
                                api("androidx.lifecycle:lifecycle-common:2.10.0")
                            }
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        val commonMain by getting {
                            dependencies {
                                api("androidx.lifecycle:lifecycle-common:2.10.0")
                            }
                        }
                    }
                }
            """,
            scriptExtension = "gradle.kts",
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `allows identical dependency declarations with arbitrary versions`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.compose.runtime:runtime-retain:${'$'}composeVersion")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.compose.runtime:runtime-retain:${'$'}composeVersion")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `fail if missing dependency`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
        assertThat(result.output).contains("$PROJECT_PATH:jbUpdateForkDependencies")
    }

    @Test
    fun `updates fork dependencies for mismatched source sets`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbUpdateForkDependencies")
        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(projectDir(root).resolve("build-fork.gradle").readText()).isEqualTo(
            """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `updates fork dependencies without replacing compatible forked groups`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:2.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.10.3")
                            implementation("com.example:tool:1.0.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbUpdateForkDependencies")

        assertThat(projectDir(root).resolve("build-fork.gradle").readText()).isEqualTo(
            """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.10.3")
                            implementation("com.example:tool:2.0.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `updates only incompatible fork dependency versions`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.11.0")
                            implementation("org.jetbrains.lifecycle:lifecycle-runtime:2.9.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbUpdateForkDependencies")

        assertThat(projectDir(root).resolve("build-fork.gradle").readText()).isEqualTo(
            """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.11.0")
                            implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `allows suppressing fork dependency verification for a source set`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        // jbVerifyForkDependencies: suppress
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbUpdateForkDependencies")
        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(projectDir(root).resolve("build-fork.gradle").readText()).isEqualTo(
            """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `fail if wrong type`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `fail if wrong order`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:extra:1.0.0")
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `fail if additional dependency`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `don't fail if version is greater`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.11.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `fail if version is lower`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `don't fail if fork uses project`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api(project(":lifecycle:lifecycle-common"))
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `fail if fork uses artifact instead of project`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api(project(":lifecycle:lifecycle-common"))
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `don't fail for forked group if version is compatible`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.10.3")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `fail for forked group if version is lower`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.lifecycle:lifecycle-common:2.9.3")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `fail for other matching source sets`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        val result = gradleAndFail(root, "$PROJECT_PATH:jbVerifyForkDependencies")

        assertThat(result.output).contains("Fork dependencies are out of date.")
    }

    @Test
    fun `don't fail for non-existing source sets`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        desktopMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.9.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    @Test
    fun `don't fail if build-fork doesn't exist`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
                            api("androidx.lifecycle:lifecycle-common:2.10.0")
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = null,
        )

        gradle(root, "$PROJECT_PATH:jbVerifyForkDependencies")
    }

    private fun createProject(
        original: String,
        fork: String?,
        scriptExtension: String = "gradle",
    ): File {
        val root = temporaryFolder.newFolder()
        val pluginClasspath = pluginClasspath()
            .joinToString(",\n                        ") { "\"${it.invariantSeparatorsPath}\"" }
        root.resolve("gradle.properties").writeText(
            """
                androidx.studio.type=jetbrains-fork
            """.trimIndent()
        )
        // Use empty.gradle so test build.gradle isn't included into the build,
        // just parsed. This allows not bothering writing a fully correct DSL in the tests
        root.resolve("settings.gradle").writeText(
            """
                include("$PROJECT_PATH")
                project("$PROJECT_PATH").buildFileName = "empty.gradle"
            """.trimIndent()
        )
        root.resolve("build.gradle").writeText(
            """
                import org.jetbrains.androidx.build.JetBrainsAndroidXRootImplPlugin
                import org.jetbrains.androidx.build.JetBrainsAndroidXImplPlugin

                buildscript {
                    dependencies {
                        classpath(files([$pluginClasspath]))
                    }
                }

                allprojects {
                    ext.supportRootFolder = rootDir
                }
                apply plugin: JetBrainsAndroidXRootImplPlugin
                project("$PROJECT_PATH").apply plugin: JetBrainsAndroidXImplPlugin
            """.trimIndent()
        )

        val projectDir = projectDir(root)
        projectDir.mkdirs()
        projectDir.resolve("empty.gradle").writeText("")
        projectDir.resolve("build.$scriptExtension").writeText(original.trimIndent())
        if (fork != null) {
            forkFile(root, scriptExtension).writeText(fork.trimIndent())
        }
        return root
    }
}

private const val PROJECT_PATH = ":test-project"

private fun gradle(root: File, vararg arguments: String): BuildResult =
    runner(root, *arguments).build()

private fun gradleAndFail(root: File, vararg arguments: String): BuildResult =
    runner(root, *arguments).buildAndFail()

private fun runner(root: File, vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withProjectDir(root)
        .withArguments(arguments.toList() + "--stacktrace")

private fun pluginClasspath(): List<File> {
    val classpath = File(System.getProperty("test.plugin.classpath.file")).readText()
    return classpath.split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .map(::File)
}

private fun projectDir(root: File): File =
    root.resolve(PROJECT_PATH.removePrefix(":").replace(':', File.separatorChar))

private fun forkFile(root: File, scriptExtension: String = "gradle"): File =
    projectDir(root).resolve("build-fork.$scriptExtension")
