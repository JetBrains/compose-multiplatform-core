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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object InspectorProtocol {
    const val PATH: String = "/compose-layout-inspector"
    val ports: IntRange = 52500..52509
    const val MAX_MESSAGE_BYTES: Long = 1_048_576

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun encode(message: InspectorMessage): String = json.encodeToString(message)
    fun decode(text: String): InspectorMessage = json.decodeFromString(text)
}

@Serializable
sealed interface InspectorMessage {

    @Serializable
    @SerialName("greeting")
    data object Greeting : InspectorMessage

    @Serializable
    @SerialName("greetingAccepted")
    data object GreetingAccepted : InspectorMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val id: Long) : InspectorMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val id: Long) : InspectorMessage

    @Serializable
    @SerialName("error")
    data class Error(val reason: String) : InspectorMessage
}
