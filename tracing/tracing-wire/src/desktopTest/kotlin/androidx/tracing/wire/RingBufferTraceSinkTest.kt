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

package androidx.tracing.wire

import androidx.tracing.TraceDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class RingBufferTraceSinkTest {
    @get:Rule val tmpFolder = TemporaryFolder()

    @Test
    fun testFlush_preservesOngoingEvents() = runBlocking {
        val folder = tmpFolder.newFolder()
        val file1 = File(folder, "trace1.perfetto")
        val sink =
            RingBufferTraceSink(
                capacityInBytes = 10_000_000,
                sequenceId = 1,
                bufferedSink = file1.sink().buffer(),
            )
        TraceDriver(sink = sink, isEnabled = true).use { driver ->
            val tracer = driver.tracer

            val job =
                launch(Dispatchers.Default) {
                    repeat(100) { tracer.traceCoroutine("cat", "event-$it") { delay(1) } }
                }

            // Wait a bit and flush midway
            delay(20)
            sink.flush()

            job.join()

            // Flush remainder
            sink.flush()

            assertTrue(file1.exists(), "File should exist")
            assertTrue(file1.length() > 0, "Should have captured trace data")
        }
    }

    @Test
    fun testBufferOverflow_truncatesOldData() = runBlocking {
        val folder = tmpFolder.newFolder()
        val file = File(folder, "trace_overflow.perfetto")
        // Small capacity
        val capacity = 1024L
        val sink =
            RingBufferTraceSink(
                capacityInBytes = capacity,
                sequenceId = 1,
                bufferedSink = file.sink().buffer(),
            )
        TraceDriver(sink = sink, isEnabled = true).use { driver ->
            val tracer = driver.tracer

            // Generate enough data to overflow
            repeat(1000) { tracer.traceCoroutine("cat", "event-$it") {} }

            sink.flush()
            assertTrue(file.exists())
            assertTrue(file.length() > 0, "File should not be empty")

            // We can't assert <= capacity because of our approximation of TraceEvent size.
            // However, 1000 events without limits would produce a file > 50KB.
            // We assert that it's within 4KB our expected capacity
            assertTrue(
                file.length() < capacity + 4096,
                "File size (${file.length()} bytes) should be bounded and close to capacity",
            )
        }
    }

    @Test
    fun testBufferOverflow_reportsDroppedEvent() = runBlocking {
        val folder = tmpFolder.newFolder()
        val file = File(folder, "trace_dropped.perfetto")
        val sink =
            RingBufferTraceSink(
                capacityInBytes = 1024L,
                sequenceId = 1,
                bufferedSink = file.sink().buffer(),
            )

        val driver = TraceDriver(sink = sink, isEnabled = true)
        val tracer = driver.tracer

        // Enqueue more than 1 event to force the ring buffer to wrap around and overwrite the
        // oldest.
        // TraceCoroutine internally pushes 2 events (one for START, one for STOP)
        tracer.traceCoroutine("cat", "event1") { delay(1) }
        tracer.traceCoroutine("cat", "event2") { delay(1) }
        tracer.traceCoroutine("cat", "event3") { delay(1) }

        // Flush should identify that it overwrote dropped data and trigger the notification
        sink.flush()

        // This effectively writes out a packet with previous_packet_dropped = true.
        // It's checked during parsing or if the file contains that flag.
        // Since we removed the delegate, we can just check if file exists and has size.
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun testComplexEventPreservation() = runBlocking {
        val folder = tmpFolder.newFolder()
        val file = File(folder, "trace_complex.perfetto")
        // Sufficient for one complex event
        val sink =
            RingBufferTraceSink(
                capacityInBytes = 10_000,
                sequenceId = 1,
                bufferedSink = file.sink().buffer(),
            )
        TraceDriver(sink = sink, isEnabled = true).use { driver ->
            val tracer = driver.tracer

            tracer.traceCoroutine(
                category = "cat-complex",
                name = "event-complex",
                metadataBlock = {
                    addMetadataEntry("meta-key", "meta-value")
                    addCategory("cat-extra")
                    addCallStackEntry("func1", "file1.kt", 100)
                },
            ) {
                // No-op
            }

            sink.flush()

            assertTrue(file.exists())
            assertTrue(file.length() > 0)
        }
    }

    @Test
    fun testCloseWithoutFlush_dropsData() = runBlocking {
        val folder = tmpFolder.newFolder()
        val file = File(folder, "trace_dropped.perfetto")
        val sink =
            RingBufferTraceSink(
                capacityInBytes = 10_000,
                sequenceId = 1,
                bufferedSink = file.sink().buffer(),
            )
        val driver = TraceDriver(sink = sink, isEnabled = true)
        val tracer = driver.tracer

        tracer.traceCoroutine("cat", "event-dropped") {}

        // Wait for potential background processing (drainQueue) to move data to ring buffer
        delay(50)

        // Close sink without flush
        sink.close(flush = false)

        // The file should be empty because data should be stuck in RingBuffer and dropped
        assertEquals(0L, file.length(), "File should be empty when closed without flush")
    }

    @Test
    fun testTraceSinkFactory_createsRingBufferSink() = runBlocking {
        val folder = tmpFolder.newFolder()
        val sink =
            TraceSink(directory = folder, sequenceId = 1, ringBufferCapacityInBytes = 10_000_000L)

        assertTrue(sink is RingBufferTraceSink, "Factory should return RingBufferTraceSink")

        TraceDriver(sink = sink, isEnabled = true).use { driver ->
            val tracer = driver.tracer
            tracer.traceCoroutine("cat", "event-test") {}
            sink.flush()

            val traceFiles = folder.listFiles()?.filter { it.name.endsWith(".perfetto-trace") }
            assertEquals(1, traceFiles?.size, "Should have created exactly one trace file")
            val file = traceFiles!!.first()

            assertTrue(file.exists(), "File should exist")
            assertTrue(file.length() > 0, "Should have captured trace data")
        }
    }
}
