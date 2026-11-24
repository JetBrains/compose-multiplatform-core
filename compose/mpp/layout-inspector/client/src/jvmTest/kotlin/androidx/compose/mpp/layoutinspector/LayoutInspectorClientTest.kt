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

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LayoutInspectorClientTest {
    @Test
    fun discoverySkipsUnreachableAndNonInspectorEndpointsAndUsesFirstAgent() = runBlocking<Unit> {
        val first = ComposeLayoutInspector(52700..52709)
        val second = ComposeLayoutInspector(52700..52709)
        val wrongServer = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) { send(Frame.Text("not an inspector")) } }
        }.startSuspend()
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val lease = TrackingLease(listOf(
            InspectorEndpoint("127.0.0.1", unavailablePort),
            InspectorEndpoint("127.0.0.1", wrongServer.engine.resolvedConnectors().single().port),
            InspectorEndpoint("127.0.0.1", first.start()),
            InspectorEndpoint("127.0.0.1", second.start()),
        ))
        val client = LayoutInspectorClient({ lease }, attemptTimeoutMillis = 300)
        try {
            assertEquals(lease.endpoints[2], client.connect())
            assertEquals(lease.endpoints[2], client.connect())
            assertEquals(0, lease.closes.get())
            client.ping()
            coroutineScope {
                repeat(20) { launch { client.ping() } }
            }
        } finally {
            client.disconnect()
            second.stop()
            first.stop()
            wrongServer.stopSuspend(0, 1_000)
        }
        assertEquals(1, lease.closes.get())
    }

    @Test
    fun failedDiscoveryReleasesLeaseAndReportsDisconnection() = runBlocking<Unit> {
        val lease = TrackingLease(emptyList())
        val client = LayoutInspectorClient({ lease })
        try {
            assertFailsWith<InspectorConnectionException> { client.connect() }
            assertNotNull(assertIs<InspectorConnectionState.Disconnected>(client.state.value).cause)
            assertEquals(1, lease.closes.get())
            assertFailsWith<InspectorConnectionException> { client.ping() }
            client.disconnect()
            client.disconnect()
            assertEquals(1, lease.closes.get())
        } finally { client.disconnect() }
    }

    @Test
    fun disconnectDoesNotRescanAndExplicitConnectCanFindRestartedAgent() = runBlocking<Unit> {
        val agent = ComposeLayoutInspector(52710..52719)
        val acquisitions = AtomicInteger()
        val closes = AtomicInteger()
        val provider = InspectorConnectionProvider {
            acquisitions.incrementAndGet()
            object : InspectorConnectionLease {
                override val endpoints = (52710..52719).map { InspectorEndpoint("127.0.0.1", it) }
                override suspend fun close() { closes.incrementAndGet() }
            }
        }
        val client = LayoutInspectorClient(provider, heartbeatIntervalMillis = 50)
        try {
            agent.start()
            client.connect()
            agent.stop()
            withTimeout(5_000) { client.state.first { it is InspectorConnectionState.Disconnected } }
            agent.start()
            delay(200)
            assertEquals(1, acquisitions.get())
            assertEquals(1, closes.get())
            assertIs<InspectorConnectionState.Disconnected>(client.state.value)
            client.connect()
            client.ping()
            assertEquals(2, acquisitions.get())
        } finally { client.disconnect(); agent.stop() }
        assertEquals(2, closes.get())
    }

    @Test
    fun cancelledDiscoveryReleasesItsLease() = runBlocking<Unit> {
        val greeted = CompletableDeferred<Unit>()
        val silent = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                greeted.complete(Unit)
                incoming.receiveCatching()
            } }
        }.startSuspend()
        val lease = TrackingLease(listOf(InspectorEndpoint(
            "127.0.0.1", silent.engine.resolvedConnectors().single().port,
        )))
        val client = LayoutInspectorClient({ lease }, attemptTimeoutMillis = 30_000)
        try {
            val discovery = launch { client.connect() }
            withTimeout(5_000) { greeted.await() }
            discovery.cancelAndJoin()
            assertIs<InspectorConnectionState.Disconnected>(client.state.value)
            assertEquals(1, lease.closes.get())
        } finally { client.disconnect(); silent.stopSuspend(0, 1_000) }
    }

    @Test
    fun heartbeatDetectsAnUnresponsiveAgentAndFailsPendingRequests() = runBlocking<Unit> {
        val pings = AtomicInteger()
        val silent = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.GreetingAccepted)))
                for (frame in incoming) {
                    val message = InspectorProtocol.decode((frame as Frame.Text).readText())
                    if (message is InspectorMessage.Ping) pings.incrementAndGet()
                }
            } }
        }.startSuspend()
        val lease = TrackingLease(listOf(InspectorEndpoint(
            "127.0.0.1", silent.engine.resolvedConnectors().single().port,
        )))
        val client = LayoutInspectorClient(
            { lease }, heartbeatIntervalMillis = 50, responseTimeoutMillis = 100,
        )
        try {
            client.connect()
            withTimeout(3_000) { client.state.first { it is InspectorConnectionState.Disconnected } }
            assertTrue(pings.get() >= 1)
            assertEquals(1, lease.closes.get())
            assertNotNull(assertIs<InspectorConnectionState.Disconnected>(client.state.value).cause)
        } finally { client.disconnect(); silent.stopSuspend(0, 1_000) }
    }

    @Test
    fun aGreetingTimeoutIsSkippedAndProviderResourcesRemainUntilDisconnect() = runBlocking<Unit> {
        val silent = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                incoming.receiveCatching()
            } }
        }.startSuspend()
        val incompatible = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.Greeting)))
            } }
        }.startSuspend()
        val agent = ComposeLayoutInspector(52720..52729)
        val lease = TrackingLease(listOf(
            InspectorEndpoint("127.0.0.1", silent.engine.resolvedConnectors().single().port),
            InspectorEndpoint("127.0.0.1", incompatible.engine.resolvedConnectors().single().port),
            InspectorEndpoint("127.0.0.1", agent.start()),
        ))
        val client = LayoutInspectorClient({ lease }, attemptTimeoutMillis = 100)
        try {
            assertEquals(lease.endpoints.last(), withTimeout(5_000) { client.connect() })
            assertEquals(0, lease.closes.get())
            client.ping()
        } finally {
            client.disconnect()
            agent.stop()
            incompatible.stopSuspend(0, 1_000)
            silent.stopSuspend(0, 1_000)
        }
        assertEquals(1, lease.closes.get())
    }

    @Test
    fun healthyHeartbeatKeepsTheConnectionUsable() = runBlocking<Unit> {
        val pings = AtomicInteger()
        val heartbeatsReceived = CompletableDeferred<Unit>()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.GreetingAccepted)))
                for (frame in incoming) {
                    val ping = assertIs<InspectorMessage.Ping>(
                        InspectorProtocol.decode((frame as Frame.Text).readText())
                    )
                    if (pings.incrementAndGet() >= 3) heartbeatsReceived.complete(Unit)
                    val response = InspectorMessage.Pong(ping.id)
                    send(Frame.Text(InspectorProtocol.encode(response)))
                }
            } }
        }.startSuspend()
        val lease = TrackingLease(listOf(InspectorEndpoint(
            "127.0.0.1", server.engine.resolvedConnectors().single().port,
        )))
        val client = LayoutInspectorClient({ lease }, heartbeatIntervalMillis = 30)
        try {
            client.connect()
            withTimeout(3_000) { heartbeatsReceived.await() }
            client.ping()
            assertIs<InspectorConnectionState.Connected>(client.state.value)
        } finally { client.disconnect(); server.stopSuspend(0, 1_000) }
    }

    @Test
    fun remoteCloseFailsAnInFlightRequestAndReleasesTheLease() = runBlocking<Unit> {
        val requestReceived = CompletableDeferred<Unit>()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing { webSocket(InspectorProtocol.PATH) {
                incoming.receive()
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.GreetingAccepted)))
                incoming.receive()
                requestReceived.complete(Unit)
                incoming.receiveCatching()
            } }
        }.startSuspend()
        val lease = TrackingLease(listOf(InspectorEndpoint(
            "127.0.0.1", server.engine.resolvedConnectors().single().port,
        )))
        val client = LayoutInspectorClient({ lease }, responseTimeoutMillis = 30_000)
        try {
            client.connect()
            val pending = async { runCatching { client.ping() } }
            withTimeout(3_000) { requestReceived.await() }
            server.stopSuspend(0, 1_000)
            assertTrue(withTimeout(3_000) { pending.await() }.isFailure)
            withTimeout(3_000) { client.state.first { it is InspectorConnectionState.Disconnected } }
            assertEquals(1, lease.closes.get())
        } finally { client.disconnect(); server.stopSuspend(0, 1_000) }
    }

    @Test
    fun loopbackProviderSupportsDesktopAndSimulatorEndpoints() = runBlocking<Unit> {
        val lease = LoopbackConnectionProvider(52800..52802).acquire()
        try {
            assertEquals((52800..52802).map { InspectorEndpoint("127.0.0.1", it) }, lease.endpoints)
        } finally { lease.close() }
        assertFailsWith<IllegalArgumentException> { LoopbackConnectionProvider(0..1) }
        assertFailsWith<IllegalArgumentException> { LoopbackConnectionProvider(2..1) }
    }

    @Test
    fun simulatorProviderUsesAscendingLoopbackPorts() = runBlocking<Unit> {
        val lease = SimulatorConnectionProvider(52800..52802).acquire()
        try {
            assertEquals((52800..52802).map { InspectorEndpoint("127.0.0.1", it) }, lease.endpoints)
        } finally { lease.close() }
    }

    @Test
    fun invalidConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { SimulatorConnectionProvider(0..1) }
        assertFailsWith<IllegalArgumentException> { InspectorEndpoint("", 52500) }
        assertFailsWith<IllegalArgumentException> { InspectorEndpoint("localhost", 65536) }
        assertFailsWith<IllegalArgumentException> { LayoutInspectorClient(attemptTimeoutMillis = 0) }
        assertFailsWith<IllegalArgumentException> { LayoutInspectorClient(heartbeatIntervalMillis = 0) }
        assertFailsWith<IllegalArgumentException> { LayoutInspectorClient(responseTimeoutMillis = 0) }
    }

    private class TrackingLease(override val endpoints: List<InspectorEndpoint>) : InspectorConnectionLease {
        val closes = AtomicInteger()
        override suspend fun close() { closes.incrementAndGet() }
    }
}
