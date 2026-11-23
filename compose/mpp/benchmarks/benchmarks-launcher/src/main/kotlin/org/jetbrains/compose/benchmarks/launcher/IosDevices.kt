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
import kotlinx.serialization.json.*

@Serializable
internal data class IosDevice(
    override val id: String,
    val udid: String,
    val name: String,
    val model: String?,
    val osVersion: String?,
) : IosTarget

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
            hardware.string("deviceType") == "iPhone" && physical &&
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

internal fun iosCollectorHost(processes: Processes): String {
    val route = processes.capture(listOf("/sbin/route", "-n", "get", "default"))
    val networkInterface = route.lineSequence().map { it.trim() }
        .firstOrNull { it.startsWith("interface:") }?.substringAfter(":")?.trim()
        ?: error("cannot detect the default network interface for the iPhone collector")
    return processes.capture(listOf("/usr/sbin/ipconfig", "getifaddr", networkInterface)).trim().also {
        check(it.isNotEmpty()) { "cannot detect the IPv4 address of network interface $networkInterface" }
    }
}
