//import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

import androidx.build.AndroidXComposePlugin
import androidx.build.JetbrainsAndroidXPlugin
//plugins {
//    alias(libs.plugins.kotlinMultiplatform)
//    alias(libs.plugins.androidApplication)
//    alias(libs.plugins.composeMultiplatform)
//    alias(libs.plugins.composeCompiler)
//}

plugins {
    id("AndroidXPlugin")
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
//  [1.4 Update]  id("application")
//    kotlin("plugin.serialization") version "1.9.21"
    id("JetbrainsAndroidXPlugin")
}

AndroidXComposePlugin.applyAndConfigureKotlinPlugin(project)
JetbrainsAndroidXPlugin.applyAndConfigure(project)

val skikoWasm = configurations.findByName("skikoWasm") ?: configurations.create("skikoWasm")

dependencies {
    skikoWasm(libs.skikoWasm)
}

val resourcesDir = "$buildDir/resources"
val unzipTask = tasks.register("unzipWasm", Copy::class) {
    destinationDir = file(resourcesDir)
    from(skikoWasm.map { zipTree(it) })
}

kotlin {
    jvm()

    js {
        outputModuleName = "compilation-tests"
        browser {
            commonWebpackConfig {
                outputFileName = "compilation-tests.js"
            }
        }
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "compilation-tests"
        browser {
            commonWebpackConfig {
                outputFileName = "compilation-tests.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.rootDir.path)
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":compose:foundation:foundation"))
                implementation(project(":compose:foundation:foundation-layout"))
                implementation(project(":compose:material3:material3"))
                implementation(project(":compose:material3:material3-window-size-class"))
                implementation(project(":compose:material3:adaptive:adaptive"))
                implementation(project(":compose:material3:adaptive:adaptive-layout"))
                implementation(project(":compose:material3:adaptive:adaptive-navigation"))
                implementation(project(":compose:material:material"))
                implementation(project(":compose:mpp"))
                implementation(project(":compose:runtime:runtime"))
                implementation(project(":compose:ui:ui"))
                implementation(project(":compose:ui:ui-graphics"))
                implementation(project(":compose:ui:ui-text"))
                implementation(project(":compose:ui:ui-backhandler"))
                implementation(project(":lifecycle:lifecycle-common"))
                implementation(project(":lifecycle:lifecycle-runtime"))
                implementation(project(":lifecycle:lifecycle-runtime-compose"))
                implementation(project(":navigation:navigation-common"))
                implementation(project(":navigation:navigation-compose"))
                implementation(project(":navigation:navigation-runtime"))
                implementation(libs.kotlinStdlib)
                implementation(libs.kotlinCoroutinesCore)

                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3") {
                    // exclude dependencies, because they override local projects when we build 0.0.0-* version
                    // (see https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-core-desktop/1.6.11/material-icons-core-desktop-1.6.11.module)
                    exclude("org.jetbrains.compose.runtime")
                    exclude("org.jetbrains.compose.ui")
                }

                implementation("org.jetbrains.compose.material3:material3:1.9.0-beta03")
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            }
        }

        val skikoMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.skikoCommon)
            }
        }

        val jvmMain by getting {
            dependsOn(skikoMain)
            dependencies {
                implementation(libs.skikoCurrentOs)
                implementation(project(":compose:desktop:desktop"))
            }
        }

        val webMain by creating {
            dependsOn(skikoMain)
            resources.setSrcDirs(resources.srcDirs)
            resources.srcDirs(unzipTask.map { it.destinationDir })
        }

        val jsMain by getting {
            dependsOn(webMain)
        }

        val wasmJsMain by getting {
            dependsOn(webMain)
        }
    }

    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage=disable") }
}

tasks.create("runDesktop", JavaExec::class.java) {
    dependsOn(":compose:desktop:desktop:jar")
    mainClass.set("androidx.compose.mpp.compilation-tests.MainKt")
    args = listOfNotNull(project.findProperty("args")?.toString())
    systemProperty("skiko.fps.enabled", "true")
    val compilation = kotlin.jvm().compilations["main"]
    classpath =
        compilation.output.allOutputs +
            compilation.runtimeDependencyFiles
}