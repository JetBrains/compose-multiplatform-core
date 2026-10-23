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

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("kotlin-multiplatform")
}

// Keep the Klib identity used by precompiled dependencies of the demo.
group = "androidx.collection"
version = "1.6.0"

val collectionSources = rootProject.layout.projectDirectory.dir("collection/collection/src")
val generatedSources = layout.buildDirectory.dir("generated/profiledCollection")
val generateProfiledCollection by tasks.registering(Sync::class) {
    from(collectionSources.dir("commonMain/kotlin"))
    into(generatedSources)

    val mapFile = collectionSources.file("commonMain/kotlin/androidx/collection/IntObjectMap.kt")
    inputs.file(mapFile)
    doFirst {
        val source = mapFile.asFile.readText()
        listOf(
            "    private var growthLimit = 0",
            "        initializeStorage(unloadedCapacity(initialCapacity))",
            "        _size += 1",
        ).forEach { anchor ->
            check(source.lineSequence().count { it == anchor } == 1) {
                "IntObjectMap instrumentation needs updating: expected one '$anchor'"
            }
        }
    }

    filesMatching("androidx/collection/IntObjectMap.kt") {
        filter { line: String ->
            when (line) {
                "    private var growthLimit = 0" ->
                    "$line\n    private val profileId: Int"
                "        initializeStorage(unloadedCapacity(initialCapacity))" ->
                    "$line\n        profileId = IntObjectMapStats.register()"
                "        _size += 1" ->
                    "$line\n        IntObjectMapStats.record(profileId, _size)"
                else -> line
            }
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    js {
        outputModuleName = "collection"
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "collection"
        nodejs()
    }

    sourceSets {
        configureEach {
            languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
        }
        commonMain {
            kotlin.srcDir(generateProfiledCollection)
            kotlin.srcDir("src/main/kotlin")
            dependencies { api("androidx.annotation:annotation:1.9.1") }
        }
        val webMain by getting {
            kotlin.srcDir(collectionSources.dir("nonJvmMain/kotlin"))
            kotlin.srcDir(collectionSources.dir("webMain/kotlin"))
            dependencies {
                implementation("org.jetbrains.compose.annotation-internal:annotation:1.10.0")
            }
        }
        val webTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
    }
}
