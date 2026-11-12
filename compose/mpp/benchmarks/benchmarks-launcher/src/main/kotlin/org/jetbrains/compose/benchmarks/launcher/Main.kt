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

package org.jetbrains.compose.benchmarks.launcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.benchmarks.report.generateHtmlReport
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*
import kotlin.math.round
import kotlin.system.exitProcess

private val cases = listOf(
    "AnimatedVisibility", "LazyGrid", "LazyGrid-ItemLaunchedEffect", "LazyGrid-SmoothScroll",
    "LazyGrid-SmoothScroll-ItemLaunchedEffect", "VisualEffects", "LazyList", "MultipleComponents",
    "MultipleComponents-NoVectorGraphics", "TextLayout", "CanvasDrawing", "HeavyShader",
)
private val modes = listOf("averageFrame", "fps", "realFrames", "startup")
private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

fun main(args: Array<String>) = Launcher(args.toList()).main(browserArguments(args))

// Clikt's varargValues stops at punctuation, including '/' in absolute browser paths.
// Feed each browser as an attached option value instead, preserving spaces inside paths.
private fun browserArguments(args: Array<String>): List<String> = buildList {
    var browsers = false
    for (arg in args) {
        when {
            arg == "--browser" -> {
                browsers = true
                add(arg)
            }
            browsers && !arg.startsWith("-") -> {
                if (last() == "--browser") removeAt(lastIndex)
                add("--browser=$arg")
            }
            else -> {
                browsers = arg.startsWith("--browser=")
                add(arg)
            }
        }
    }
}

private class Launcher(private val invocationArguments: List<String>) : CliktCommand(name = "benchmarks-launcher") {
    private val targets by option("--target", help = "Targets: jvm, macos, js, wasmJs, ios; space/comma separated")
        .varargValues().required()
    private val composeVersions by option(
        "--compose-version",
        help = "Compose versions or local, space/comma separated; default: version catalog"
    ).varargValues().default(emptyList())
    private val kotlinVersions by option(
        "--kotlin-version",
        help = "Kotlin versions, space/comma separated; default: version catalog"
    ).varargValues().default(emptyList())
    private val port by option(help = "Collector port").int().default(8090)
    private val tests by option(help = "Case names, space/comma separated, or all").varargValues()
        .default(listOf("all"))
    private val selectedModes by option("--modes", help = "Modes, space/comma separated, or all")
        .varargValues().default(listOf("all"))
    private val repeat by option(help = "Repetitions of each case/mode").int().default(1)
    private val timeout by option(help = "Seconds per test, excluding build time").double()
        .default(300.0)
    private val outputDir by option(
        "--output-dir",
        help = "Base directory for timestamped results; default: out/benchmark-reports"
    ).convert { absolutePath(it) }
    private val projectDir by option(
        "--project-dir",
        help = "Standalone benchmarks project, detected by default"
    )
        .convert { absolutePath(it) }
    private val corePath by option(
        "--compose-core-path",
        help = "Core checkout for local Compose; default: current core checkout"
    )
        .convert { absolutePath(it) }
    private val browsers by option("--browser", help = "Browser executables, commands, or macOS .apps; space/comma separated")
        .multiple()
    private val webPort by option(
        "--web-port",
        help = "Static file port, 0 selects a free port"
    ).int().default(0)
    private val simulator by option(help = "iOS Simulator UUID or unique name; default: one booted device")

    override fun run() {
        try {
            require(
                !System.getProperty("os.name").startsWith("Windows")
            ) { "this launcher requires macOS or Linux" }
            val selectedTargets = select(targets, listOf("jvm", "macos", "js", "wasmJs", "ios"))
            val selectedBrowsers = browsers.flatMap { it.split(",") }.distinct()
            require(selectedBrowsers.none { it.isBlank() }) { "browser must not be empty" }
            require(selectedTargets.none { it in listOf("ios", "macos") } || isMac) { "Apple targets require macOS" }
            require(port in 1..65535 && webPort in 0..65535) { "invalid port" }
            require(repeat > 0 && timeout.isFinite() && timeout > 0) { "repeat and timeout must be positive" }
            require(selectedTargets.none { it in listOf("js", "wasmJs") } || selectedBrowsers.isNotEmpty()) {
                "web targets require --browser"
            }
            launchSeries(selectedTargets, selectedBrowsers)
        } catch (error: Exception) {
            echo("Error: ${error.message}", err = true)
            exitProcess(1)
        }
    }

    private fun launchSeries(selectedTargets: List<String>, selectedBrowsers: List<String>) {
        val sessionStarted = System.nanoTime()
        val root = findRoot(projectDir)
        val out = path(System.getenv("OUT_DIR") ?: root.resolve("out").toString()).let {
            (if (it.isAbsolute) it else root.resolve(it)).toAbsolutePath().normalize()
        }
        val selectedKotlin = selectVersions(kotlinVersions, catalogVersion(root, "kotlin"))
        require("local" !in selectedKotlin) { "Kotlin must use published versions" }
        val catalogCompose = validateVersion(catalogVersion(root, "compose-multiplatform"))
        require(catalogCompose != "local") { "the catalog must contain a published Compose version" }
        val configuredCore = corePath ?: configuredCorePath(root)
        val selectedCompose = selectVersions(composeVersions, if (configuredCore != null) "local" else catalogCompose)
        require(corePath == null || "local" in selectedCompose) {
            "--compose-core-path requires local in --compose-version"
        }
        val selectedTests = select(tests, cases)
        val measurementModes = select(selectedModes, modes)
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val base = outputDir ?: out.resolve("benchmark-reports")
        Files.createDirectories(base)
        val session = Files.createDirectory(base.resolve(stamp))
        val sessionInfo = Session(listOf("benchmarks-launcher") + invocationArguments)
        session.resolve("session.json").writeText(json.encodeToString(sessionInfo) + "\n")
        val summaries = mutableListOf<Summary>()
        val activeProcesses = AtomicReference<Processes?>()
        val shuttingDown = AtomicBoolean()
        val mainThread = Thread.currentThread()
        val hook = Thread({
            shuttingDown.set(true)
            mainThread.interrupt()
            mainThread.join(20_000)
            activeProcesses.get()?.close()
        }, "benchmark-shutdown")
        Runtime.getRuntime().addShutdownHook(hook)
        var interrupted = false
        var reportFailed = false
        val configurations = selectedTargets.flatMap { target ->
            if (target in listOf("js", "wasmJs")) selectedBrowsers.mapIndexed { index, browser ->
                val name = path(browser).name.removeSuffix(".app").replace(Regex("[^A-Za-z0-9._-]"), "_")
                Triple(target, browser, "$target/browser-${index + 1}-$name")
            } else listOf(Triple(target, null, target))
        }
        val total = selectedKotlin.size * selectedCompose.size * configurations.size
        println("Matrix: $total configurations. Results: $session")
        try {
            for (kotlin in selectedKotlin) for (compose in selectedCompose) for ((target, browser, relativeDirectory) in configurations) {
                if (Thread.interrupted() || shuttingDown.get()) throw InterruptedException()
                val local = compose == "local"
                val core = if (local) configuredCore ?: root.resolve("../../..").normalize() else null
                val summary = Summary(
                    target, selectedTests, measurementModes, repeat, timeout,
                    kotlinVersion = kotlin, composeVersion = if (local) catalogCompose else compose,
                    localCompose = local, composeCorePath = core?.toString()
                )
                val directory = session.resolve("kotlin($kotlin)-cmp($compose)-reports/$relativeDirectory")
                Files.createDirectories(directory.parent)
                Files.createDirectory(directory)
                val logs = Files.createDirectory(directory.resolve("logs"))
                val environment = System.getenv().toMutableMap()
                environment["ORG_GRADLE_PROJECT_benchmarkKotlinVersion"] = kotlin
                environment["ORG_GRADLE_PROJECT_benchmarkComposeVersion"] = summary.composeVersion
                // An empty value disables a core checkout configured in gradle.properties.
                environment["ORG_GRADLE_PROJECT_composeCorePath"] = core?.toString().orEmpty()
                val processes = Processes(root, environment, logs.resolve("commands.log"))
                activeProcesses.set(processes)
                summaries.add(summary)
                println("[configuration ${summaries.size}/$total]")
                try {
                    runConfiguration(root, out, directory, logs, summary, processes, shuttingDown, browser)
                } finally {
                    activeProcesses.compareAndSet(processes, null)
                }
                if (summary.error == "interrupted") throw InterruptedException()
            }
        } catch (_: InterruptedException) {
            interrupted = true
        } catch (error: Exception) {
            if (shuttingDown.get()) interrupted = true else throw error
        } finally {
            Thread.interrupted()
            try {
                activeProcesses.getAndSet(null)?.close()
            } finally {
                Thread.interrupted()
                try {
                    sessionInfo.totalTimeSeconds = (System.nanoTime() - sessionStarted) / 1_000_000_000
                    session.resolve("session.json").writeText(json.encodeToString(sessionInfo) + "\n")
                    println("Generating HTML report...")
                    val html = generateHtmlReport(session)
                    println("HTML report: $html")
                } catch (error: Exception) {
                    reportFailed = true
                    System.err.println("Cannot generate HTML report: ${error.message}")
                } finally {
                    runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
                }
            }
        }
        val passed = summaries.count { it.error == null && it.runs.all { run -> run.status == "passed" } }
        println("Finished matrix: $passed/$total configurations passed. Results: $session")
        if (!shuttingDown.get()) {
            val code = if (interrupted) 130 else if (passed != total || reportFailed) 1 else 0
            if (code != 0) exitProcess(code)
        }
    }

    private fun runConfiguration(
        root: Path, out: Path, directory: Path, logs: Path, summary: Summary,
        processes: Processes, shuttingDown: AtomicBoolean, browser: String?,
    ) {
        val target = summary.target
        val reports = Files.createDirectory(directory.resolve("reports"))
        println("Versions: Kotlin ${summary.kotlinVersion}, Compose ${summary.composeVersion}${if (summary.localCompose) " (local)" else ""}")
        summary.composeCorePath?.let { println("Core: $it (uses its own Kotlin version)") }
        println("Results: $directory")
        try {
            summary.save(directory)
            val device = if (target == "ios") selectSimulator(simulator, processes) else null
            val browserPath = if (target in listOf("js", "wasmJs")) executable(
                requireNotNull(browser),
                processes
            ) else null
            val kind = browserPath?.let { browserKind(it) }
            if (browserPath != null) {
                summary.browser = Browser(kind!!, browserPath.toString())
                println("Browser: $kind ($browserPath)")
                if (kind == "safari") {
                    require(isMac) { "Safari requires macOS" }
                    requireSafariClosed(browserPath, processes)
                }
            }
            summary.launcherEnvironment = collectLauncherEnvironment(
                target, summary.composeCorePath?.let { Path.of(it) }, browserPath, device, processes
            )
            summary.save(directory)
            ensureFreePort(port)
            build(
                root, out, logs, device, processes, summary.kotlinVersion,
                summary.composeVersion,
                summary.composeCorePath?.let { Path.of(it) }, target
            )
            val app = artifact(root.resolve("benchmarks-app/build"), out, target)
            if (device != null) {
                if (device.state != "Booted") processes.run(
                    listOf(
                        "xcrun",
                        "simctl",
                        "boot",
                        device.id
                    ), logs.resolve("simulator.log")
                )
                processes.run(
                    listOf("xcrun", "simctl", "bootstatus", device.id, "-b"),
                    logs.resolve("simulator.log")
                )
                processes.run(
                    listOf("xcrun", "simctl", "install", device.id, app.toString()),
                    logs.resolve("simulator.log")
                )
            }
            ensureFreePort(port)
            val collectorCommand = listOf(
                root.resolve(
                    "benchmarks-collector/build/install/benchmarks-collector/bin/benchmarks-collector"
                ).toString(), port.toString(), reports.toString()
            )
            processes.start(collectorCommand, logs.resolve("collector.log")).use { collector ->
                waitForCollector(collector.process, port)
                val server = if (browserPath != null) WebServer(
                    app,
                    webPort,
                    logs.resolve("web-server.log")
                ) else null
                server.use {
                    val environment = EnvironmentCollection(UUID.randomUUID().toString())
                    summary.environment = environment
                    summary.save(directory)
                    println("Collecting target environment...")
                    try {
                        val started = System.nanoTime()
                        runApplication(
                            processes, target, app, browserPath, kind, device,
                            listOf("environment", environment.id, "http://127.0.0.1:$port/environment"),
                            server?.url, logs.resolve("environment.log"),
                        ) { process ->
                            val report = waitForEnvironment(reports, environment.id, started, timeout, process, collector.process)
                            environment.report = directory.relativize(report).toString()
                            environment.collectedAt = Instant.now().toString()
                        }
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (error: Exception) {
                        environment.error = error.message ?: error.toString()
                        System.err.println("Cannot collect target environment: ${environment.error}")
                    } finally {
                        summary.save(directory)
                    }
                    val total = summary.tests.size * summary.modes.size * repeat
                    for (name in summary.tests) for (mode in summary.modes) for (iteration in 1..repeat) {
                        if (Thread.interrupted()) throw InterruptedException()
                        check(collector.process.isAlive) { "collector exited; see logs/collector.log" }
                        if (kind == "safari") requireSafariClosed(
                            requireNotNull(browserPath),
                            processes
                        )
                        val id = UUID.randomUUID().toString()
                        val log = logs.resolve("$id.log")
                        val result = RunResult(id, name, mode, iteration, log = "logs/$id.log")
                        summary.runs.add(result)
                        summary.save(directory)
                        val started = System.nanoTime()
                        println("[${summary.runs.size}/$total] $name / $mode / $id")
                        try {
                            runApplication(
                                processes,
                                target,
                                app,
                                browserPath,
                                kind,
                                device,
                                listOf(mode, id, "http://127.0.0.1:$port/reports", name),
                                server?.url,
                                log
                            ) { process ->
                                val report = waitForReport(
                                    reports,
                                    id,
                                    name,
                                    mode,
                                    started,
                                    timeout,
                                    process,
                                    collector.process
                                )
                                result.status = "passed"
                                result.report = directory.relativize(report).toString()
                            }
                        } catch (error: InterruptedException) {
                            result.status = "interrupted"
                            throw error
                        } catch (error: Exception) {
                            result.status = "failed"
                            result.error = error.message ?: error.toString()
                            appendLog(log, "\nLauncher: ${error.stackTraceToString()}\n")
                        } finally {
                            result.elapsedSeconds =
                                round((System.nanoTime() - started) / 1_000_000.0) / 1000
                            summary.save(directory)
                        }
                        println("  ${result.status}: ${result.error ?: result.report.orEmpty()}")
                    }
                }
            }
        } catch (_: InterruptedException) {
            summary.error = "interrupted"
        } catch (error: Exception) {
            summary.error = error.message ?: error.toString()
            System.err.println("Error: ${summary.error}")
        } finally {
            Thread.interrupted() // File writes and cleanup must complete after cancellation.
            try {
                processes.close()
            } finally {
                Thread.interrupted()
                if (shuttingDown.get()) summary.error = "interrupted"
                summary.save(directory)
            }
        }
        println("Finished: ${summary.runs.count { it.status == "passed" }}/${summary.runs.size} passed. Results: $directory")
    }

    private fun build(
        root: Path, out: Path, logs: Path, device: Simulator?, processes: Processes,
        kotlinVersion: String, composeVersion: String, core: Path?, target: String,
    ) {
        val appTask = when (target) {
            "jvm" -> "packageReleaseUberJarForCurrentOS"
            "macos" -> "linkReleaseExecutableMacosArm64"
            "js" -> "jsBrowserDistribution"
            "wasmJs" -> "wasmJsBrowserDistribution"
            else -> null
        }
        // A separate project cache avoids locking the outer build when launched through Gradle run.
        val command = listOf(
            root.resolve("gradlew").toString(), "--project-cache-dir",
            root.resolve(".gradle/launcher").toString(), ":benchmarks-collector:installDist"
        ) +
                listOfNotNull(appTask?.let { ":benchmarks-app:$it" }) +
                listOf(
                    "-PbenchmarkKotlinVersion=$kotlinVersion",
                    "-PbenchmarkComposeVersion=$composeVersion",
                    "-PcomposeCorePath=${core?.toString().orEmpty()}"
                ) + "--console=plain"
        processes.run(command, logs.resolve("gradle-build.log"))
        if (target == "ios") {
            processes.run(
                listOf(
                    "xcodebuild",
                    "-project",
                    root.resolve("benchmarks-ios-app/BenchmarksIosApp.xcodeproj").toString(),
                    "-scheme",
                    "BenchmarksIosApp",
                    "-configuration",
                    "Release",
                    "-sdk",
                    "iphonesimulator",
                    "-destination",
                    "id=${requireNotNull(device).id}",
                    "-derivedDataPath",
                    out.resolve("benchmarks-ios-derived").toString(),
                    "ARCHS=arm64",
                    "CODE_SIGNING_ALLOWED=NO",
                    "build"
                ), logs.resolve("xcode-build.log")
            )
        }
    }

    private fun artifact(appBuild: Path, out: Path, target: String): Path {
        val app = when (target) {
            "jvm" -> appBuild.resolve("compose/jars").let { directory ->
                if (directory.exists()) directory.listDirectoryEntries("*-release.jar")
                    .maxByOrNull { it.getLastModifiedTime() } else null
            }

            "macos" -> appBuild.resolve("bin/macosArm64/releaseExecutable/benchmarks-app.kexe")
            "js", "wasmJs" -> appBuild.resolve("dist/$target/productionExecutable")
            else -> out.resolve("benchmarks-ios-derived/Build/Products/Release-iphonesimulator/BenchmarksIosApp.app")
        }
        check(app != null && app.exists()) { "release artifact not found after build" }
        return app
    }
}

private fun absolutePath(value: String): Path = path(value).toAbsolutePath().normalize()

private fun findRoot(explicit: Path?): Path {
    fun valid(candidate: Path) = candidate.resolve("settings.gradle.kts").exists() &&
            candidate.resolve("gradlew").exists() && candidate.resolve("benchmarks-app").isDirectory()
    if (explicit != null) {
        require(valid(explicit)) { "not a benchmarks project: $explicit" }
        return explicit
    }
    val cwd = absolutePath(".")
    val location = Path.of(Launcher::class.java.protectionDomain.codeSource.location.toURI())
    val candidates = sequenceOf(cwd, cwd.resolve("compose/mpp/benchmarks"), location)
        .flatMap { generateSequence(it) { parent -> parent.parent } }
    return candidates.firstOrNull { valid(it) }
        ?: error("cannot find the benchmarks project; pass --project-dir /path/to/benchmarks")
}

private fun select(values: List<String>, available: List<String>): List<String> {
    val items = values.flatMap { it.split(",") }
    if (items == listOf("all")) return available
    require(items.all { it in available }) { "unknown values: ${(items - available.toSet()).joinToString()}" }
    return items.distinct()
}

private fun ensureFreePort(port: Int) {
    ServerSocket().use { socket -> socket.bind(InetSocketAddress("127.0.0.1", port)) }
}

private fun waitForCollector(process: Process, port: Int) {
    val started = System.nanoTime()
    while ((System.nanoTime() - started) / 1e9 < 30) {
        check(process.isAlive) { "collector exited; see logs/collector.log" }
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
            return
        } catch (_: java.io.IOException) {
            Thread.sleep(100)
        }
    }
    error("collector did not start within 30 seconds; see logs/collector.log")
}

private fun waitForReport(
    reports: Path, id: String, name: String, mode: String, started: Long, timeout: Double,
    application: Process, collector: Process,
): Path {
    fun find(): Path? = reports.listDirectoryEntries("$id-*.json").firstOrNull()?.also {
        val report = Json.parseToJsonElement(it.readText()).jsonObject
        check(report.text("id") == id && report.text("name") == name && report.text("type") == mode) {
            "unexpected report contents: $it"
        }
    }
    while (true) {
        find()?.let { return it }
        check(collector.isAlive) { "collector exited; see logs/collector.log" }
        check((System.nanoTime() - started) / 1e9 < timeout) { "no report before test timeout" }
        if (!application.isAlive) return find() ?: error("application exited without a report")
        Thread.sleep(100)
    }
}

private fun waitForEnvironment(
    reports: Path, id: String, started: Long, timeout: Double, application: Process, collector: Process,
): Path {
    fun find(): Path? = reports.listDirectoryEntries("$id-environment-*.json").firstOrNull()?.also {
        val document = Json.parseToJsonElement(it.readText()).jsonObject
        check(document.text("id") == id && "runtime" in document && "type" !in document) {
            "unexpected environment document: $it"
        }
    }
    while (true) {
        find()?.let { return it }
        check(collector.isAlive) { "collector exited; see logs/collector.log" }
        check((System.nanoTime() - started) / 1e9 < timeout) { "no environment before collection timeout" }
        if (!application.isAlive) return find() ?: error("application exited without environment data")
        Thread.sleep(100)
    }
}

@Serializable
private data class Session(
    val launcherCommand: List<String>,
    var totalTimeSeconds: Long? = null,
)

@Serializable
private data class Summary(
    val target: String,
    val tests: List<String>,
    val modes: List<String>,
    val repeat: Int,
    val timeoutSeconds: Double,
    val kotlinVersion: String,
    val composeVersion: String,
    val localCompose: Boolean,
    val composeCorePath: String? = null,
    val runs: MutableList<RunResult> = mutableListOf(),
    var browser: Browser? = null,
    var launcherEnvironment: LauncherEnvironment? = null,
    var environment: EnvironmentCollection? = null,
    var error: String? = null,
) {
    fun save(directory: Path) =
        directory.resolve("summary.json").writeText(json.encodeToString(this) + "\n")
}

@Serializable
private data class Browser(val kind: String, val executable: String)

@Serializable
private data class RunResult(
    val id: String, val name: String, val type: String, val iteration: Int, val log: String,
    var status: String = "interrupted", var elapsedSeconds: Double = 0.0,
    var report: String? = null, var error: String? = null,
)
