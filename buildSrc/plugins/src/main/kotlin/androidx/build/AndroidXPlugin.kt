/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.KotlinClosure1

/**
 * A plugin which enables all of the Gradle customizations for AndroidX. This plugin reacts to other
 * plugins being added and adds required and optional functionality.
 *
 * The actual implementation is in AndroidXImplPlugin. This extracts this logic out of the classpath
 * so that individual tasks can't access this logic so Gradle can know that changes to this logic
 * doesn't need to automatically invalidate every task
 */
class AndroidXPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val supportRoot = project.getSupportRootFolder()
        project.apply(
            mapOf<String, String>(
                "from" to "$supportRoot/buildSrc/apply/applyAndroidXImplPlugin.gradle"
            )
        )

        // TODO(buildSrc) remove after migrating all build.gradle to the new buildSrc
        project.extra.set(
            "projectOrArtifact",
            KotlinClosure1<String, Any>(
                function = { "test:test:1.0" }
            )
        )
    }

    companion object {
        /** @return `true` if running in a Playground (Github) setup, `false` otherwise. */
        @JvmStatic
        fun isPlayground(project: Project): Boolean {
            return ProjectLayoutType.isPlayground(project)
        }
    }
}
