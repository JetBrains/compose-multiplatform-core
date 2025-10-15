/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.tracing.driver.wire

import androidx.tracing.driver.AtomicInteger
import androidx.tracing.driver.DEFAULT_LONG
import androidx.tracing.driver.PooledTracePacketArray
import androidx.tracing.driver.TRACE_PACKET_BUFFER_SIZE
import androidx.tracing.driver.TRACE_PACKET_POOL_ARRAY_POOL_SIZE
import androidx.tracing.driver.TraceContext
import androidx.tracing.driver.TraceSink
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.blackholeSink
import okio.buffer
import perfetto.protos.MutableTracePacket
import perfetto.protos.MutableTrackDescriptor
import perfetto.protos.MutableTrackEvent

class TestSink : TraceSink() {
    internal val packets = mutableListOf<MutableTracePacket>()

    override fun enqueue(pooledPacketArray: PooledTracePacketArray) {
        pooledPacketArray.forEach { it ->
            packets.add(
                MutableTracePacket(
                        timestamp = DEFAULT_LONG,
                        trusted_packet_sequence_id = 1, // arbitrary value
                    )
                    .apply {
                        track_event = MutableTrackEvent(track_uuid = DEFAULT_LONG)
                        // slightly abuse this function by passing in freshly allocated objects each
                        // time so this test can keep ref to all packets created, and doesn't need
                        // to bother with proto serialization,
                        WireTraceEventSerializer.updateScratchPacketFromTraceEvent(
                            event = it,
                            reportDroppedTraceEvent = false,
                            scratchTracePacket = this,
                            // this is mostly dropped and not used, but we don't care about extra
                            // allocations during this test
                            scratchTrackDescriptor = MutableTrackDescriptor(),
                            // this is sometimes not used, but we don't care about extra
                            // allocations during this test
                            scratchTrackEvent = MutableTrackEvent(track_uuid = DEFAULT_LONG),
                            // We don't reset annotations in tests. Allocations are okay here.
                            scratchAnnotations = mutableListOf(),
                            scratchAnnotationIndex = AtomicInteger(-1),
                        )
                    }
            )
        }
    }

    override fun onDroppedTraceEvent() {
        // Does nothing
    }

    override fun flush() {
        // Does nothing
    }

    override fun close() {
        // Does nothing
    }
}

class TracingTest {
    private val sink = TestSink()
    private val context: TraceContext = TraceContext(sink = sink, isEnabled = true)

    @Test
    internal fun testProcessTrackEvents() {
        context.use {
            val process = context.getOrCreateProcessTrack(id = 1, name = "process")
            val thread = process.getOrCreateThreadTrack(1, "thread")
            thread.trace("section") {}
        }
        assertTrue(sink.packets.size == 4)
        assertNotNull(sink.packets.find { it.track_descriptor?.process?.process_name == "process" })
        assertNotNull(sink.packets.find { it.track_descriptor?.thread?.thread_name == "thread" })
        sink.firstStartStopWithName("section") { start, end ->
            assertTrue { start.track_event!!.categories.isEmpty() }
        }
    }

    @Test
    internal fun testCounterTrackEvents() {
        context.use {
            val process = context.getOrCreateProcessTrack(id = 1, name = "process")
            val counter = process.getOrCreateCounterTrack("counter")
            counter.setCounter(10L)
        }
        assertTrue(sink.packets.size == 3)
    }

    @Test
    internal fun testAsyncEventsInProcess() {
        context.use {
            val process = context.getOrCreateProcessTrack(id = 1, name = "process")
            process.trace("section") {}
            process.trace("section2") {}
        }
        assertTrue(sink.packets.size == 5)
        assertNotNull(sink.packets.find { it.track_descriptor?.process?.process_name == "process" })
        listOf("section", "section2").forEach { name ->
            sink.firstStartStopWithName(name) { start, end ->
                assertTrue { start.track_event!!.categories.isEmpty() }
            }
        }
    }

    @Test
    internal fun testAsyncEventsWithFlows() = runTest {
        context.use {
            with(context) {
                val process = getOrCreateProcessTrack(id = 1, name = "process")
                with(process) {
                    traceCoroutine("service") {
                        coroutineScope {
                            async { traceCoroutine(name = "method1") { delay(10) } }.await()
                            async { traceCoroutine(name = "method2") { delay(40) } }.await()
                        }
                    }
                }
            }
        }
        assertTrue { sink.packets.isNotEmpty() }
        val (start, _) = sink.firstStartStopWithName("service")
        val flowId = start.track_event?.flow_ids?.first()
        assertNotNull(flowId) { "Packet $start does not include a flow_id" }
        val (method1, _) = sink.firstStartStopWithName("method1")
        val (method2, _) = sink.firstStartStopWithName("method2")
        assertContains(method1.track_event?.flow_ids ?: emptyList(), flowId)
        assertContains(method2.track_event?.flow_ids ?: emptyList(), flowId)
    }

    @Test
    internal fun testSuspendAndResume() = runTest {
        context.use {
            with(context) {
                val process = getOrCreateProcessTrack(id = 1, name = "process")
                with(process) {
                    traceCoroutine("service") {
                        coroutineScope {
                            async { traceCoroutine(name = "method1") { delay(10) } }.await()
                        }
                    }
                }
            }
        }
        assertTrue { sink.packets.isNotEmpty() }
        // We should have a balanced number of begin and end events.
        val starts =
            sink.packets.filter { packet ->
                packet.track_event?.type == MutableTrackEvent.Type.TYPE_SLICE_BEGIN
            }
        val ends =
            sink.packets.filter { packet ->
                packet.track_event?.type == MutableTrackEvent.Type.TYPE_SLICE_END
            }
        assertTrue { starts.size == ends.size }
    }

    @Test
    internal fun testDroppedPackets() {
        val dispatcher = StandardTestDispatcher()
        // Use a real sink to test for dropped packets.
        val sink =
            TraceSinkDelegate(
                sink =
                    TraceSink(
                        sequenceId = 1,
                        bufferedSink = blackholeSink().buffer(),
                        // Use a test dispatcher to control exactly when trace events are being
                        // drained from the queue.
                        coroutineContext = dispatcher,
                    )
            )
        val context = TraceContext(sink = sink, isEnabled = true)
        // Don't use context.use { ... } here given it will wait indefinitely because the
        // queue won't be empty unless we advance the test dispatcher.
        val process = context.getOrCreateProcessTrack(id = 1, name = "process")
        repeat(TRACE_PACKET_POOL_ARRAY_POOL_SIZE) {
            repeat(16) {
                process.trace("section") {} // 2 events per loop.
            }
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue { sink.reportDroppedTracePacket }
        assertEquals(
            TRACE_PACKET_POOL_ARRAY_POOL_SIZE * TRACE_PACKET_BUFFER_SIZE,
            sink.packetCountOnDroppedTracePacket,
        )
    }

    internal class TraceSinkDelegate(private val sink: TraceSink) : TraceSink() {
        internal var reportDroppedTracePacket = false
        internal var packetCount: Int = 0
        internal var packetCountOnDroppedTracePacket = 0

        override fun enqueue(pooledPacketArray: PooledTracePacketArray) {
            sink.enqueue(pooledPacketArray)
            packetCount += pooledPacketArray.packets.size
        }

        override fun onDroppedTraceEvent() {
            reportDroppedTracePacket = true
            packetCountOnDroppedTracePacket = packetCount
        }

        override fun flush() {
            sink.flush()
        }

        override fun close() {
            sink.close()
        }
    }
}
