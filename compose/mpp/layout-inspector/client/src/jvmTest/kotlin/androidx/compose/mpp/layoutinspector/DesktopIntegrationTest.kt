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

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopIntegrationTest {
    @Test
    fun hostDiscoversAgentInAnotherJvmAndReportsItsShutdown() = runBlocking<Unit> {
        val classpath = requireNotNull(System.getProperty("layoutInspector.desktopFixtureClasspath"))
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpath, DesktopAgentFixture::class.java.name,
        ).redirectErrorStream(true).start()
        val ready = CompletableFuture<Int>()
        val output = StringBuffer()
        val reader = thread(isDaemon = true, name = "inspector-desktop-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.append(line).append('\n')
                    if (line.startsWith("INSPECTOR_READY:")) {
                        ready.complete(line.substringAfter(':').toInt())
                    }
                }
            }
            ready.completeExceptionally(IllegalStateException("Desktop agent exited: $output"))
        }
        val client = LayoutInspectorClient(
            LoopbackConnectionProvider(53000..53009), heartbeatIntervalMillis = 100,
        )
        try {
            val port = try {
                ready.get(10, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                throw AssertionError("Desktop agent did not become ready: $output", failure)
            }
            assertEquals(InspectorEndpoint("127.0.0.1", port), withTimeout(5_000) { client.connect() })
            client.ping()
            coroutineScope {
                repeat(10) { launch { client.ping() } }
            }
            process.outputStream.close()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Desktop agent did not stop: $output")
            assertEquals(0, process.exitValue(), output.toString())
            withTimeout(5_000) { client.state.first { it is InspectorConnectionState.Disconnected } }
            assertIs<InspectorConnectionState.Disconnected>(client.state.value)
        } finally {
            client.disconnect()
            process.outputStream.close()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
            reader.join(1_000)
        }
    }
}
