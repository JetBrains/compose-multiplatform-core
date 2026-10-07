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

import org.jetbrains.androidx.build.forkPublicationVersion

import org.jetbrains.androidx.build.registerRedirectVersionsExtension

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow")
    id("JetbrainsUnsplitPackagePlugin")
}

// The upstream `androidx.*` version of savedstate, which this aggregate's dependencies name. Read
// through the redirect registry because the fork no longer builds savedstate from source.
// Registered explicitly because this is a plain java/shadow project: it does not apply
// JetBrainsAndroidXImplPlugin, which is what registers the extension for AndroidX modules.
registerRedirectVersionsExtension()
val redirectVersions = extensions.getByType<org.jetbrains.androidx.build.RedirectVersions>()

unsplitPackage {
    splitPackageModule(project(":compose:ui:ui"))
    splitPackageModule(project(":compose:ui:ui-backhandler"))
    splitPackageModule(project(":compose:ui:ui-geometry"))
    splitPackageModule(project(":compose:ui:ui-graphics"))
    splitPackageModule(project(":compose:ui:ui-skiko"))
    splitPackageModule(project(":compose:ui:ui-text"))
    splitPackageModule(project(":compose:ui:ui-unit"))
    splitPackageModule(project(":compose:ui:ui-util"))

    dependency(libs.androidx.annotation)
    // The version ui and foundation declare; runtime-all-desktop names its own, older one.
    dependency("androidx.collection:collection:1.6.0")
    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.kotlinSerializationJson)

    dependency(libs.skiko.asProvider())
    dependency(libs.atomicFu)
    dependency("org.jetbrains.kotlinx:kotlinx-io-core-jvm:${libs.versions.kotlinxIo.get()}")

    dependency(project(":compose:runtime:runtime"))
    dependency(project(":compose:runtime:runtime-retain"))
    dependency(project(":compose:runtime:runtime-saveable"))
    dependency("androidx.savedstate:savedstate-compose:${redirectVersions.get("androidx.savedstate")}")
    // Fleet loads lifecycle from this one aggregate, which carries all five modules.
    dependency(project(":fleet:lifecycle:lifecycle-all-desktop"))
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.fleet.compose.ui"
        version = forkPublicationVersion("COMPOSE")
    }
}
