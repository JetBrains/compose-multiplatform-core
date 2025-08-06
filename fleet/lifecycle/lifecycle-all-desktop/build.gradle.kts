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
    id("JetbrainsUnsplitPackagePlugin")
}

buildscript {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/internal")
        maven("https://maven.pkg.jetbrains.space/public/p/space/maven")
        mavenLocal()
    }
}

unsplitPackage {
    val lifecycleVersion =
        project.providers.gradleProperty("artifactRedirection.version.androidx.lifecycle").get()
    splitPackageModule("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
    splitPackageModule("androidx.lifecycle:lifecycle-runtime:$lifecycleVersion")
    splitPackageModule(project(":lifecycle:lifecycle-runtime-compose"))
//    splitPackageModule(project(":lifecycle:lifecycle-runtime-ktx"))
    splitPackageModule("androidx.lifecycle:lifecycle-viewmodel:$lifecycleVersion")
    splitPackageModule(project(":lifecycle:lifecycle-viewmodel-compose"))
//    splitPackageModule(project(":lifecycle:lifecycle-viewmodel-navigation3"))
    splitPackageModule("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion")

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
//    dependency(project(":savedstate:savedstate-compose"))
    dependency(libs.jspecify)
}

group = "org.jetbrains.androidx.lifecycle"
version = project.providers.environmentVariable("LIFECYCLE_CUSTOM_VERSION")
    .orElse("0.0.0-SNAPSHOT")
    .get()
