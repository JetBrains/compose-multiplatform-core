package androidx.build

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import java.net.URI
import javax.inject.Inject
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named

private const val SHADOW_PLUGIN_ID = "com.gradleup.shadow"

class JetbrainsUnsplitPackagePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<DefaultUnsplitPackageExtension>("unsplitPackage")
        configureShadowJar(project, extension)
        configurePublishing(project, extension)
    }

    private fun configureShadowJar(project: Project, extension: DefaultUnsplitPackageExtension) {
        project.pluginManager.withPlugin(SHADOW_PLUGIN_ID) {
            project.tasks.named<ShadowJar>("shadowJar") {
                configurations = listOf(extension.unsplitPackage)
                archiveClassifier.set("")
            }
        }
        project.tasks.named("jar") { it.enabled = false }
    }

    private fun configurePublishing(
        project: Project,
        extension: DefaultUnsplitPackageExtension
    ) {
        project.pluginManager.withPlugin(SHADOW_PLUGIN_ID) {
            val shadowComponent =
                project.components.findByName("shadow") as AdhocComponentWithVariants
            shadowComponent.addVariantsFromConfiguration(extension.unsplitPackageTransitives) {
                it.mapToMavenScope("runtime")
            }
        }

        project.pluginManager.withPlugin("maven-publish") {
            project.pluginManager.withPlugin(SHADOW_PLUGIN_ID) {
                project.extensions.configure<PublishingExtension>("publishing") { publishingExtension ->
                    publishingExtension.publications { publicationContainer ->
                        publicationContainer.register<MavenPublication>("unsplitPackage") {
                            from(project.components.findByName("shadow"))
                        }
                    }
                    publishingExtension.repositories { repositoryHandler ->
                        repositoryHandler.maven { mavenArtifactRepository ->
                            mavenArtifactRepository.url =
                                URI("https://maven.pkg.jetbrains.space/public/p/compose/dev")
                            mavenArtifactRepository.credentials { credentials ->
                                credentials.username = project.properties["publishingUsername"] as String?
                                credentials.password = project.properties["publishingPassword"] as String?
                            }
                        }
                        repositoryHandler.mavenLocal()
                    }
                }
            }
        }
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class UnsplitPackageDsl

/**
 * Makes the DSL extension expose [org.gradle.api.artifacts.dsl.DependencyHandler.project] functions
 */
@UnsplitPackageDsl
interface CanRequestProjectDependency {
    fun project(path: String, configuration: String? = null): ProjectDependency
    fun project(notation: Map<String, Any?>): ProjectDependency
}

@UnsplitPackageDsl
interface UnsplitPackageExtension : CanRequestProjectDependency {
    fun splitPackageModule(dependency: ModuleDependency)
    fun splitPackageModule(dependencyNotation: String)

    fun dependency(dependency: Dependency)
    fun dependency(dependencyNotation: String)
    fun dependency(dependency: Provider<out ModuleDependency>)
}

@Suppress("UnstableApiUsage")
internal open class DefaultUnsplitPackageExtension @Inject internal constructor(
    configurationContainer: ConfigurationContainer,
    private val dependencyHandler: DependencyHandler,
    private val objectFactory: ObjectFactory,
) : UnsplitPackageExtension {
    private val unsplitPackageDependencies =
        configurationContainer.dependencyScope("unsplitPackageDependencies")
    internal val unsplitPackage = configurationContainer.resolvable("unsplitPackage") {
        it.extendsFrom(unsplitPackageDependencies.get())
    }.get()

    private val unsplitPackageTransitivesDependencies =
        configurationContainer.dependencyScope("unsplitPackageTransitivesDependencies")
    internal val unsplitPackageTransitives =
        configurationContainer.resolvable("unsplitPackageTransitives") {
            it.attributes { attributes ->
                attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objectFactory.named(Usage.JAVA_RUNTIME),
                )
            }
            it.extendsFrom(unsplitPackageTransitivesDependencies.get())
        }.get()

    override fun splitPackageModule(dependency: ModuleDependency) {
        addSplitPackageModule(dependency)
    }

    override fun splitPackageModule(dependencyNotation: String) {
        addSplitPackageModule(dependencyHandler.create(dependencyNotation) as ModuleDependency)
    }

    private fun addSplitPackageModule(dependency: ModuleDependency) {
        unsplitPackageDependencies.configure { dependencyScopeConfiguration ->
            dependencyScopeConfiguration.withDependencies { dependencySet ->
                dependencySet.add(dependency.apply { isTransitive = false })
            }
        }
    }

    override fun dependency(dependency: Dependency) {
        addTransitive(dependency)
    }

    override fun dependency(dependencyNotation: String) {
        addTransitive(dependencyHandler.create(dependencyNotation))
    }

    override fun dependency(dependency: Provider<out ModuleDependency>) {
        addTransitive(dependency.get())
    }

    private fun addTransitive(dependency: Dependency) {
        unsplitPackageTransitivesDependencies.configure { dependencyScopeConfiguration ->
            dependencyScopeConfiguration.withDependencies { dependencySet ->
                dependencySet.add(dependency)
            }
        }
    }

    override fun project(path: String, configuration: String?): ProjectDependency =
        dependencyHandler.project(path, configuration)

    override fun project(notation: Map<String, Any?>): ProjectDependency =
        dependencyHandler.project(notation) as ProjectDependency
}