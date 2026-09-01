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
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForkDependenciesTasksTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `adds and removes dependencies`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
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
                            implementation("com.example:extra:1.0.0")
                            api("androidx.compose.ui:ui:1.1.1")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `keeps catalog dependencies`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(libs.kotlinSerializationCore)
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(libs.kotlinSerializationCore)
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `updates dependency version`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.compose.ui:ui:2.10.2")
                            implementation("somelib:animation:1.2.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.compose.ui:ui:2.9.3")
                            implementation("somelib:animation:1.3.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.compose.ui:ui:2.10.2")
                            implementation("somelib:animation:1.3.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `updates dependency type`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `does not change an associated fork artifact version or project, but changes type`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("androidx.compose.ui:ui:1.4.1")
                            implementation("androidx.compose.material3:material3:1.4.1")
                            implementation(project(":compose:material:material"))
                            implementation("com.example:tool:2.0.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("org.jetbrains.compose.ui:ui:1.2.0")
                            api(project(":compose:material3:material3"))
                            api("org.jetbrains.compose.material:material:1.2.0")
                            implementation("com.example:tool:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.compose.ui:ui:1.2.0")
                            implementation(project(":compose:material3:material3"))
                            implementation("org.jetbrains.compose.material:material:1.2.0")
                            implementation("com.example:tool:2.0.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `works if fork build file is absent`() {
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

        verifyThenUpdate(root)
    }

    @Test
    fun `works with Kotlin build scripts`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        val commonMain by getting {
                            dependencies {
                                api("androidx.compose.ui:ui:2.10.0")
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
                                api("androidx.compose.ui:ui:2.9.0")
                            }
                        }
                    }
                }
            """,
            scriptExtension = "gradle.kts",
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        val commonMain by getting {
                            dependencies {
                                api("androidx.compose.ui:ui:2.10.0")
                            }
                        }
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `works when DSL syntax differs`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain {
                            dependencies {
                                // Tool dependency
                                implementation("com.example:tool:1.6.0")

                                // Extra dependency
                                implementation("com.example:extra:1.0.0")
                            }
                        }
                    }
                }
            """,
            fork = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // Tool dependency
                            implementation("com.example:tool:1.5.0")

                            // Extra dependency
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // Tool dependency
                            implementation("com.example:tool:1.6.0")

                            // Extra dependency
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `works when a version isn't parseable`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("somelib:somelib:${'$'}composeVersion")
                            api("somelib:somelib2:${'$'}composeVersion")
                            implementation("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("somelib:somelib2:${'$'}composeVersion")
                            implementation("com.example:tool:1.3.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("somelib:somelib:${'$'}composeVersion")
                            api("somelib:somelib2:${'$'}composeVersion")
                            implementation("com.example:tool:1.4.0")
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `works if using anything else from androidXMultiplatform`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
            fork = """
                anythingElse {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                anythingElse {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `does not update dependencies when dependency verification is suppressed`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                            api("somelib:somelib2:1.2.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // jbVerifyForkDependencies: suppress
                            implementation("com.example:extra:1.0.0")

                            // jbVerifyForkDependencies: suppress
                            api("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            api("somelib:somelib2:1.2.0")

                            // jbVerifyForkDependencies: suppress
                            implementation("com.example:extra:1.0.0")

                            // jbVerifyForkDependencies: suppress
                            api("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `does not update dependencies when sourceSet verification is suppressed`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        // jbVerifyForkDependencies: suppress
                        commonMain.dependencies {
                            implementation("com.example:tool:1.4.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(root)
    }

    @Test
    fun `updates dependencies for non-commonMain matching source sets`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
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
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
                            implementation("com.example:tool:1.5.0")
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `does not update dependencies for non-matching source sets`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmMain.dependencies {
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
                            implementation("com.example:extra:1.0.0")
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(root)
    }

    @Test
    fun `preserves spaces and comments for the same group and module`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // UI library
                            api("androidx.compose.ui:ui:1.9.3")

                            // Tool dependency
                            // Version 2.0.0
                            implementation("com.example:tool:2.0.0") // original comment

                            // Material library
                            api("androidx.compose.material:material:1.9.3")
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // Tool 1.0.0
                            implementation("com.example:tool:1.0.0") // fork comment



                            // Material library
                            implementation("org.jetbrains.compose.material:material:1.8.0")


                            // UI Project
                            api(project(":compose:ui:ui")) // fork comment
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        commonMain.dependencies {
                            // UI Project
                            api(project(":compose:ui:ui")) // fork comment

                            // Tool dependency
                            // Version 2.0.0
                            implementation("com.example:tool:2.0.0") // original comment

                            // Material library
                            api("org.jetbrains.compose.material:material:1.8.0")
                        }
                    }
                }
            """,
        )
    }

    @Test
    fun `fills an empty block`() {
        val root = createProject(
            original = """
                androidXMultiplatform {
                    sourceSets {
                        jvmAndAndroidMain.dependencies {
                            api(libs.jspecify)
                        }
                    }
                }
            """,
            fork = """
                androidXForkMultiplatform {
                    sourceSets {
                        jvmAndAndroidMain.dependencies {
                        }
                    }
                }
            """,
        )

        verifyThenUpdate(
            root,
            expected = """
                androidXForkMultiplatform {
                    sourceSets {
                        jvmAndAndroidMain.dependencies {
                            api(libs.jspecify)
                        }
                    }
                }
            """,
        )
    }

    private fun createProject(
        original: String,
        fork: String?,
        scriptExtension: String = "gradle",
    ): File {
        val root = temporaryFolder.newFolder()
        val pluginClasspath = pluginClasspath()
            .joinToString(",\n                        ") { "\"${it.invariantSeparatorsPath}\"" }

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

private fun verifyThenUpdate(
    root: File,
    expected: String? = null,
) {
    val forkFile = projectDir(root).listFiles()
        ?.singleOrNull { it.name.startsWith("build-fork.") }
    val expectedFork = expected?.trimIndent() ?: forkFile?.readText()

    if (expected != null) {
        val result = runner(root, "$PROJECT_PATH:jbVerifyForkDependencies").buildAndFail()
        assertThat(result.output).contains("Fork dependencies are out of date.")
        assertThat(result.output).contains("jbUpdateForkDependencies")
    } else {
        runner(root, "$PROJECT_PATH:jbVerifyForkDependencies").build()
    }
    runner(root, "$PROJECT_PATH:jbUpdateForkDependencies").build()
    runner(root, "$PROJECT_PATH:jbVerifyForkDependencies").build()
    if (expectedFork != null) {
        assertThat(forkFile?.readText()).isEqualTo(expectedFork)
    }
}

private fun runner(root: File, vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withProjectDir(root)
        .withArguments(arguments.toList() + "--stacktrace")

private fun pluginClasspath(): List<File> {
    val classpath = System.getProperty("java.class.path")
    return classpath.split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .map(::File)
}

private fun projectDir(root: File): File =
    root.resolve(PROJECT_PATH.removePrefix(":").replace(':', File.separatorChar))

private fun forkFile(root: File, scriptExtension: String = "gradle"): File =
    projectDir(root).resolve("build-fork.$scriptExtension")
