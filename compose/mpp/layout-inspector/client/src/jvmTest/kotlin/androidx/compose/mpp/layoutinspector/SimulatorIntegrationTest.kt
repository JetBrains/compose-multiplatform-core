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

import java.nio.file.Files
import java.nio.file.Path
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

/** Runs only through the opt-in simulatorIntegrationTest task on macOS. */
class SimulatorIntegrationTest {
    @Test
    fun jvmHostDiscoversSimulatorAgentAndExchangesMessages() = runBlocking<Unit> {
        val executable = requireNotNull(System.getProperty("layoutInspector.simulatorAgentExecutable"))
        check(Files.isExecutable(Path.of(executable))) { "Simulator fixture is missing: $executable" }
        val simulator = System.getProperty("layoutInspector.simulatorUdid", "booted")
        val directory = Files.createTempDirectory("layout-inspector-integration").toFile()
        val stopFile = directory.resolve("stop")
        val process = ProcessBuilder(
            "xcrun", "simctl", "spawn", simulator, executable,
            "--ktest_filter=androidx.compose.mpp.layoutinspector.SimulatorAgentFixture.serveHostClient",
        ).apply {
            redirectErrorStream(true)
            environment()["SIMCTL_CHILD_LAYOUT_INSPECTOR_FIXTURE_STOP_FILE"] = stopFile.absolutePath
        }.start()
        val ready = CompletableFuture<Int>()
        val output = StringBuffer()
        val reader = thread(isDaemon = true, name = "inspector-simulator-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.append(line).append('\n')
                    if (line.startsWith("INSPECTOR_READY:")) {
                        ready.complete(line.substringAfter(':').toInt())
                    }
                }
            }
            ready.completeExceptionally(IllegalStateException("Simulator agent exited: $output"))
        }
        val client = LayoutInspectorClient(
            SimulatorConnectionProvider(52900..52909), heartbeatIntervalMillis = 100,
        )
        try {
            val port = try {
                ready.get(25, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                throw AssertionError("Simulator fixture did not become ready ($executable): $output", failure)
            }
            assertEquals(InspectorEndpoint("127.0.0.1", port), client.connect())
            client.ping()
            coroutineScope {
                repeat(10) { launch { client.ping() } }
            }
            stopFile.writeText("stop")
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Agent did not stop: $output")
            assertEquals(0, process.exitValue(), output.toString())
            withTimeout(5_000) { client.state.first { it is InspectorConnectionState.Disconnected } }
            assertIs<InspectorConnectionState.Disconnected>(client.state.value)
        } finally {
            client.disconnect()
            stopFile.writeText("stop")
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            reader.join(1_000)
            directory.deleteRecursively()
        }
    }
}
