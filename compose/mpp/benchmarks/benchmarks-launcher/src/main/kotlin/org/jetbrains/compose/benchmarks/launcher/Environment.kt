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

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class LauncherEnvironment(
    val capturedAt: String = Instant.now().toString(),
    val buildProfile: String = "release",
    val optimization: String,
    val coreCommit: String? = null,
    val coreDirty: Boolean? = null,
    val browserVersion: String? = null,
    val simulator: Simulator? = null,
    val host: HostEnvironment? = null,
    val xcodeVersion: String? = null,
    val sdkVersion: String? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
internal data class HostEnvironment(
    val osName: String,
    val osVersion: String,
    val architecture: String,
    val cpuModel: String?,
    val availableProcessors: Int,
    val physicalMemoryBytes: Long?,
)

@Serializable
internal data class EnvironmentCollection(
    val id: String,
    val log: String = "logs/environment.log",
    var report: String? = null,
    var collectedAt: String? = null,
    var error: String? = null,
)

internal fun collectLauncherEnvironment(
    target: String, core: Path?, browser: Path?, simulator: Simulator?, processes: Processes,
): LauncherEnvironment {
    val warnings = mutableListOf<String>()
    fun <T> read(field: String, block: () -> T): T? = try {
        block()
    } catch (error: InterruptedException) {
        throw error
    } catch (_: Exception) {
        warnings.add("Cannot read $field")
        null
    }
    fun command(vararg args: String) = processes.capture(args.toList(), timeoutSeconds = 5).trim()
    val commit = core?.let { read("coreCommit") { command("git", "-C", it.toString(), "rev-parse", "HEAD") } }
    val dirty = core?.let { read("coreDirty") {
        command("git", "-C", it.toString(), "status", "--porcelain", "--untracked-files=normal").isNotEmpty()
    } }
    val browserVersion = browser?.let { read("browserVersion") {
        // Running Safari with --version opens it; read the selected bundle instead.
        if (browserKind(it) == "safari") {
            val bundle = generateSequence(it.parent) { parent -> parent.parent }
                .first { parent -> parent.fileName.toString().endsWith(".app") }
            command("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleShortVersionString",
                bundle.resolve("Contents/Info.plist").toString())
        } else command(it.toString(), "--version")
    } }
    val device = simulator?.let { selected -> read("simulatorRuntime") {
        val runtimes = Json.parseToJsonElement(command("xcrun", "simctl", "list", "runtimes", "--json"))
            .jsonObject.getValue("runtimes").jsonArray.map { it.jsonObject }
        val runtime = runtimes.first { it.text("identifier") == selected.runtimeIdentifier }
        selected.copy(runtimeVersion = runtime.text("version"), runtimeBuild = runtime.text("buildversion"))
    } ?: selected }
    val host = if (simulator != null) read("host") {
        HostEnvironment(
            System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
            read("host.cpuModel") { command("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string") },
            Runtime.getRuntime().availableProcessors(),
            read("host.physicalMemoryBytes") {
                (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.totalMemorySize
            },
        )
    } else null
    return LauncherEnvironment(
        optimization = when (target) {
            "jvm" -> "ProGuard (release uber JAR)"
            "js", "wasmJs" -> "Kotlin production executable / webpack production"
            else -> "Kotlin/Native release optimizations"
        },
        coreCommit = commit, coreDirty = dirty, browserVersion = browserVersion, simulator = device, host = host,
        xcodeVersion = if (target in listOf("ios", "macos")) read("xcodeVersion") { command("xcodebuild", "-version") } else null,
        sdkVersion = if (target in listOf("ios", "macos")) read("sdkVersion") {
            command("xcrun", "--sdk", if (target == "ios") "iphonesimulator" else "macosx", "--show-sdk-version")
        } else null,
        warnings = warnings.toList(),
    )
}
