package androidx.build

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val SHADOW_PLUGIN_ID = "com.gradleup.shadow"

class JetbrainsUnsplitPackagePlugin @Inject constructor(
    private val archiveOperations: ArchiveOperations,
) : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<DefaultUnsplitPackageExtension>("unsplitPackage")
        configureShadowJar(project, extension)
        val sourcesJar = registerSourcesJar(project, extension)
        configurePublishing(project, extension, sourcesJar)
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

    private fun registerSourcesJar(
        project: Project,
        extension: DefaultUnsplitPackageExtension
    ): TaskProvider<Jar> {
        val sourceJars =
            extension.unsplitPackageSources.incoming.artifactView { it.isLenient = true }.files
        return project.tasks.register<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            from(sourceJars.elements.map { locations -> locations.map { archiveOperations.zipTree(it) } })
        }
    }

    private fun configurePublishing(
        project: Project,
        extension: DefaultUnsplitPackageExtension,
        sourcesJar: TaskProvider<Jar>,
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
                        // Needs to be called "desktop" to be picked up by
                        // publishComposeJb[ToMavenLocal] from AbstractComposePublishingTask
                        publicationContainer.register<MavenPublication>("desktop") {
                            from(project.components.findByName("shadow"))
                            artifact(sourcesJar)
                        }
                        // Needs to be present and called "kotlinMultiplatform" to satisfy
                        // publishComposeJb[ToMavenLocal] from AbstractComposePublishingTask
                        publicationContainer.register<MavenPublication>("kotlinMultiplatform") {}
                    }
                    publishingExtension.repositories { repositoryHandler ->
                        repositoryHandler.maven { mavenArtifactRepository ->
                            mavenArtifactRepository.setUrl(project.getRepositoryDirectory())
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

    internal val unsplitPackageSources =
        configurationContainer.resolvable("unsplitPackageSources") {
            it.extendsFrom(unsplitPackageDependencies.get())
            it.attributes { attributes ->
                attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objectFactory.named(Usage.JAVA_RUNTIME),
                )
                attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objectFactory.named(Category.DOCUMENTATION),
                )
                attributes.attribute(
                    DocsType.DOCS_TYPE_ATTRIBUTE,
                    objectFactory.named(DocsType.SOURCES),
                )
                attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objectFactory.named(LibraryElements.JAR),
                )
                attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
            }
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