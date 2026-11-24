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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Runs the target side of the inspector protocol on an already established session.
 *
 * The host sends the first greeting regardless of which endpoint opened the connection.
 * [run] suspends for the lifetime of the session and closes it on return, failure, or cancellation.
 */
fun interface InspectorAgent {
    suspend fun run(session: InspectorSession)
}

/** Stateless agent protocol implementation. Owns no listener, endpoint, or connection provider. */
class DefaultInspectorAgent(private val handshakeTimeoutMillis: Long = 2_000) : InspectorAgent {
    init { require(handshakeTimeoutMillis > 0) }

    override suspend fun run(session: InspectorSession) {
        var closeCause: Throwable? = null
        try {
            val greeting = withTimeout(handshakeTimeoutMillis) { session.receive() } ?: return
            if (greeting != InspectorMessage.Greeting) {
                throw InspectorProtocolException("Expected an inspector greeting")
            }
            session.send(InspectorMessage.GreetingAccepted)
            while (true) {
                val message = session.receive() ?: break
                if (message !is InspectorMessage.Ping) {
                    throw InspectorProtocolException("Expected ping")
                }
                session.send(InspectorMessage.Pong(message.id))
            }
        } catch (failure: InspectorProtocolException) {
            closeCause = failure
            session.send(InspectorMessage.Error(failure.message ?: "Invalid inspector message"))
        } catch (failure: Throwable) {
            closeCause = failure
            throw failure
        } finally {
            withContext(NonCancellable) {
                try { session.close(closeCause) } catch (failure: Throwable) {
                    if (closeCause == null) throw failure
                    closeCause.addSuppressed(failure)
                }
            }
        }
    }
}
