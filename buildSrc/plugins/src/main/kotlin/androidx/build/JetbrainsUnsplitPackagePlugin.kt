/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.build

import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.util.Node
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

class JetbrainsUnsplitPackagePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java")
        project.plugins.apply("maven-publish")
        project.plugins.apply("com.gradleup.shadow")

        val extension = project.extensions.create<UnsplitPackageExtension>("unsplitPackage")

        project.afterEvaluate {
            configureDependencies(it, extension.splitPackageModules)
            configureShadowJar(it)
            configurePublishing(it, extension.dependencies)
        }
    }

    private fun configureDependencies(
        project: Project,
        splitPackageModules: List<ModuleDependency>
    ) {
        project.dependencies {
            splitPackageModules.forEach { add("implementation", it) }
        }
    }

    private fun configureShadowJar(project: Project) {
        project.tasks.named<ShadowJar>("shadowJar") {
            configurations = listOf(project.configurations.getByName("compileClasspath"))
            manifest.attributes(
                mapOf(
                    "Class-Path" to object {
                        override fun toString(): String =
                            project.configurations.getByName("compileClasspath")
                                .files.joinToString(" ") { it.name }
                    }
                )
            )
            archiveClassifier.set("")
        }
        project.tasks.named("jar") { it.enabled = false }
    }

    private fun configurePublishing(project: Project, dependencies: List<ModuleDependency>) {
        project.extensions.configure<PublishingExtension>("publishing") { publishingExtension ->
            publishingExtension.publications { publicationContainer ->
                publicationContainer.create<MavenPublication>("unsplitPackage") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()

                    project.extensions.getByType<ShadowExtension>()
                        .component(this)

                    pom.withXml { xmlProvider ->
                        val root = xmlProvider.asNode()
                        root.children()
                            .filterIsInstance<Node>()
                            .filter { it.name() == "dependencies" }
                            .forEach { root.remove(it) }

                        root.appendNode("dependencies").apply {
                            dependencies.forEach {
                                appendNode("dependency").apply {
                                    appendNode("groupId", it.group)
                                    appendNode("artifactId", it.name)
                                    it.version?.let { version ->
                                        appendNode(
                                            "version",
                                            version
                                        )
                                    }
                                    appendNode("scope", "compile")
                                }
                            }

                            project
                                .configurations
                                .getByName("runtimeOnly")
                                .allDependencies
                                .filter { it.group != null && it.version != null }
                                .forEach {
                                    appendNode("dependency").apply {
                                        appendNode("groupId", it.group!!)
                                        appendNode("artifactId", it.name)
                                        appendNode("version", it.version!!)
                                        appendNode("scope", "runtime")
                                    }
                                }
                        }
                    }
                }
            }
            publishingExtension.repositories { repositoryHandler ->
                repositoryHandler.mavenLocal()
            }
        }
    }
}

open class UnsplitPackageExtension internal constructor(private val dependencyFactory: DependencyFactory) {
    internal val splitPackageModules = mutableListOf<ModuleDependency>()
    internal val dependencies = mutableListOf<ModuleDependency>()

    fun splitPackageModule(dependencyProvider: Provider<MinimalExternalModuleDependency>) {
        splitPackageModules.add(dependencyProvider.get().apply { isTransitive = false })
    }

    fun splitPackageModule(project: Project) {
        splitPackageModules.add(dependencyFactory.create(project).apply { isTransitive = false })
    }

    fun splitPackageModule(dependencyNotation: CharSequence) {
        splitPackageModules.add(
            dependencyFactory.create(dependencyNotation).apply { isTransitive = false }
        )
    }

    fun dependency(dependencyProvider: Provider<MinimalExternalModuleDependency>) {
        dependencies.add(dependencyProvider.get())
    }

    fun dependency(project: Project) {
        dependencies.add(dependencyFactory.create(project))
    }

    fun dependency(dependencyNotation: CharSequence) {
        dependencies.add(dependencyFactory.create(dependencyNotation))
    }
}
