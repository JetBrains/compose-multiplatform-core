pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
    }
}

dependencyResolutionManagement {
    // Gradle imports gradle/libs.versions.toml into this catalog automatically.
    versionCatalogs {
        create("libs") {
            providers.gradleProperty("benchmarkKotlinVersion").orNull?.let {
                version("kotlin", it)
            }
            providers.gradleProperty("benchmarkComposeVersion").orNull?.let {
                version("compose-multiplatform", it)
            }
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
    }
}

include(":benchmarks-runtime")
include(":benchmarks-report")
include(":benchmarks-collector")
include(":benchmarks-launcher")
include(":benchmarks-app")

val composeCorePath = providers.gradleProperty("composeCorePath").orNull?.takeIf { it.isNotBlank() }
if (composeCorePath != null) {
    includeBuild(composeCorePath) {
        dependencySubstitution {
            // Explicit mappings are needed because core's project groups differ from Maven groups.
            val components = mapOf(
                "animation" to listOf("animation", "animation-core", "animation-graphics"),
                "foundation" to listOf("foundation", "foundation-layout"),
                "material" to listOf("material", "material-navigation", "material-ripple"),
                "runtime" to listOf("runtime", "runtime-saveable"),
                "ui" to listOf(
                    "ui", "ui-geometry", "ui-backhandler", "ui-graphics", "ui-skiko", "ui-text",
                    "ui-tooling", "ui-tooling-data", "ui-tooling-preview", "ui-uikit", "ui-unit", "ui-util",
                ),
                "desktop" to listOf("desktop"),
            )
            components.forEach { (group, names) ->
                names.forEach { artifact ->
                    substitute(module("org.jetbrains.compose.$group:$artifact"))
                        .using(project(":compose:$group:$artifact"))
                }
            }
        }
    }
}
