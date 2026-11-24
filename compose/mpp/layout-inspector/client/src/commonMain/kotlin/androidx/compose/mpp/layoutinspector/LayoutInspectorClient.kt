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
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface InspectorConnectionState {
    data object Connecting : InspectorConnectionState
    data class Connected(val endpoint: InspectorEndpoint) : InspectorConnectionState
    data class Disconnected(val cause: Throwable? = null) : InspectorConnectionState
}

class InspectorConnectionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Host client with explicit discovery and JSON heartbeat messages.
 *
 * Discovery runs only when [connect] is called. Session loss is reported through [state]; it
 * never starts another scan. Call [disconnect] when the client is no longer needed.
 */
class LayoutInspectorClient(
    private val connectionProvider: InspectorConnectionProvider = LoopbackConnectionProvider(),
    private val attemptTimeoutMillis: Long = 1_000,
    private val heartbeatIntervalMillis: Long = 5_000,
    private val responseTimeoutMillis: Long = 2_000,
) {
    init {
        require(attemptTimeoutMillis > 0)
        require(heartbeatIntervalMillis > 0)
        require(responseTimeoutMillis > 0)
    }

    private val mutex = Mutex()
    private var connection: Connection? = null
    private val mutableState = MutableStateFlow<InspectorConnectionState>(InspectorConnectionState.Disconnected())
    val state: StateFlow<InspectorConnectionState> = mutableState.asStateFlow()

    /**
     * Finds the first endpoint that completes the inspector greeting, or throws
     * [InspectorConnectionException]. Already-connected calls return the current endpoint.
     * Cancelling discovery releases the acquired lease and preserves coroutine cancellation.
     */
    suspend fun connect(): InspectorEndpoint = mutex.withLock {
        connection?.let { return@withLock it.endpoint }
        mutableState.value = InspectorConnectionState.Connecting
        var lease: InspectorConnectionLease? = null
        var httpClient: HttpClient? = null
        try {
            lease = connectionProvider.acquire()
            httpClient = HttpClient {
                install(WebSockets) { maxFrameSize = InspectorProtocol.MAX_MESSAGE_BYTES }
            }
            var lastFailure: Exception? = null
            for (endpoint in lease.endpoints) {
                var session: DefaultClientWebSocketSession? = null
                try {
                    withTimeout(attemptTimeoutMillis) {
                        session = httpClient.webSocketSession {
                            url {
                                protocol = URLProtocol.WS
                                host = endpoint.host
                                port = endpoint.port
                                encodedPath = InspectorProtocol.PATH
                            }
                        }
                        val messages: InspectorSession = KtorInspectorSession(session)
                        messages.send(InspectorMessage.Greeting)
                        val response = messages.receive()
                        if (response != InspectorMessage.GreetingAccepted) {
                            throw InspectorConnectionException("Endpoint did not accept the inspector greeting")
                        }
                    }
                    val connected = Connection(endpoint, session!!, httpClient, lease)
                    connection = connected
                    mutableState.value = InspectorConnectionState.Connected(endpoint)
                    connected.start()
                    return@withLock endpoint
                } catch (failure: Exception) {
                    session?.cancel()
                    // Closing a WebSocket can cancel its channels while connect() is still active.
                    // Only cancellation of the calling coroutine should abort the scan.
                    currentCoroutineContext().ensureActive()
                    lastFailure = failure
                }
            }
            throw InspectorConnectionException("No compatible inspector agent found", lastFailure)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                httpClient?.close()
                try { lease?.close() } catch (cleanupFailure: Throwable) { failure.addSuppressed(cleanupFailure) }
                mutableState.value = InspectorConnectionState.Disconnected(failure)
            }
            if (failure is CancellationException || failure is InspectorConnectionException) throw failure
            throw InspectorConnectionException("Inspector discovery failed", failure)
        }
    }

    suspend fun ping(): Unit = currentConnection().ping()

    /** Releases the session and any forwarding resources; safe to call repeatedly. */
    suspend fun disconnect(): Unit = mutex.withLock {
        val current = connection ?: return@withLock
        connection = null
        val failure = current.dispose(InspectorConnectionException("Inspector client disconnected"))
        mutableState.value = InspectorConnectionState.Disconnected(failure)
    }

    private suspend fun currentConnection(): Connection = mutex.withLock {
        connection ?: throw InspectorConnectionException("Inspector client is disconnected")
    }

    private suspend fun connectionEnded(current: Connection, cause: Throwable) = mutex.withLock {
        if (connection !== current) return@withLock
        connection = null
        val cleanupFailure = current.dispose(cause)
        if (cleanupFailure != null) cause.addSuppressed(cleanupFailure)
        mutableState.value = InspectorConnectionState.Disconnected(cause)
    }

    private inner class Connection(
        val endpoint: InspectorEndpoint,
        val session: DefaultClientWebSocketSession,
        val httpClient: HttpClient,
        val lease: InspectorConnectionLease,
    ) {
        private val messages: InspectorSession = KtorInspectorSession(session)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val requestsMutex = Mutex()
        private val pendingPings = mutableMapOf<Long, CompletableDeferred<Unit>>()
        private var nextId = 0L
        private var ended = false

        fun start() {
            scope.launch {
                val failure = try {
                    while (true) {
                        val response = messages.receive() ?: break
                        if (response !is InspectorMessage.Pong) {
                            throw InspectorConnectionException("Unexpected inspector response: $response")
                        }
                        // A late pong is ignored after its ping is cancelled or times out.
                        requestsMutex.withLock { pendingPings[response.id]?.complete(Unit) }
                    }
                    InspectorConnectionException("Inspector agent closed the connection")
                } catch (failure: Throwable) { failure }
                connectionEnded(this@Connection, failure)
            }
            scope.launch {
                try {
                    while (isActive) {
                        delay(heartbeatIntervalMillis)
                        ping()
                    }
                } catch (failure: Throwable) {
                    connectionEnded(this@Connection, failure)
                }
            }
        }

        suspend fun ping() {
            val response = CompletableDeferred<Unit>()
            val id = requestsMutex.withLock {
                if (ended) throw InspectorConnectionException("Inspector client is disconnected")
                (++nextId).also { pendingPings[it] = response }
            }
            try {
                withTimeout(responseTimeoutMillis) {
                    messages.send(InspectorMessage.Ping(id))
                    response.await()
                }
            } finally {
                // Cleanup must also run when the request's caller is cancelled.
                withContext(NonCancellable) { requestsMutex.withLock { pendingPings.remove(id) } }
            }
        }

        suspend fun dispose(cause: Throwable): Throwable? = withContext(NonCancellable) {
            requestsMutex.withLock {
                ended = true
                pendingPings.values.forEach { it.completeExceptionally(cause) }
                pendingPings.clear()
            }
            scope.cancel()
            session.cancel()
            httpClient.close()
            try { lease.close(); null } catch (failure: Throwable) { failure }
        }
    }
}
