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
