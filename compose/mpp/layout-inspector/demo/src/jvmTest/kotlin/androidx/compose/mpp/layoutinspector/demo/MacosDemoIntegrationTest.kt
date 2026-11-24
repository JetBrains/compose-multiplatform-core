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

package androidx.compose.mpp.layoutinspector.demo

import androidx.compose.mpp.layoutinspector.InspectorConnectionState
import androidx.compose.mpp.layoutinspector.InspectorEndpoint
import androidx.compose.mpp.layoutinspector.LayoutInspectorClient
import androidx.compose.mpp.layoutinspector.LoopbackConnectionProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MacosDemoIntegrationTest {
    @Test
    fun jvmClientPingsNativeMacosDemo() = runBlocking<Unit> {
        val executable = requireNotNull(System.getProperty("layoutInspector.macosDemoExecutable"))
        val process = ProcessBuilder(executable, "--stop-on-stdin").redirectErrorStream(true).start()
        val ready = CompletableFuture<Int>()
        val output = StringBuffer()
        val reader = thread(isDaemon = true, name = "layout-inspector-demo-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.appendLine(line)
                    if (line.startsWith("INSPECTOR_READY:")) {
                        ready.complete(line.substringAfter(':').toInt())
                    }
                }
            }
            ready.completeExceptionally(IllegalStateException("macOS demo exited before announcing its port"))
        }

        try {
            val port = try {
                ready.get(15, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                throw AssertionError("macOS demo did not start: $output", failure)
            }
            val client = LayoutInspectorClient(LoopbackConnectionProvider(port..port))
            try {
                withTimeout(5_000) {
                    assertEquals(InspectorEndpoint("127.0.0.1", port), client.connect())
                    client.ping()
                }
            } finally {
                client.disconnect()
            }
            assertIs<InspectorConnectionState.Disconnected>(client.state.value)

            process.outputStream.close()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "macOS demo did not exit: $output")
            assertEquals(0, process.exitValue(), output.toString())
        } finally {
            process.outputStream.close()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
            reader.join(1_000)
        }
    }
}
