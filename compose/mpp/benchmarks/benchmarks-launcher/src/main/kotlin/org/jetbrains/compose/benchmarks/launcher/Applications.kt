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

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val bundleId = "org.jetbrains.compose.benchmarks.app.BenchmarksIosApp"
internal val isMac = System.getProperty("os.name").startsWith("Mac")

internal fun path(value: String): Path = Path.of(
    when {
        value == "~" -> System.getProperty("user.home")
        value.startsWith("~/") -> System.getProperty("user.home") + value.drop(1)
        else -> value
    }
)

internal fun executable(value: String, processes: Processes): Path {
    var candidate = path(value)
    if (candidate.name.endsWith(".app") && Files.isDirectory(candidate)) {
        val name = processes.capture(
            listOf(
                "/usr/libexec/PlistBuddy", "-c", "Print :CFBundleExecutable",
                candidate.resolve("Contents/Info.plist").toAbsolutePath().toString(),
            )
        ).trim()
        candidate = candidate.resolve("Contents/MacOS/$name")
    }
    val found = if (Files.isRegularFile(candidate)) candidate else {
        System.getenv("PATH").orEmpty().split(":").map { Path.of(it).resolve(value) }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    }
    check(found != null && Files.isExecutable(found)) { "executable not found: $value" }
    return found.toRealPath()
}

internal fun browserKind(browser: Path): String {
    val name = browser.name.lowercase()
    return when {
        name in listOf("safari", "safari technology preview") -> "safari"
        "firefox" in name -> "firefox"
        listOf(
            "chrome",
            "chromium",
            "brave",
            "edge",
            "vivaldi"
        ).any { it in name } || name == "arc" -> "chromium"

        else -> error("cannot determine browser family from executable name: $browser")
    }
}

internal data class Simulator(val id: String, val state: String)

internal fun selectSimulator(selection: String?, processes: Processes): Simulator {
    val data = Json.parseToJsonElement(
        processes.capture(
            listOf(
                "xcrun", "simctl", "list", "devices", "available", "--json",
            )
        )
    ).jsonObject.getValue("devices").jsonObject
    val devices = data.values.flatMap { it.jsonArray }.map { it.jsonObject }
        .filter { it["isAvailable"]?.jsonPrimitive?.booleanOrNull == true }
    val matches = devices.filter {
        if (selection == null) it.text("state") == "Booted"
        else selection in listOf(it.text("udid"), it.text("name"))
    }
    check(matches.size == 1) { "specify --simulator UUID, or boot exactly one simulator before running" }
    return Simulator(matches.single().text("udid"), matches.single().text("state"))
}

internal fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

private fun safariBundle(browser: Path): Path = generateSequence(browser.parent) { it.parent }
    .firstOrNull { it.name.endsWith(".app") }
    ?: error("Safari executable must be inside its .app bundle")

private fun safariPids(browser: Path, processes: Processes): List<Long> =
    processes.capture(listOf("ps", "-ww", "-axo", "pid=,comm=")).lineSequence().mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"), limit = 2)
        if (fields.size != 2) return@mapNotNull null
        if (runCatching {
                Path.of(fields[1]).toRealPath()
            }.getOrNull() == browser) fields[0].toLong() else null
    }.toList()

internal fun requireSafariClosed(browser: Path, processes: Processes) {
    check(safariPids(browser, processes).isEmpty()) {
        "close Safari before starting the series; existing Safari sessions will not be closed"
    }
}

private fun quitSafari(browser: Path, processes: Processes, log: Path) {
    val owned = safariPids(browser, processes).mapNotNull { ProcessHandle.of(it).orElse(null) }
    if (owned.isEmpty()) return
    var interrupted = Thread.interrupted()
    try {
        try {
            processes.run(
                listOf(
                    "osascript",
                    "-e",
                    "on run argv\n tell application (item 1 of argv) to quit\nend run",
                    safariBundle(browser).toString(),
                ), log, timeoutSeconds = 5, checkExit = false
            )
        } catch (_: InterruptedException) {
            interrupted = true
        } catch (error: Exception) {
            appendLog(log, "Safari quit failed: ${error.message}\n")
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!interrupted && owned.any { it.isAlive } && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        owned.filter { it.isAlive }.forEach { it.destroyForcibly() }
        check(owned.all {
            !it.isAlive || runCatching {
                it.onExit().get(3, TimeUnit.SECONDS); true
            }.getOrDefault(false)
        }) {
            "Safari did not stop; refusing to start the next test"
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

internal fun runApplication(
    processes: Processes,
    target: String,
    app: Path,
    browser: Path?,
    kind: String?,
    simulator: Simulator?,
    parameters: List<String>,
    webUrl: String?,
    browserArgs: List<String>,
    log: Path,
    waitForReport: (Process) -> Unit,
) {
    if (target == "ios") {
        val device = requireNotNull(simulator).id
        try {
            processes.start(
                listOf(
                    "xcrun", "simctl", "launch", "--console", "--terminate-running-process",
                    device, bundleId
                ) + parameters, log
            ).use { waitForReport(it.process) }
        } finally {
            processes.run(
                listOf("xcrun", "simctl", "terminate", device, bundleId),
                log,
                10,
                checkExit = false
            )
        }
        return
    }
    if (browser == null) {
        val command = if (target == "jvm") {
            val java = System.getenv("JAVA_HOME")?.let { path(it).resolve("bin/java").toString() }
                ?: "java"
            listOf(executable(java, processes).toString(), "-jar", app.toString()) + parameters
        } else listOf(app.toString()) + parameters
        processes.start(command, log).use { waitForReport(it.process) }
        return
    }
    val query = listOf("benchmarkCollectorUrl", "test", "type", "id").zip(parameters)
        .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val url = "$webUrl/?$query"
    if (kind == "safari") {
        requireSafariClosed(browser, processes)
        processes.start(listOf("open", "-W", "-a", safariBundle(browser).toString(), url), log)
            .use {
                try {
                    waitForReport(it.process)
                } finally {
                    quitSafari(browser, processes, log)
                }
            }
        return
    }
    val profile = Files.createTempDirectory("benchmark-browser-")
    try {
        val arguments = if (kind == "chromium") {
            listOf(
                "--user-data-dir=$profile",
                "--no-first-run",
                "--no-default-browser-check",
                "--new-window"
            )
        } else {
            profile.resolve("user.js").writeText(
                "user_pref(\"browser.shell.checkDefaultBrowser\", false);\n" +
                    "user_pref(\"browser.startup.homepage_override.mstone\", \"ignore\");\n"
            )
            listOf("--no-remote", "--profile", profile.toString(), "--new-window")
        }
        processes.start(listOf(browser.toString()) + arguments + browserArgs + url, log)
            .use { waitForReport(it.process) }
    } finally {
        deleteTree(profile)
    }
}
