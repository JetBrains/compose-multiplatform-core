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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.varargValues
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.round
import kotlin.system.exitProcess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val cases = listOf(
    "AnimatedVisibility", "LazyGrid", "LazyGrid-ItemLaunchedEffect", "LazyGrid-SmoothScroll",
    "LazyGrid-SmoothScroll-ItemLaunchedEffect", "VisualEffects", "LazyList", "MultipleComponents",
    "MultipleComponents-NoVectorGraphics", "TextLayout", "CanvasDrawing", "HeavyShader",
)
private val modes = listOf("averageFrame", "fps", "realFrames", "startup")
private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

fun main(args: Array<String>) = Launcher().main(args)

private class Launcher : CliktCommand(name = "benchmarks-launcher") {
    private val target by option(help = "Target to build and run")
        .choice("jvm", "macos", "js", "wasmJs", "ios").required()
    private val port by option(help = "Collector port").int().default(8090)
    private val tests by option(help = "Case names, space/comma separated, or all").varargValues()
        .default(listOf("all"))
    private val selectedModes by option("--modes", help = "Modes, space/comma separated, or all")
        .varargValues().default(listOf("all"))
    private val repeat by option(help = "Repetitions of each case/mode").int().default(1)
    private val timeout by option(help = "Seconds per test, excluding build time").double()
        .default(30.0)
    private val outputDir by option(
        "--output-dir",
        help = "New directory for this series"
    ).convert { absolutePath(it) }
    private val projectDir by option(
        "--project-dir",
        help = "Standalone benchmarks project, detected by default"
    )
        .convert { absolutePath(it) }
    private val skipBuild by option(
        "--skip-build",
        help = "Reuse existing release artifacts"
    ).flag()
    private val corePath by option(
        "--compose-core-path",
        help = "Include a core checkout instead of Maven libraries"
    )
        .convert { absolutePath(it) }
    private val browser by option(help = "Chromium/Firefox/Safari executable, command name, or macOS .app")
    private val browserArgs by option(
        "--browser-arg",
        help = "Extra browser flag; use --browser-arg=--flag"
    ).multiple()
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
            require(target !in listOf("ios", "macos") || isMac) { "Apple targets require macOS" }
            require(port in 1..65535 && webPort in 0..65535) { "invalid port" }
            require(repeat > 0 && timeout.isFinite() && timeout > 0) { "repeat and timeout must be positive" }
            require(
                target !in listOf(
                    "js",
                    "wasmJs"
                ) || browser != null
            ) { "web targets require --browser" }
            launchSeries()
        } catch (error: Exception) {
            echo("Error: ${error.message}", err = true)
            exitProcess(1)
        }
    }

    private fun launchSeries() {
        val root = findRoot(projectDir)
        val out = path(System.getenv("OUT_DIR") ?: root.resolve("out").toString()).let {
            (if (it.isAbsolute) it else root.resolve(it)).toAbsolutePath().normalize()
        }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val directory = outputDir ?: out.resolve(
            "benchmark-reports/$stamp-$target-${
                UUID.randomUUID().toString().take(8)
            }"
        )
        val summary = Summary(
            target, select(tests, cases), select(selectedModes, modes), repeat, timeout,
            composeCorePath = corePath?.toString()
        )
        Files.createDirectories(directory.parent)
        Files.createDirectory(directory)
        val logs = Files.createDirectory(directory.resolve("logs"))
        val reports = Files.createDirectory(directory.resolve("reports"))
        val environment = System.getenv().toMutableMap()
        corePath?.let { environment["ORG_GRADLE_PROJECT_composeCorePath"] = it.toString() }
        val processes = Processes(root, environment, logs.resolve("commands.log"))
        val shuttingDown = AtomicBoolean()
        val mainThread = Thread.currentThread()
        val hook = Thread({
            shuttingDown.set(true)
            mainThread.interrupt()
            mainThread.join(20_000)
            processes.close()
        }, "benchmark-shutdown")
        Runtime.getRuntime().addShutdownHook(hook)
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
                    require(browserArgs.isEmpty()) { "--browser-arg is not supported for Safari" }
                    requireSafariClosed(browserPath, processes)
                }
            }
            ensureFreePort(port)
            if (!skipBuild) build(root, out, logs, device, processes)
            val app = artifact(root.resolve("benchmarks-app/build"), out)
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
                                listOf("http://127.0.0.1:$port/reports", name, mode, id),
                                server?.url,
                                browserArgs,
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
                try {
                    summary.save(directory)
                } finally {
                    runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
                }
            }
        }
        println("Finished: ${summary.runs.count { it.status == "passed" }}/${summary.runs.size} passed. Results: $directory")
        if (!shuttingDown.get()) {
            val code = if (summary.error == "interrupted") 130
            else if (summary.error != null || summary.runs.any { it.status != "passed" }) 1 else 0
            if (code != 0) exitProcess(code)
        }
    }

    private fun build(root: Path, out: Path, logs: Path, device: Simulator?, processes: Processes) {
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
            listOfNotNull(appTask?.let { ":benchmarks-app:$it" }) + "--console=plain"
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

    private fun artifact(appBuild: Path, out: Path): Path {
        val app = when (target) {
            "jvm" -> appBuild.resolve("compose/jars").let { directory ->
                if (directory.exists()) directory.listDirectoryEntries("*-release.jar")
                    .maxByOrNull { it.getLastModifiedTime() } else null
            }

            "macos" -> appBuild.resolve("bin/macosArm64/releaseExecutable/benchmarks-app.kexe")
            "js", "wasmJs" -> appBuild.resolve("dist/$target/productionExecutable")
            else -> out.resolve("benchmarks-ios-derived/Build/Products/Release-iphonesimulator/BenchmarksIosApp.app")
        }
        check(app != null && app.exists()) { "release artifact not found; run without --skip-build" }
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

@Serializable
private data class Summary(
    val target: String,
    val tests: List<String>,
    val modes: List<String>,
    val repeat: Int,
    val timeoutSeconds: Double,
    val composeCorePath: String? = null,
    val runs: MutableList<RunResult> = mutableListOf(),
    var browser: Browser? = null,
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
