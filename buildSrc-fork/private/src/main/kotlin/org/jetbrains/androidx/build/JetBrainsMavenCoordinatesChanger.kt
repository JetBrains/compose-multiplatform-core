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

package org.jetbrains.androidx.build

import androidx.build.Version
import org.gradle.api.Project

fun Project.changeMavenCoordinatesToJetBrains() {
    // Every project gets JetBrains coordinates, not only what we publish: an unpublished module
    // (e.g. navigation-testing) would otherwise resolve against redirected artifacts.
    val component = JetBrainsPublication.projectPathToComponent[path]

    val group = JetBrainsPublication.mavenGroupFor(path)
    // An unpublished module keeps upstream's placeholder version; it is never released, it only
    // needs JetBrains coordinates so it resolves against its sibling projects.
    val version = Version(component?.let { forkPublicationVersion(it.library()) } ?: "9999.0.0-SNAPSHOT")
    this.group = group
    this.version = version

    afterEvaluate {
        check(this.group == group) {
            "The $path group is changed after evaluation from $group to ${this.group}. Check if it is overridden inside build.gradle and remove it"
        }
        check(this.version == version) {
            "The $path version is changed after evaluation from $version to ${this.version}. Check if it is overridden inside build.gradle and remove it"
        }
    }
}
