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

import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.*
import org.jetbrains.compose.benchmarks.report.TargetEnvironment
import platform.Foundation.NSProcessInfo
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.darwin.sysctlbyname
import platform.posix.getenv
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal actual fun collectTargetEnvironment(id: String): TargetEnvironment {
    val probe = EnvironmentProbe()
    val process = NSProcessInfo.processInfo
    fun environment(name: String) = getenv(name)?.toKString()
    fun sysctl(name: String): String? = probe.read(name) {
        memScoped {
            val size = alloc<size_tVar>()
            check(sysctlbyname(name, null, size.ptr, null, 0u) == 0)
            val buffer = allocArray<ByteVar>(size.value.toInt())
            check(sysctlbyname(name, buffer, size.ptr, null, 0u) == 0)
            buffer.toKString()
        }
    }
    val simulator = environment("SIMULATOR_UDID") != null
    return TargetEnvironment(
        id = id,
        runtime = "native",
        osName = kotlin.native.Platform.osFamily.name,
        osVersion = probe.read("osVersion") { process.operatingSystemVersionString },
        architecture = kotlin.native.Platform.cpuArchitecture.name,
        cpuModel = sysctl("machdep.cpu.brand_string"),
        availableProcessors = probe.read("availableProcessors") { process.activeProcessorCount.toInt() },
        physicalMemoryBytes = probe.read("physicalMemoryBytes") {
            process.physicalMemory.toLong().takeIf { it > 0 }
        },
        deviceModel = sysctl(if (kotlin.native.Platform.osFamily.name == "MACOSX" || simulator) "hw.model" else "hw.machine"),
        simulator = simulator,
        simulatorModel = environment("SIMULATOR_MODEL_IDENTIFIER"),
        gpuName = probe.read("gpuName") { MTLCreateSystemDefaultDevice()?.name },
        warnings = probe.warnings.toList(),
    )
}
