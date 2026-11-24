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
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class OutboundInspectorAgentTest {
    @Test
    fun agentCanConnectToAHostSideServer() = runBlocking<Unit> {
        val inspected = CompletableDeferred<Unit>()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing {
                webSocket(InspectorProtocol.PATH) {
                    val session = KtorInspectorSession(this)
                    try {
                        session.send(InspectorMessage.Greeting)
                        assertEquals(InspectorMessage.GreetingAccepted, session.receive())
                        session.send(InspectorMessage.Ping(11))
                        assertEquals(InspectorMessage.Pong(11), session.receive())
                        inspected.complete(Unit)
                    } catch (failure: Throwable) {
                        inspected.completeExceptionally(failure)
                    } finally { session.close() }
                }
            }
        }.startSuspend()
        val client = HttpClient(ClientCIO) { install(ClientWebSockets) }
        try {
            val port = server.engine.resolvedConnectors().single().port
            val running = async {
                client.webSocket("ws://127.0.0.1:$port${InspectorProtocol.PATH}") {
                    val agent: InspectorAgent = DefaultInspectorAgent()
                    agent.run(KtorInspectorSession(this))
                }
            }
            withTimeout(5_000) { inspected.await(); running.await() }
        } finally { client.close(); server.stopSuspend(0, 1_000) }
    }
}
