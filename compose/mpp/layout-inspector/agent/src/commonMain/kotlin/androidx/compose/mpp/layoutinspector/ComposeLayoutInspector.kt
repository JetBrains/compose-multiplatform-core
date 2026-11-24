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
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-process transport agent for JVM, native macOS on Apple Silicon, and iOS Simulators.
 * No layout data is collected by this PoC.
 *
 * Keep one instance for the app's lifetime and call [stop] when inspection is no longer needed.
 * The server listens only on IPv4 loopback and starts only when [start] is called.
 * [InspectorAgent] owns the protocol handling; this facade only supplies accepted sessions.
 */
class ComposeLayoutInspector(
    private val ports: IntRange = InspectorProtocol.ports,
    private val agent: InspectorAgent = DefaultInspectorAgent(),
) {
    init {
        require(!ports.isEmpty() && ports.first >= 1 && ports.last <= 65535)
    }

    private val mutex = Mutex()
    private var runningServer: RunningServer? = null

    /** Starts on the first available port; repeated calls return the same port. */
    suspend fun start(): Int = mutex.withLock {
        val server = runningServer
        if (server != null) {
            return@withLock server.port
        }
        var lastFailure: Throwable? = null
        for (port in ports) {
            val attempt = tryStartServer(port)
            val started = attempt.getOrNull()
            if (started != null) {
                println("Started on port $port")
                this@ComposeLayoutInspector.runningServer = started
                return@withLock started.port
            }
            lastFailure = attempt.exceptionOrNull()
        }
        throw IllegalStateException("No inspector port available in $ports", lastFailure)
    }

    /** Stops the server and its sessions. Safe before start and on repeated calls. */
    suspend fun stop(): Unit = mutex.withLock {
        val server = runningServer ?: return@withLock
        runningServer = null
        server.stop()
    }

    private suspend fun tryStartServer(port: Int): Result<RunningServer> {
        // CIO reports startup failures both through startSuspend and its engine job.
        // We handle the failure from startSuspend below.
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
        )
        val server = scope.embeddedServer(CIO, host = "127.0.0.1", port = port) {
            install(WebSockets) { maxFrameSize = InspectorProtocol.MAX_MESSAGE_BYTES }
            routing {
                webSocket(InspectorProtocol.PATH) { agent.run(KtorInspectorSession(this)) }
            }
        }
        val candidate = RunningServer(port, server, scope)
        return try {
            server.startSuspend()
            Result.success(candidate)
        } catch (failure: Exception) {
            candidate.stop()
            // CIO can report a bind failure as cancellation of its engine job.
            // Only cancellation of the calling coroutine should abort the scan.
            currentCoroutineContext().ensureActive()
            Result.failure(failure)
        }
    }

    private class RunningServer(
        val port: Int,
        private val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
        private val scope: CoroutineScope,
    ) {
        suspend fun stop(): Unit = withContext(NonCancellable) {
            scope.cancel()
            server.stopSuspend(0, 1_000)
        }
    }
}
