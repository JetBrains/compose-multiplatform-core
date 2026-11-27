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

import java.nio.file.Files
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal sealed interface IosTarget {
    val id: String
}

@Serializable
internal data class IosSimulator(
    override val id: String,
    val state: String,
    val name: String,
    val deviceTypeIdentifier: String?,
    val runtimeIdentifier: String,
    val runtimeVersion: String? = null,
    val runtimeBuild: String? = null,
) : IosTarget

@Serializable
internal data class IosDevice(
    override val id: String,
    val udid: String,
    val name: String,
    val model: String?,
    val osVersion: String?,
) : IosTarget

internal fun listIosSimulators(processes: Processes): List<IosSimulator> {
    val data = Json.parseToJsonElement(
        processes.capture(
            listOf(
                "xcrun", "simctl", "list", "devices", "available", "--json",
            )
        )
    ).jsonObject.getValue("devices").jsonObject
    return listIosSimulators(data)
}

internal fun listIosSimulators(data: JsonObject): List<IosSimulator> =
    data.filterKeys { it.startsWith("com.apple.CoreSimulator.SimRuntime.iOS-") }
        .flatMap { (runtime, devices) ->
            devices.jsonArray.map { JsonObject(it.jsonObject + ("runtimeIdentifier" to JsonPrimitive(runtime))) }
        }
        .filter { it["isAvailable"]?.jsonPrimitive?.booleanOrNull == true }
        .map { device ->
            IosSimulator(
                device.text("udid"), device.text("state"), device.text("name"),
                device["deviceTypeIdentifier"]?.jsonPrimitive?.contentOrNull, device.text("runtimeIdentifier"),
            )
        }

// Use a file rather than stdout: older Xcode releases mix progress messages into stdout.
internal fun listIosDevices(processes: Processes): List<IosDevice> {
    val output = Files.createTempFile("benchmark-ios-devices-", ".json")
    val data = try {
        processes.capture(listOf("xcrun", "devicectl", "list", "devices", "--json-output", output.toString()))
        Json.parseToJsonElement(output.readText()).jsonObject
    } finally {
        Files.deleteIfExists(output)
    }
    return listIosDevices(data)
}

internal fun listIosDevices(data: JsonObject): List<IosDevice> =
    data.getValue("result").jsonObject.getValue("devices").jsonArray.map { it.jsonObject }
        .filter { device ->
            val hardware = device.properties("hardware", "hardwareProperties")
            val connection = device.properties("connection", "connectionProperties")
            // Connected devices can omit reality until their developer services are ready.
            // An ECID identifies physical hardware; sameMachine is a simulator transport.
            val physical = when (hardware.string("reality")) {
                "physical" -> true
                null -> hardware["ecid"] != null && hardware["ecid"] != JsonNull &&
                    connection.string("transportType") != "sameMachine"
                else -> false
            }
            hardware.string("deviceType") in listOf("iPhone", "iPad") && physical &&
                connection.string("pairingState") == "paired" &&
                (connection.string("state") ?: connection.string("tunnelState")) in listOf("connected", "disconnected")
        }.map { device ->
            val hardware = device.properties("hardware", "hardwareProperties")
            val state = device.properties("state", "deviceProperties")
            val software = device["properties"]?.jsonObject?.get("software")?.jsonObject
            val version = software?.get("osVersionNumber")
            IosDevice(
                device.text("identifier"), hardware.text("udid"), state.text("name"),
                hardware.string("marketingName"),
                (version as? JsonObject)?.string("stringValue")
                    ?: device["deviceProperties"]?.jsonObject?.string("osVersionNumber"),
            )
        }

private fun JsonObject.properties(name: String, legacy: String): JsonObject =
    get("properties")?.jsonObject?.get(name)?.jsonObject ?: getValue(legacy).jsonObject

private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

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
            "cannot resolve --ios-target '$selection' by name because physical iOS device discovery failed " +
                "(${error.message}); specify a simulator UUID"
        }
        println("Cannot discover physical iOS devices (${error.message}); searching simulators only")
        emptyList()
    }
    simulatorError?.let { error ->
        if (devices.none { selection in listOf(it.id, it.udid, it.name) }) throw error
        check(devices.any { selection == it.id || selection == it.udid }) {
            "cannot resolve --ios-target '$selection' by name because iOS simulator discovery failed " +
                "(${error.message}); specify a physical iOS device UDID or CoreDevice UUID"
        }
        println("Cannot discover iOS simulators (${error.message}); searching physical iOS devices only")
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
            "or a physical iOS device UDID, CoreDevice UUID or name (connect, pair and unlock it, and enable Developer Mode)"
    }
    check(matches.size == 1) {
        "multiple iOS targets matching '$selection'; specify --ios-target with a unique ID"
    }
    return matches.single()
}
