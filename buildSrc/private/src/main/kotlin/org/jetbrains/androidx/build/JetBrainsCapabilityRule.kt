/*
 * Copyright 2026 The Android Open Source Project
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

import org.gradle.api.Project
import org.gradle.api.artifacts.CapabilityResolutionDetails
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

private const val ANDROIDX_GROUP_PREFIX = "androidx."

/**
 * Gradle component metadata rule that adds capabilities to resolve conflicts between
 * forked org.jetbrains.androidx.* artifacts and original androidx.* artifacts.
 *
 * This ensures that when both variants exist in the dependency graph, Gradle can
 * properly resolve the conflict, with project references taking precedence over
 * external dependencies.
 */
private class JetBrainsCapabilityRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext): Unit = context.details.run {
        if (!id.group.startsWith(ANDROIDX_GROUP_PREFIX)) return
        val group = id.group.substring(ANDROIDX_GROUP_PREFIX.length).replace(".",":")

        // Do not customize capabilities for not published artifacts
        if (!JetBrainsPublication.shouldPublish(":$group:${id.name}")) return

        // Add capability with a common resolver group to enable conflict resolution
        allVariants { variant ->
            variant.withCapabilities {
                // Use implicit declaration
            }
        }
    }
}

/**
 * Configures capability resolution for JetBrains and AndroidX projects in a Gradle build.
 * It ensures correct dependency resolution by handling conflicts between `androidx.*` and `org.jetbrains.*` artifacts.
 */
fun Project.configureJetBrainsCapabilityResolution() {
    // Register the component metadata rule globally for external dependencies
    dependencies.components.all(JetBrainsCapabilityRule::class.java)

    // Configure capability resolution for all projects
    configurations.configureEach { configuration ->

        // https://github.com/gradle/gradle/issues/35943 workaround
        if (path.contains("integration-tests") || path.contains("samples")) {
            configuration.resolutionStrategy.dependencySubstitution {
                it.substitute(it.module("androidx.compose.ui:ui"))
                    .using(it.project(":compose:ui:ui"))
            }
        }

        configuration.resolutionStrategy.capabilitiesResolution.all { details ->
            if (details.capability.group.startsWith(ANDROIDX_GROUP_PREFIX)) {
                details.selectPreferredAndroidXCandidate()
            }
        }
    }
}

fun Project.configureRedirectionCapability() {
    if (!JetBrainsPublication.shouldPublish(this)) return
    val redirection = artifactRedirection() ?: return
    if (redirection.targetNames.isEmpty()) return

    // Configure resolution strategy to handle all capability conflicts
    configurations.configureEach { configuration ->
        if (configuration.isCanBeConsumed) {
            // It's important to declare the implicit capability explicitly because once you define
            // any explicit capability, all capabilities must be declared, including the implicit one.
            configuration.outgoing.capability("$group:$name:$version")

            // Add the androidx.* capability in addition to the implicit project capability
            configuration.outgoing.capability("${redirection.groupId}:$name:${redirection.defaultVersion}")
        }
    }
}

private fun CapabilityResolutionDetails.selectPreferredAndroidXCandidate() {
    // Only intervene if there are multiple candidates
    if (candidates.size <= 1) {
        return
    }

    // Priority order: 1) Project reference, 2) org.jetbrains.*, 3) androidx.*
    val projectCandidate = candidates.firstOrNull { candidate ->
        candidate.id is ProjectComponentIdentifier
    }
    if (projectCandidate != null) {
        // Project reference always wins over external dependencies
        select(projectCandidate)
        return
    }
    
    // Prefer org.jetbrains.* over androidx.*
    val jetbrainsCandidate = candidates.firstOrNull { candidate ->
        val candidateId = candidate.id
        if (candidateId is ModuleComponentIdentifier) {
            candidateId.group.startsWith("org.jetbrains.")
        } else {
            false
        }
    }
    if (jetbrainsCandidate != null) {
        select(jetbrainsCandidate)
        return
    }

    // Let Gradle use its default resolution
}
