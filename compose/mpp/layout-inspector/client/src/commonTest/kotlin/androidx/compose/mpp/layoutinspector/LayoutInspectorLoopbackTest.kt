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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LayoutInspectorLoopbackTest {
    @Test
    fun clientConnectsAndPingsAgentInSameProcess() = runBlocking<Unit> {
        val ports = 53010..53019
        val agent = ComposeLayoutInspector(ports)
        val client = LayoutInspectorClient(LoopbackConnectionProvider(ports))
        try {
            val port = agent.start()
            assertEquals(InspectorEndpoint("127.0.0.1", port), client.connect())
            client.ping()

            agent.stop()
            withTimeout(5_000) {
                client.state.first { it is InspectorConnectionState.Disconnected }
            }
        } finally {
            try {
                client.disconnect()
            } finally {
                agent.stop()
            }
        }
    }
}
