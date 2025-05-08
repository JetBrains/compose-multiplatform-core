import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask

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
    moduleName.set("Compose Multiplatform API Reference")
    moduleVersion.set("1.8.0")

    pluginsConfiguration.html {
        customStyleSheets.from(file("material3-api-storytale-style.css"))
        templatesDir.set(file("dokka-templates"))
    }
}

// Task to copy WebAssembly stories to the Dokka output directory
tasks.register("copyStoriesToDokka") {
    description = "Copies WebAssembly stories to the Dokka output directory"
    group = "documentation"

    // Depend on the required tasks
    dependsOn(":mpp:apiReferences:dokkaGeneratePublicationHtml")
    dependsOn(":compose:material3:material3-stories:wasmJsBrowserStoriesProductionExecutableDistribution")

    doLast {
        // Define source and destination directories
        val sourceDir = rootProject.file("out/androidx/compose/material3/material3-stories/build/dist/wasmJs/StoriesProductionExecutable")
        val destDir = rootProject.file("out/androidx/mpp/apiReferences/build/dokka/html/stories")

        // Ensure the source directory exists
        if (sourceDir.exists()) {
            // Delete destination directory if it exists
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }

            // Create parent directories if they don't exist
            destDir.parentFile.mkdirs()

            // Copy the directory with a new name
            sourceDir.copyRecursively(destDir)

            println("Successfully copied WebAssembly stories to Dokka output directory")
        } else {
            println("Source directory does not exist: $sourceDir")
        }
    }
}

// Task to update stories references in HTML files
tasks.register("updateStoriesReferences") {
    description = "Updates stories references in HTML files"
    group = "documentation"

    // Depend on the copyStoriesToDokka task
    dependsOn("copyStoriesToDokka")

    doLast {
        // Define the directory containing HTML files to update
        val htmlDir = project.buildDir.resolve("dokka/html/material3")
        val storiesRoot = project.properties["apiReferences.storiesRootPath"] as String?

        if (storiesRoot.isNullOrBlank()) {
            return@doLast
        }

        val newRoot = if (storiesRoot.startsWith("/")) { storiesRoot } else { "/$storiesRoot"}

        if (htmlDir.exists()) {
            // Find all HTML files in the directory and its subdirectories
            val htmlFiles = htmlDir.walk()
                .filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
                .toList()

            var filesUpdated = 0

            // Process each HTML file
            htmlFiles.forEach { file ->
                val content = file.readText()

                // change the path to a story
                val updatedContent = content.replace("""src="/stories""", """src="/$newRoot""")

                // Write the updated content back to the file if changes were made
                if (content != updatedContent) {
                    file.writeText(updatedContent)
                    filesUpdated++
                }
            }
        }
    }
}
