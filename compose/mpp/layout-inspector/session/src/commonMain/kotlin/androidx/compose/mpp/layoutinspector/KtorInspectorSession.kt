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

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException

/**
 * Adapts either an accepted or an outbound Ktor WebSocket to JSON inspector messages.
 * Use a default Ktor session, which handles control frames and reassembles fragmented messages.
 */
class KtorInspectorSession(private val socket: WebSocketSession) : InspectorSession {

    override suspend fun receive(): InspectorMessage? {
        val result = socket.incoming.receiveCatching()
        result.exceptionOrNull()?.let { throw it }
        val frame = result.getOrNull() ?: return null
        if (frame !is Frame.Text) throw InspectorProtocolException("Expected a JSON text message")
        return try {
            InspectorProtocol.decode(frame.readText())
        } catch (failure: SerializationException) {
            throw InspectorProtocolException("Invalid inspector JSON", failure)
        }
    }

    override suspend fun send(message: InspectorMessage) {
        socket.send(Frame.Text(InspectorProtocol.encode(message)))
    }

    override suspend fun close(cause: Throwable?) {
        val reason = if (cause is InspectorProtocolException) {
            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid inspector message")
        } else {
            CloseReason(CloseReason.Codes.NORMAL, "")
        }
        socket.close(reason)
    }
}
