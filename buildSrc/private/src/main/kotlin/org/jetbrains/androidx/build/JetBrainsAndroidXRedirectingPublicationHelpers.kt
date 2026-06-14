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

package org.jetbrains.androidx.build

import com.android.utils.mapValuesNotNull
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier

/**
 * Build a `fork-coordinate -> androidx-coordinate` map used to rewrite published POM dependencies
 * (see [modifyPomDependencies]). The fork publishes under `org.jetbrains.*` group ids that redirect
 * to `androidx.*`; this discovers, per resolved first-level dependency, the `androidx.*` module it
 * ultimately resolves to so the POM can reference the real coordinate.
 *
 * Workaround for
 * https://youtrack.jetbrains.com/issue/CMP-7764/Redirection-of-artifacts-breaks-poms-for-multiplatform-libraries-that-use-them
 * After it is resolved, this shouldn't be needed.
 */
internal fun Project.originalToRedirectedDependency(
    componentName: String
): Map<ModuleIdentifier, ModuleVersionIdentifier> {
    /**
     * Find a redirect to another group and version.
     *
     * Use heuristic method that compares modules names. Example:
     *   [first-level-dependency] org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 ->
     *   [artifact-with-the-same-name] androidx.lifecycle:lifecycle-runtime:2.8.5 ->
     *   [artifact-with-the-same-name-plus-suffix] androidx.lifecycle:lifecycle-runtime-desktop:2.8.5
     *
     * The first dependency redirects to the last one.
     */
    fun ResolvedDependency.findRedirectedDependencyHeuristically() =
        children
            .find { it.moduleName == moduleName }
            ?.children
            // don't check `it.moduleName == "moduleName-$target"` here,
            // as it can be resolved to any other suitable target
            // (for example, to jvm, or any other custom)
            ?.find { it.moduleName.startsWith(moduleName) }

    fun mainConfiguration() =
        configurations.find { it.name == "${componentName}RuntimeClasspath" } ?:
        configurations.find { it.name == "${componentName}CompileKlibraries" }!!

    /**
     * Extract redirections for dependencies using heuristic method (for both project, and external)
     *
     * Example for compose:ui
     * org.jetbrains.androidx.lifecycle:lifecycle-common=androidx.lifecycle:lifecycle-common-jvm:2.8.5
     * org.jetbrains.androidx.lifecycle:lifecycle-runtime=androidx.lifecycle:lifecycle-runtime-desktop:2.8.5
     * org.jetbrains.androidx.lifecycle:lifecycle-viewmodel=androidx.lifecycle:lifecycle-viewmodel-desktop:2.8.5
     */
    return mainConfiguration()
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .orEmpty()
        .associateBy { DefaultModuleIdentifier.newId(it.moduleGroup, it.moduleName) }
        .mapValuesNotNull { it.value.findRedirectedDependencyHeuristically()?.module?.id }
}
