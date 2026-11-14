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


package org.jetbrains.compose.benchmarks

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.jetbrains.compose.benchmarks.data.TargetEnvironment

internal actual fun collectTargetEnvironment(id: String): TargetEnvironment {
    val probe = EnvironmentProbe()
    fun property(name: String) = probe.read(name) { System.getProperty(name) }
    val runtime = Runtime.getRuntime()
    val osName = property("os.name")
    fun sysctl(name: String): String? = probe.read(name) {
        val process = ProcessBuilder("/usr/sbin/sysctl", "-n", name).start()
        try {
            check(process.waitFor(2, TimeUnit.SECONDS)) { "sysctl timeout" }
            check(process.exitValue() == 0) { "sysctl failed" }
            process.inputStream.bufferedReader().use { it.readText().trim().takeIf(String::isNotEmpty) }
        } finally {
            process.destroyForcibly()
        }
    }
    val cpuModel = when {
        osName?.startsWith("Mac") == true -> sysctl("machdep.cpu.brand_string")
        osName == "Linux" -> probe.read("cpuModel") {
            Files.readAllLines(Path.of("/proc/cpuinfo")).firstOrNull {
                it.startsWith("model name") || it.startsWith("Hardware")
            }?.substringAfter(':')?.trim()
        }
        else -> probe.read("cpuModel") { System.getenv("PROCESSOR_IDENTIFIER") }
    }
    return TargetEnvironment(
        id = id,
        runtime = "jvm",
        osName = osName,
        osVersion = property("os.version"),
        architecture = property("os.arch"),
        cpuModel = cpuModel,
        availableProcessors = probe.read("availableProcessors") { runtime.availableProcessors() },
        physicalMemoryBytes = probe.read("physicalMemoryBytes") {
            (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)
                ?.totalMemorySize?.takeIf { it > 0 }
        },
        deviceModel = if (osName?.startsWith("Mac") == true) sysctl("hw.model") else null,
        graphicsBackend = probe.read("graphicsBackend") { Platform.getGraphicsContext()::class.simpleName },
        jvmName = property("java.vm.name"),
        jvmVersion = property("java.vm.version"),
        jvmVendor = property("java.vm.vendor"),
        javaVersion = property("java.version"),
        jvmArguments = probe.read("jvmArguments") { ManagementFactory.getRuntimeMXBean().inputArguments },
        maxHeapBytes = probe.read("maxHeapBytes") { runtime.maxMemory() },
        warnings = probe.warnings.toList(),
    )
}
