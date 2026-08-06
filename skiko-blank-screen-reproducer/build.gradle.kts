plugins {
    kotlin("multiplatform") version "2.3.20"
}

val skikoVersion = "0.150.1"

val skikoWasm by configurations.creating

dependencies {
    skikoWasm("org.jetbrains.skiko:skiko-js-wasm-runtime:$skikoVersion")
}

val unpackWasmRuntime = tasks.register("unpackWasmRuntime", Copy::class) {
    destinationDir = file("$buildDir/skiko-runtime/")
    from(skikoWasm.map { zipTree(it) })
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
    dependsOn(unpackWasmRuntime)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.skiko:skiko:$skikoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            }
            resources.srcDirs(unpackWasmRuntime.map { it.destinationDir })
        }
    }
}
