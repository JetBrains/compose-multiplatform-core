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

internal sealed interface IosTarget {
    val id: String
}

internal fun selectIosTarget(selection: String?, processes: Processes): IosTarget {
    // Automatic selection never probes or selects physical devices.
    if (selection == null) return selectIosTarget(null, listIosSimulators(processes), emptyList())
    var simulatorError: Exception? = null
    val simulators = try {
        listIosSimulators(processes)
    } catch (error: InterruptedException) {
        throw error
    } catch (error: Exception) {
        simulatorError = error
        emptyList()
    }
    val devices = try {
        listIosDevices(processes)
    } catch (error: InterruptedException) {
        throw error
    } catch (error: Exception) {
        // Simulator selection by UUID also works with Xcode versions that lack devicectl.
        if (simulators.none { selection == it.id || selection == it.name }) {
            simulatorError?.let { error.addSuppressed(it) }
            throw error
        }
        check(simulators.any { selection == it.id }) {
            "cannot resolve --ios-target '$selection' by name because physical iPhone discovery failed " +
                "(${error.message}); specify a simulator UUID"
        }
        println("Cannot discover physical iPhones (${error.message}); searching simulators only")
        emptyList()
    }
    simulatorError?.let { error ->
        if (devices.none { selection in listOf(it.id, it.udid, it.name) }) throw error
        check(devices.any { selection == it.id || selection == it.udid }) {
            "cannot resolve --ios-target '$selection' by name because iOS simulator discovery failed " +
                "(${error.message}); specify a physical iPhone UDID or CoreDevice UUID"
        }
        println("Cannot discover iOS simulators (${error.message}); searching physical iPhones only")
    }
    return selectIosTarget(selection, simulators, devices)
}

internal fun selectIosTarget(
    selection: String?, simulators: List<IosSimulator>, devices: List<IosDevice>,
): IosTarget {
    if (selection == null) {
        return simulators.firstOrNull { it.state == "Booted" } ?: simulators.firstOrNull()
            ?: error("no available iOS simulators; install an iOS runtime and create a simulator in Xcode")
    }
    val matches = simulators.filter { selection == it.id || selection == it.name } +
        devices.filter { selection in listOf(it.id, it.udid, it.name) }
    check(matches.isNotEmpty()) {
        "no available iOS target matching '$selection'; specify --ios-target with a simulator UUID or name, " +
            "or a physical iPhone UDID, CoreDevice UUID or name (connect, pair and unlock it, and enable Developer Mode)"
    }
    check(matches.size == 1) {
        "multiple iOS targets matching '$selection'; specify --ios-target with a unique ID"
    }
    return matches.single()
}
