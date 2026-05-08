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
    splitPackageModule(project(":compose:runtime:runtime"))
    splitPackageModule(project(":compose:runtime:runtime-annotation"))
    splitPackageModule(project(":compose:runtime:runtime-retain"))
    splitPackageModule(project(":compose:runtime:runtime-saveable"))

    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.androidx.annotation)
    dependency("androidx.collection:collection:1.5.0")
    dependency(libs.atomicFu)
    dependency(project(":lifecycle:lifecycle-runtime-compose"))
    dependency(project(":savedstate:savedstate-compose"))
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.compose.runtime"
        version = providers.environmentVariable("COMPOSE_CUSTOM_VERSION").getOrNull()
            ?: properties["jetbrains.publication.version.COMPOSE"] as String?
                ?: "0.0.0-SNAPSHOT"
    }
}
