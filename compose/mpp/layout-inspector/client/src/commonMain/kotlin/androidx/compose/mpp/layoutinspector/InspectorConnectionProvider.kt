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

package androidx.compose.mpp.layoutinspector

/** A host-reachable endpoint, including a future endpoint forwarded through usbmuxd. */
data class InspectorEndpoint(val host: String, val port: Int) {
    init {
        require(host.isNotBlank())
        require(port in 1..65535)
    }
}

/**
 * Owns the endpoints and any forwarding resources until discovery fails or the session ends.
 * The endpoint list must remain stable until [close], which the client calls once per acquisition.
 */
interface InspectorConnectionLease {
    val endpoints: List<InspectorEndpoint>
    suspend fun close()
}

/**
 * Acquires endpoints to try in order. Implementations may establish device port forwards.
 * If acquisition fails or is cancelled, the provider must release any partially acquired resources.
 */
fun interface InspectorConnectionProvider {
    suspend fun acquire(): InspectorConnectionLease
}

/** Loopback discovery for desktop JVM apps, native macOS apps, and iOS Simulators. */
class LoopbackConnectionProvider(
    private val ports: IntRange = InspectorProtocol.ports,
) : InspectorConnectionProvider {
    init {
        require(!ports.isEmpty() && ports.first >= 1 && ports.last <= 65535)
    }

    override suspend fun acquire(): InspectorConnectionLease = object : InspectorConnectionLease {
        override val endpoints: List<InspectorEndpoint> = ports.map { InspectorEndpoint("127.0.0.1", it) }
        override suspend fun close() { }
    }
}

/** Simulator networking uses the host's loopback interface. No device forwarding is required. */
class SimulatorConnectionProvider(
    ports: IntRange = InspectorProtocol.ports,
) : InspectorConnectionProvider by LoopbackConnectionProvider(ports)
