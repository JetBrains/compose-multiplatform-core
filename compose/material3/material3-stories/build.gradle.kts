//import org.jetbrains.compose.web.tasks.UnpackSkikoWasmRuntimeTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.tomlj.Toml


plugins {
    // In this module we don't apply AndroidX plugins.
    // It's not going to be published to maven, it can be considered as another demo/playground.
    // It's very close to a user project, and AndroidX plugins cause interference with Storytale gradle plugin.
    // Therefore, we apply the "user-project-friendly" plugin:
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.8.0-beta01" // Needed for Storytale gradle plugin
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.storytale)
}

kotlin {
    // TODO: enable wasmJs target after fixes in storytale gradle plugin
//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        moduleName = "composeApp"
//        browser {
//            commonWebpackConfig {
//                outputFileName = "composeApp.js"
//            }
//        }
//    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":compose:runtime:runtime", null))
                implementation(project(":compose:ui:ui", null))
                implementation(project(":compose:material3:material3", null))
                implementation(project(":navigation:navigation-compose", null))
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinCoroutinesSwing)
                implementation(libs.skikoCurrentOs)
                implementation(project(":compose:desktop:desktop", null))
            }
        }
    }
}

/* Providing skiko.mjs and skiko.wasm to Storytale */
val toml = Toml.parse(
    project.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath()
)
val skikoVersion = toml.getTable("versions")!!.getString("skiko")!!
val resourcesDir = project.buildDir.resolve("resources")
val skikoWasm by project.configurations.creating

project.dependencies {
    skikoWasm("org.jetbrains.skiko:skiko-js-wasm-runtime:${skikoVersion}")
}

//afterEvaluate {
//    val unpackSkikoTask = project.tasks.withType<UnpackSkikoWasmRuntimeTask>().single()
//    val fetchSkikoWasmRuntime = project.tasks.register("fetchSkikoWasmRuntime", Copy::class.java) {
//        destinationDir = unpackSkikoTask.outputDir.get().asFile
//        from(skikoWasm.map { artifact ->
//            project.zipTree(artifact).matching {
//                include("skiko.wasm", "skiko.mjs")
//            }
//        })
//    }
//    tasks.getByName("unpackSkikoWasmRuntime").dependsOn(fetchSkikoWasmRuntime)
//}
/* End Skiko section */