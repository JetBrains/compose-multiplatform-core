pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        maven("https://cache-redirector.jetbrains.com/maven-central")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/maven-central")
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
        mavenCentral()
    }
}

rootProject.name = "skiko-repro-cmp-8615"
