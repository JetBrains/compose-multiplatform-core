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

package androidx.build.kythe

import androidx.build.AndroidXExtension
import androidx.build.ProjectLayoutType
import androidx.build.checkapi.ApiTaskConfig
import androidx.build.checkapi.configureJavaInputsAndManifest
import androidx.build.checkapi.createReleaseApiConfiguration
import androidx.build.getDefaultTargetJavaVersion
import org.gradle.api.Project

/** Sets up tasks for generating kzip files that are used for generating xref support on website. */
fun Project.configureProjectForKzipTasks(config: ApiTaskConfig, extension: AndroidXExtension) {
    // We use the output of kzip tasks for the Kythe pipeline to generate xrefs in cs.android.com
    // This is not supported, nor needed in GitHub
    if (ProjectLayoutType.isPlayground(this)) {
        return
    }
    // afterEvaluate required to read extension properties
    afterEvaluate {
        val (javaInputs, _) = configureJavaInputsAndManifest(config) ?: return@afterEvaluate
        val generateApiDependencies = createReleaseApiConfiguration()

        GenerateKotlinKzipTask.setupProject(
            project,
            javaInputs,
            generateApiDependencies,
            extension.kotlinTarget,
            getDefaultTargetJavaVersion(extension.type, project.name)
        )
    }
}
