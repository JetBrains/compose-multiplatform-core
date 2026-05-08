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
    val originalLifecycleVersion = properties["artifactRedirection.version.androidx.lifecycle"]
    val originalSavedStateVersion = properties["artifactRedirection.version.androidx.savedstate"]
    splitPackageModule("androidx.lifecycle:lifecycle-common:$originalLifecycleVersion")
    splitPackageModule("androidx.lifecycle:lifecycle-runtime:$originalLifecycleVersion")
    splitPackageModule("androidx.lifecycle:lifecycle-runtime-compose:$originalLifecycleVersion")
//    splitPackageModule(project(":lifecycle:lifecycle-runtime-ktx"))
    splitPackageModule("androidx.lifecycle:lifecycle-viewmodel:$originalLifecycleVersion")
    splitPackageModule("androidx.lifecycle:lifecycle-viewmodel-compose:$originalLifecycleVersion")
//    splitPackageModule(project(":lifecycle:lifecycle-viewmodel-navigation3"))
    splitPackageModule("androidx.lifecycle:lifecycle-viewmodel-savedstate:$originalLifecycleVersion")
//    splitPackageModule("androidx.savedstate:savedstate-compose:$originalSavedStateVersion")

    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.kotlinSerializationCore)
    dependency(libs.androidx.annotation)
    dependency("androidx.arch.core:core-common:2.2.0")
//    dependency("androidx.collection:collection-ktx:1.4.5")
    dependency(project(":compose:runtime:runtime"))
    dependency(project(":compose:runtime:runtime-saveable"))
    dependency(project(":compose:ui:ui"))
//    dependency(project(":navigation:navigation3"))
    dependency(project(":savedstate:savedstate"))
    dependency(libs.jspecify)
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.androidx.lifecycle"
        version = properties["jetbrains.publication.version.LIFECYCLE"] as String?
                ?: "0.0.0-SNAPSHOT"
    }
}
