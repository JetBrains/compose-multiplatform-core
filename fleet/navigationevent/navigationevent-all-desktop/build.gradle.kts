/*
 * Copyright 2025 The Android Open Source Project
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
    val originalNavigationEventVersion = properties["artifactRedirection.version.androidx.navigationevent"]
    splitPackageModule("androidx.navigationevent:navigationevent:$originalNavigationEventVersion")
    splitPackageModule("androidx.navigationevent:navigationevent-compose:$originalNavigationEventVersion")

    dependency(libs.kotlinStdlib)
    dependency(libs.kotlinCoroutinesCore)
    dependency(libs.androidx.annotation)
    dependency(project(":compose:runtime:runtime"))
}

configure<PublishingExtension> {
    publications.withType<MavenPublication> {
        groupId = "org.jetbrains.androidx.navigationevent"
        version = properties["jetbrains.publication.version.NAVIGATION_EVENT"] as String?
                ?: "0.0.0-SNAPSHOT"
    }
}
