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

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow")
    id("JetbrainsUnsplitPackagePlugin")
}

unsplitPackage {
    splitPackageModule(project(":compose:ui:ui"))
    splitPackageModule(project(":compose:ui:ui-backhandler"))
    splitPackageModule(project(":compose:ui:ui-geometry"))
    splitPackageModule(project(":compose:ui:ui-graphics"))
    splitPackageModule(project(":compose:ui:ui-text"))
    splitPackageModule(project(":compose:ui:ui-unit"))
    splitPackageModule(project(":compose:ui:ui-util"))

    dependency(libs.androidx.annotation)
    dependency("androidx.collection:collection:1.5.0")
    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.kotlinSerializationJson)

    dependency(libs.skiko)
    dependency(libs.atomicFu)
    dependency("org.jetbrains.kotlinx:kotlinx-io-core-jvm:${libs.versions.kotlinxIo.get()}")

    dependency(project(":compose:runtime:runtime"))
    dependency(project(":compose:runtime:runtime-retain"))
    dependency(project(":compose:runtime:runtime-saveable"))
    dependency(project(":savedstate:savedstate-compose"))
    dependency(project(":lifecycle:lifecycle-common"))
    dependency(project(":lifecycle:lifecycle-runtime"))
    dependency(project(":lifecycle:lifecycle-runtime-compose"))
    dependency(project(":lifecycle:lifecycle-viewmodel"))
    dependency(project(":lifecycle:lifecycle-viewmodel-savedstate"))
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.compose.ui"
        version = providers.environmentVariable("COMPOSE_CUSTOM_VERSION").getOrNull()
            ?: properties["jetbrains.publication.version.COMPOSE"] as String?
                ?: "0.0.0-SNAPSHOT"
    }
}
