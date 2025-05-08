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
    kotlin("jvm") apply false
    alias(libs.plugins.dokka)
}


dependencies {
    dokka(project(":compose:material3:material3"))
}


dokka {
    moduleName.set("Compose Multiplatform")
    moduleVersion.set("1.8.0")

    pluginsConfiguration.html {
        customStyleSheets.from(file("material3-api-storytale-style.css"))
        templatesDir.set(file("dokka-templates"))
    }
}

fun includeMaterial3Stories(storiesRootPath: String) {
    if (!storiesRootPath.endsWith("/stories")) {
        error("The storiesRootPath must end with `/stories`")
    }
    // Step 1: Copy Material3 stories to the Dokka output directory
    val storiesDir = project(":compose:material3:material3-stories")
        .buildDir.resolve("dist/wasmJs/StoriesProductionExecutable")
    val destDir = project.buildDir.resolve("dokka/html/stories/material3")

    if (storiesDir.exists()) {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.parentFile.mkdirs()
        storiesDir.copyRecursively(destDir)
    } else {
        return
    }

    // Step 2: Update stories references in HTML files
    val htmlDir = project.buildDir.resolve("dokka/html/material3")
    val newRoot = "$storiesRootPath/material3"

    if (htmlDir.exists()) {
        // Find all HTML files in the directory and its subdirectories
        val htmlFiles = htmlDir.walk()
            .filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
            .toList()

        htmlFiles.forEach { file ->
            val content = file.readText()

            // change the path to a story
            val updatedContent = content.replace("""src="/stories""", """src="$newRoot""")

            if (content != updatedContent) {
                file.writeText(updatedContent)
            }
        }
    }
}

// The result will be in .../out/androidx/mpp/apiReferences/build/dokka/html
tasks.register("buildApiReferencesWithStories") {

    // build the api references
    dependsOn(":mpp:apiReferences:dokkaGeneratePublicationHtml")
    // build the material3 stories
    dependsOn(":compose:material3:material3-stories:wasmJsBrowserStoriesProductionExecutableDistribution")

    doLast {
        // Additional processing to include the stories and update the paths
        includeMaterial3Stories(
            // storiesRootPath is a path relative to the root.
            // the parameter value must end with `/stories`.
            // example: `/api/compose-multiplatform/stories`
            storiesRootPath = project.properties["apiReferences.storiesRootPath"] as String? ?: "/stories"
        )
    }
}