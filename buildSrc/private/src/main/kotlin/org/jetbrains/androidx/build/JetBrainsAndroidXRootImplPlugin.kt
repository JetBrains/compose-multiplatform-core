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

@file:Suppress("unused")

package org.jetbrains.androidx.build

import androidx.build.AndroidXExtension
import androidx.build.Publish
import androidx.build.RunApiTasks
import androidx.build.SoftwareType.ConfigurableSoftwareType
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType

class JetBrainsAndroidXRootImplPlugin @Inject constructor(
    val componentFactory: SoftwareComponentFactory
) : Plugin<Project> {
    override fun apply(project: Project) {
        project.allprojects { subproject ->
            // Apply capability rule to resolve conflicts between org.jetbrains.androidx.* and androidx.*
            subproject.configureJetBrainsCapabilityResolution()

            subproject.tasks.configureEach {
                if (it.name == "kotlinStoreYarnLock") it.enabled = false
                if (it.name == "kotlinWasmStoreYarnLock") it.enabled = false
            }

            // Never cache test results
            subproject.tasks.withType<AbstractTestTask>().configureEach {
                it.outputs.upToDateWhen { false }
            }

            // Disable Androidx publication, as the fork publication is configured
            // by JetBrainsPublication.
            //
            // It disables Androidx checks for publishing libraries that are not needed or
            // conflict with the fork publication
            subproject.afterEvaluate {
                val androidxExtension = subproject.extensions.findByType(AndroidXExtension::class.java)
                androidxExtension?.type = ConfigurableSoftwareType(
                    name = "JB Library",
                    publish = Publish.NONE,
                    compilationTarget = androidxExtension.type.compilationTarget,
                    checkApi = RunApiTasks.No("JB Library"),
                )
            }
        }
    }
}
