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
    splitPackageModule(project(":compose:runtime:runtime"))
    splitPackageModule(project(":compose:runtime:runtime-annotation"))
    splitPackageModule(project(":compose:runtime:runtime-retain"))
    splitPackageModule(project(":compose:runtime:runtime-saveable"))

    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.androidx.annotation)
    dependency("androidx.collection:collection:1.5.0")
    dependency(libs.atomicFu)
    dependency(project(":fleet:lifecycle:lifecycle-all-desktop"))
    dependency("androidx.savedstate:savedstate-compose:${redirectVersions.get("androidx.savedstate")}")
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.fleet.compose.runtime"
        version = forkPublicationVersion("COMPOSE")
    }
}
