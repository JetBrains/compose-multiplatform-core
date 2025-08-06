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
    splitPackageModule(project(":compose:ui:ui"))
    splitPackageModule(project(":compose:ui:ui-backhandler"))
    splitPackageModule(project(":compose:ui:ui-geometry"))
    splitPackageModule(project(":compose:ui:ui-graphics"))
    splitPackageModule(project(":compose:ui:ui-text"))
    splitPackageModule(project(":compose:ui:ui-unit"))
    splitPackageModule(project(":compose:ui:ui-util"))

    dependency(libs.androidx.annotation)
    dependency(libs.androidx.collection)
    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinStdlibJdk8)
    dependency(libs.kotlinCoroutinesCore)

    dependency(libs.skikoCommon)
    dependency(libs.atomicFu)

    dependency(project(":compose:runtime:runtime"))
    dependency(project(":compose:runtime:runtime-saveable"))
    dependency(project(":lifecycle:lifecycle-common"))
    dependency(project(":lifecycle:lifecycle-runtime"))
    dependency(project(":lifecycle:lifecycle-runtime-compose"))
    dependency(project(":lifecycle:lifecycle-viewmodel"))
    dependency(project(":lifecycle:lifecycle-viewmodel-savedstate"))
}

group = "org.jetbrains.compose.ui"
version = project.providers.environmentVariable("COMPOSE_CUSTOM_VERSION")
    .orElse("0.0.0-SNAPSHOT")
    .get()
