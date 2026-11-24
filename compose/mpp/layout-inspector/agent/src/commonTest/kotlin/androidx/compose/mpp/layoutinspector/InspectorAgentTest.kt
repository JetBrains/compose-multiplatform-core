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

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class InspectorAgentTest {
    @Test
    fun startAndStopAreIdempotentAndThePortCanBeReused() = runBlocking<Unit> {
        val agent = ComposeLayoutInspector(52600..52609)
        agent.stop()
        try {
            val port = agent.start()
            assertEquals(listOf(port, port), coroutineScope {
                listOf(async { agent.start() }, async { agent.start() }).awaitAll()
            })
            agent.stop()
            agent.stop()
            val replacement = ComposeLayoutInspector(port..port)
            try { assertEquals(port, replacement.start()) } finally { replacement.stop() }
        } finally { agent.stop() }
    }

    @Test
    fun occupiedPortsAreSkippedAndExhaustionIsReported() = runBlocking<Unit> {
        val first = ComposeLayoutInspector(52610..52619)
        val second = ComposeLayoutInspector(52610..52619)
        try {
            val firstPort = first.start()
            assertTrue(second.start() > firstPort)
            val blocked = ComposeLayoutInspector(firstPort..firstPort)
            try {
                assertFailsWith<IllegalStateException> { blocked.start() }
            } finally { blocked.stop() }
        } finally { second.stop(); first.stop() }
    }

    @Test
    fun greetingThenPingUsesJsonMessages() = runBlocking<Unit> {
        withAgent { port, client ->
            client.webSocket("ws://127.0.0.1:$port${InspectorProtocol.PATH}") {
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.Greeting)))
                assertEquals(InspectorMessage.GreetingAccepted, receiveMessage())
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.Ping(2))))
                assertEquals(InspectorMessage.Pong(2), receiveMessage())
            }
        }
    }

    @Test
    fun invalidGreetingAndMalformedJsonAreRejectedWithoutStoppingAgent() = runBlocking<Unit> {
        withAgent { port, client ->
            for (message in listOf(
                InspectorProtocol.encode(InspectorMessage.Ping(1)),
                "not JSON",
            )) {
                client.webSocket("ws://127.0.0.1:$port${InspectorProtocol.PATH}") {
                    send(Frame.Text(message))
                    assertIs<InspectorMessage.Error>(receiveMessage())
                    assertTrue(withTimeout(2_000) { incoming.receiveCatching().isClosed })
                }
            }
            client.webSocket("ws://127.0.0.1:$port${InspectorProtocol.PATH}") {
                send(Frame.Text(InspectorProtocol.encode(InspectorMessage.Greeting)))
                assertEquals(InspectorMessage.GreetingAccepted, receiveMessage())
            }
        }
    }

    @Test
    fun silentPeersCannotKeepTheGreetingOpenForever() = runBlocking<Unit> {
        withAgent(agent = DefaultInspectorAgent(handshakeTimeoutMillis = 100)) { port, client ->
            client.webSocket("ws://127.0.0.1:$port${InspectorProtocol.PATH}") {
                withTimeout(2_000) {
                    // The server may send an error before closing.
                    while (!incoming.receiveCatching().isClosed) { }
                }
            }
        }
    }

    @Test
    fun invalidConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { ComposeLayoutInspector(2..1) }
        assertFailsWith<IllegalArgumentException> { ComposeLayoutInspector(0..10) }
        assertFailsWith<IllegalArgumentException> { ComposeLayoutInspector(65535..65536) }
    }

    private suspend fun io.ktor.websocket.WebSocketSession.receiveMessage(): InspectorMessage =
        withTimeout(2_000) {
            InspectorProtocol.decode((incoming.receive() as Frame.Text).readText())
        }

    private suspend fun withAgent(
        agent: InspectorAgent = DefaultInspectorAgent(),
        block: suspend (Int, HttpClient) -> Unit,
    ) {
        val inspector = ComposeLayoutInspector(52620..52629, agent = agent)
        val client = HttpClient(CIO) { install(WebSockets) }
        try { block(inspector.start(), client) } finally { client.close(); inspector.stop() }
    }
}
