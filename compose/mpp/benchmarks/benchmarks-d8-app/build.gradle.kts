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

@file:OptIn(ExperimentalWasmDsl::class)

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.time.Instant
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    wasmJs {
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":benchmarks-app"))
            implementation(project(":benchmarks-runtime"))
            implementation(project(":benchmarks-data"))
            implementation(project(":benchmarks-compose-init-current"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinCoroutinesCore)
            implementation(libs.kotlinSerializationJson)
        }
    }
}

@DisableCachingByDefault(because = "Each invocation must perform fresh benchmark measurements")
abstract class D8BenchmarkRun : DefaultTask() {
    @get:Input
    abstract val d8Executable: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherFile: RegularFileProperty

    @get:Input
    abstract val benchmarkName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun runBenchmark() {
        val command = listOf(
            d8Executable.get(), "--expose-gc", "--module",
            launcherFile.get().asFile.absolutePath, "--",
        )
        val name = benchmarkName.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Pass one benchmark case with -PbenchmarkName=<case>")
        val directory = Files.createDirectories(outputDirectory.get().asFile.toPath())
        Files.deleteIfExists(directory.resolve("report.json"))
        Files.writeString(directory.resolve("started"), Instant.now().toString())
        logger.lifecycle("Running D8 benchmark: $name")
        val (result, stdout) = runProcess(command + name, directory.resolve("d8.log"))
        result.assertNormalExitValue()
        check(stdout.lineSequence().any { it == "BENCHMARK_COMPLETE" }) {
            "D8 exited without completing $name; see ${directory.resolve("d8.log")}"
        }
        val body = stdout.lineSequence().single { it.startsWith("BENCHMARK_REPORT:") }
            .removePrefix("BENCHMARK_REPORT:")
        val report = JsonSlurper().parseText(body) as Map<*, *>
        check(report["name"] == name && report["type"] == "averageFrame") {
            "Unexpected D8 report for $name"
        }
        logger.lifecycle("Report saved: ${saveReport(directory, body)}")
    }

    private fun runProcess(command: List<String>, log: Path): Pair<ExecResult, String> {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result = try {
            execOperations.exec {
                commandLine(command)
                standardOutput = stdout
                errorOutput = stderr
                isIgnoreExitValue = true
            }
        } finally {
            Files.newBufferedWriter(log).use {
                it.appendLine("=== stdout ===")
                it.append(stdout.toString(Charsets.UTF_8))
                it.appendLine("\n=== stderr ===")
                it.append(stderr.toString(Charsets.UTF_8))
            }
        }
        return result to stdout.toString(Charsets.UTF_8)
    }

    private fun saveReport(directory: Path, body: String): Path {
        val target = directory.resolve("report.json")
        val temporary = Files.createTempFile(directory, ".averageFrame-", ".tmp")
        try {
            Files.writeString(temporary, body)
            Files.move(temporary, target, ATOMIC_MOVE)
            return target
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

// Use the managed D8 executable without registering standard development/production runners.
plugins.apply(D8Plugin::class.java)
val d8 = extensions.getByType<D8EnvSpec>()
tasks.register<D8BenchmarkRun>("wasmJsD8BenchmarkRun") {
    group = "benchmark"
    description = "Run one optimized Wasm benchmark selected by -PbenchmarkName=<case>."
    dependsOn("prepareWasmJsD8Runtime", "kotlinWasmD8Setup")
    d8Executable.set(d8.executable)
    launcherFile.set(layout.buildDirectory.file("d8/production/launcher.mjs"))
    benchmarkName.set(providers.gradleProperty("benchmarkName").orElse(""))
    outputDirectory.set(layout.buildDirectory.dir("reports/benchmark"))
}

val prepareD8Runtime = tasks.register<Sync>("prepareWasmJsD8Runtime") {
    dependsOn(
        "wasmJsProductionExecutableCompileSync",
        "unpackSkikoRuntimeForWasmJs",
        ":kotlinWasmNpmInstall",
    )
    from(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/optimized"))
    from(tasks.named("wasmJsProcessResources"))
    from(layout.buildDirectory.dir("compose/skiko-wasmJs-runtime"))
    from(rootProject.layout.buildDirectory.file("wasm/node_modules/@js-joda/core/dist/js-joda.esm.js"))
    into(layout.buildDirectory.dir("d8/production"))
    doLast {
        fun File.replaceText(old: String, replacement: String) {
            writeText(readText().replace(old, replacement))
        }

        val imports = destinationDir.listFiles().orEmpty().first {
            it.name.endsWith(".import-object.mjs")
        }
        imports.replaceText("from '@js-joda/core'", "from './js-joda.esm.js'")
        // Local core also imports Skiko through re-export modules and GPU extensions.
        // Every consumer must share the same D8 instance, not load the browser runtime.
        val extensionUrl = Regex("""new URL\(["'](\./skiko-[^"']+\.wasm)["'],\s*import\.meta\.url\)\.href""")
        destinationDir.listFiles().orEmpty().filter { it.extension == "mjs" }.forEach { file ->
            val original = file.readText()
            var patched = original
                .replace("from './skiko.mjs'", "from './skikod8.mjs'")
                .replace("from \"./skiko.mjs\"", "from \"./skikod8.mjs\"")
            if (extensionUrl.containsMatchIn(patched)) {
                patched = "import { resolveSkikoPath } from './skiko-loader.mjs';\n" +
                    extensionUrl.replace(patched) { "resolveSkikoPath('${it.groupValues[1]}')" }
            }
            file.writeText(patched)
        }
        destinationDir.resolve("skikod8.mjs").replaceText(
            "loadSkikoWASM().then",
            "loadSkikoWASM(d8SkikoOptions).then",
        )
        destinationDir.resolve("skikod8.mjs").let {
            it.writeText("import { d8SkikoOptions } from './skiko-loader.mjs';\n" + it.readText())
        }
        val moduleName = imports.name.removeSuffix(".import-object.mjs")
        destinationDir.resolve("launcher.mjs").replaceText("__BENCHMARK_MODULE__", moduleName)
    }
}

tasks.register<Zip>("buildD8Distribution") {
    dependsOn(prepareD8Runtime)
    from(prepareD8Runtime.map { it.destinationDir })
    archiveFileName.set("d8-distribution.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
