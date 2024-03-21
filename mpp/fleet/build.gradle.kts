/*
 * Copyright 2024 The Android Open Source Project
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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `maven-publish`
}

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/internal")
        maven("https://maven.pkg.jetbrains.space/public/p/space/maven")
    }
}


val modulesToBundle =
    listOf(
        "org.jetbrains.compose.runtime:runtime-saveable-desktop",
        "org.jetbrains.compose.animation:animation-desktop",
        "org.jetbrains.compose.animation:animation-core-desktop",
        "org.jetbrains.compose.foundation:foundation-desktop",
        "org.jetbrains.compose.foundation:foundation-layout-desktop",
        "org.jetbrains.compose.ui:ui-desktop",
        "org.jetbrains.compose.ui:ui-geometry-desktop",
        "org.jetbrains.compose.ui:ui-graphics-desktop",
        "org.jetbrains.compose.ui:ui-text-desktop",
        "org.jetbrains.compose.ui:ui-unit-desktop",
        "org.jetbrains.compose.ui:ui-util-desktop",
    )


dependencies {
    for (moduleToBundle in modulesToBundle) {
        implementation(
            moduleToBundle + ":" + (project.providers.environmentVariable("COMPOSE_CUSTOM_VERSION").orNull
                ?: "0.0.0-SNAPSHOT")
        )
    }
    implementation("androidx.collection:collection-jvm:1.4.0")
    implementation("androidx.annotation:annotation-jvm:1.7.1")
}

val shadowJar = tasks.register("shadowJar", ShadowJar::class) {
    configurations = listOf(project.configurations.compileClasspath.get())
    dependencies {
        include {
            "${it.moduleGroup}:${it.moduleName}" in modulesToBundle || it.moduleGroup.startsWith(
                "androidx.collection"
            ) || it.moduleGroup.startsWith("androidx.annotation")
        }
    }
    archiveFileName.set("compose-ui-foundation-animation-desktop.jar")
}

publishing {
    publications {
        publications {
            create<MavenPublication>("maven") {
                groupId = "org.jetbrains.compose"
                artifactId = "compose-ui-foundation-animation-desktop"
                version = (project.providers.environmentVariable("COMPOSE_CUSTOM_VERSION").orNull
                    ?: "0.0.0-SNAPSHOT")
                artifacts.artifact(shadowJar.map { it.archiveFile.get() })
                pom {

                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}