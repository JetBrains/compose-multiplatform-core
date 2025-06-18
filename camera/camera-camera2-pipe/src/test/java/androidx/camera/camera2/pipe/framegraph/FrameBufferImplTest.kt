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

package androidx.camera.camera2.pipe.framegraph

import android.content.Context
import android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
import android.hardware.camera2.CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
import android.os.Build
import android.util.Size
import androidx.camera.camera2.pipe.CameraBackendFactory
import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.CameraGraphId
import androidx.camera.camera2.pipe.CameraStream
import androidx.camera.camera2.pipe.CameraSurfaceManager
import androidx.camera.camera2.pipe.Frame
import androidx.camera.camera2.pipe.FrameBuffers.tryPeekAll
import androidx.camera.camera2.pipe.FrameBuffers.tryPeekFirst
import androidx.camera.camera2.pipe.FrameBuffers.tryPeekLast
import androidx.camera.camera2.pipe.FrameBuffers.tryRemoveAll
import androidx.camera.camera2.pipe.FrameBuffers.tryRemoveFirst
import androidx.camera.camera2.pipe.FrameBuffers.tryRemoveLast
import androidx.camera.camera2.pipe.FrameReference
import androidx.camera.camera2.pipe.StreamFormat
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.graph.CameraGraphImpl
import androidx.camera.camera2.pipe.graph.GraphState3A
import androidx.camera.camera2.pipe.graph.Listener3A
import androidx.camera.camera2.pipe.graph.SessionLock
import androidx.camera.camera2.pipe.graph.StreamGraphImpl
import androidx.camera.camera2.pipe.graph.SurfaceGraph
import androidx.camera.camera2.pipe.internal.CameraBackendsImpl
import androidx.camera.camera2.pipe.internal.CameraGraphParametersImpl
import androidx.camera.camera2.pipe.internal.CameraPipeLifetime
import androidx.camera.camera2.pipe.internal.FrameCaptureQueue
import androidx.camera.camera2.pipe.internal.FrameDistributor
import androidx.camera.camera2.pipe.internal.ImageSourceMap
import androidx.camera.camera2.pipe.media.ImageReaderImageSources
import androidx.camera.camera2.pipe.testing.CameraControllerSimulator
import androidx.camera.camera2.pipe.testing.FakeAudioRestrictionController
import androidx.camera.camera2.pipe.testing.FakeCameraBackend
import androidx.camera.camera2.pipe.testing.FakeCameraMetadata
import androidx.camera.camera2.pipe.testing.FakeGraphProcessor
import androidx.camera.camera2.pipe.testing.FakeThreads
import androidx.camera.camera2.pipe.testing.RobolectricCameraPipeTestRunner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricCameraPipeTestRunner::class)
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
class FrameBufferImplTest {
    private val testScope = TestScope()
    private val context = ApplicationProvider.getApplicationContext() as Context
    private val metadata =
        FakeCameraMetadata(
            mapOf(INFO_SUPPORTED_HARDWARE_LEVEL to INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
        )
    private val fakeGraphProcessor = FakeGraphProcessor()
    private val cameraSurfaceManager = CameraSurfaceManager()

    private val stream1Config =
        CameraStream.Config.create(Size(1280, 720), StreamFormat.YUV_420_888)
    private val stream2Config =
        CameraStream.Config.create(Size(1920, 1080), StreamFormat.YUV_420_888)

    private val graphId = CameraGraphId.nextId()
    private val graphConfig =
        CameraGraph.Config(camera = metadata.camera, streams = listOf(stream1Config, stream2Config))
    private val threads = FakeThreads.fromTestScope(testScope)
    private val cameraPipeLifetime = CameraPipeLifetime()
    private val backend = FakeCameraBackend(fakeCameras = mapOf(metadata.camera to metadata))
    private val backends =
        CameraBackendsImpl(
            defaultBackendId = backend.id,
            cameraBackends = mapOf(backend.id to CameraBackendFactory { backend }),
            context,
            threads,
            cameraPipeLifetime,
        )
    private val cameraContext = CameraBackendsImpl.CameraBackendContext(context, threads, backends)
    private val imageSources = ImageReaderImageSources(threads)
    private val frameCaptureQueue = FrameCaptureQueue()
    private val cameraController =
        CameraControllerSimulator(cameraContext, graphId, graphConfig, fakeGraphProcessor)
    private val cameraControllerProvider: () -> CameraControllerSimulator = { cameraController }
    private val streamGraph = StreamGraphImpl(metadata, graphConfig, cameraControllerProvider)
    private val imageSourceMap = ImageSourceMap(graphConfig, streamGraph, imageSources)
    private val frameDistributor = FrameDistributor(imageSourceMap.imageSources, frameCaptureQueue)
    private val surfaceGraph =
        SurfaceGraph(streamGraph, cameraControllerProvider, cameraSurfaceManager, emptyMap())
    private val audioRestriction = FakeAudioRestrictionController()
    private val sessionLock = SessionLock()
    private val cameraGraphParameters =
        CameraGraphParametersImpl(sessionLock, fakeGraphProcessor, testScope)
    // TODO: b/420733360 - use FrameGraph/CameraGraph simulator for better setup and testing.
    private val cameraGraph =
        CameraGraphImpl(
            graphConfig,
            metadata,
            fakeGraphProcessor,
            fakeGraphProcessor,
            streamGraph,
            surfaceGraph,
            cameraController,
            GraphState3A(),
            Listener3A(),
            frameDistributor,
            frameCaptureQueue,
            audioRestriction,
            graphId,
            cameraGraphParameters,
            sessionLock,
        )
    private val frameGraphBuffers = FrameGraphBuffers(cameraGraph, testScope)
    private val streamId1: StreamId = StreamId(1)
    private val streamId2: StreamId = StreamId(2)

    private val defaultStreams = setOf(streamId1, streamId2)
    private val defaultParameters = mapOf<Any, Any?>("paramKey" to "paramValue")
    private val defaultCapacity = 3

    private lateinit var frameBuffer: FrameBufferImpl

    private fun createFrameBuffer(
        streams: Set<StreamId> = defaultStreams,
        parameters: Map<Any, Any?> = defaultParameters,
        capacity: Int = defaultCapacity,
    ): FrameBufferImpl {
        return FrameBufferImpl(frameGraphBuffers, streams, parameters, capacity)
    }

    @Before
    fun setup() {
        frameBuffer = createFrameBuffer()
    }

    private fun mockFrameReference(id: Int): FrameReference {
        val mockFrame: Frame = mock()
        return mock {
            on { tryAcquire() }.thenReturn(mockFrame)
            on { frameNumber }.thenReturn(androidx.camera.camera2.pipe.FrameNumber(id.toLong()))
            on { toString() }.thenReturn("MockFrameReference-$id")
        }
    }

    @Test
    fun initialization_propertiesCorrectlySet() {
        assertThat(frameBuffer.streams).isEqualTo(defaultStreams)
        assertThat(frameBuffer.parameters).isEqualTo(defaultParameters)
        assertThat(frameBuffer.capacity).isEqualTo(defaultCapacity)
        assertThat(frameBuffer.size.value).isEqualTo(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialization_zeroCapacity_throwsException() {
        createFrameBuffer(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialization_negativeCapacity_throwsException() {
        createFrameBuffer(capacity = -1)
    }

    @Test
    fun onFrameStarted_addsFrame_updatesSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            assertThat(frameBuffer.size.value).isEqualTo(1)
            assertThat(frameBuffer.peekFirstReference()).isSameInstanceAs(frameRef1)
            assertThat(frameBuffer.peekLastReference()).isSameInstanceAs(frameRef1)
            assertThat(frameBuffer.peekAllReferences()).containsExactly(frameRef1)
        }

    @Test
    fun onFrameStarted_exceedsCapacity_evictsOldestFrame() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val frameRef3 = mockFrameReference(3)
            val frameRef4 = mockFrameReference(4)

            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            frameBuffer.onFrameStarted(frameRef3)
            advanceUntilIdle()
            assertThat(frameBuffer.size.value).isEqualTo(3)
            assertThat(frameBuffer.peekAllReferences())
                .containsExactly(frameRef1, frameRef2, frameRef3)
                .inOrder()

            frameBuffer.onFrameStarted(frameRef4)
            advanceUntilIdle()

            assertThat(frameBuffer.size.value).isEqualTo(defaultCapacity)
            assertThat(frameBuffer.peekAllReferences())
                .containsExactly(frameRef2, frameRef3, frameRef4)
                .inOrder()
        }

    @Test
    fun onFrameStarted_whenClosed_doesNothing() =
        testScope.runTest {
            frameBuffer.close()
            advanceUntilIdle()

            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            assertThat(frameBuffer.size.value).isEqualTo(0)
            assertThat(frameBuffer.peekFirstReference()).isNull()
        }

    @Test
    fun removeFirstReference_emptyBuffer_returnsNull() =
        testScope.runTest {
            assertThat(frameBuffer.removeFirstReference()).isNull()
            assertThat(frameBuffer.size.value).isEqualTo(0)
        }

    @Test
    fun removeFirstReference_removesCorrectFrame_updatesSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val removed = frameBuffer.removeFirstReference()
            assertThat(removed).isSameInstanceAs(frameRef1)
            assertThat(frameBuffer.size.value).isEqualTo(1)
            assertThat(frameBuffer.peekFirstReference()).isSameInstanceAs(frameRef2)

            val removedNext = frameBuffer.removeFirstReference()
            assertThat(removedNext).isSameInstanceAs(frameRef2)
            assertThat(frameBuffer.size.value).isEqualTo(0)
        }

    @Test
    fun removeFirstReference_whenClosed_returnsNull() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.removeFirstReference()).isNull()
        }

    @Test
    fun removeLastReference_emptyBuffer_returnsNull() =
        testScope.runTest {
            assertThat(frameBuffer.removeLastReference()).isNull()
            assertThat(frameBuffer.size.value).isEqualTo(0)
        }

    @Test
    fun removeLastReference_removesCorrectFrame_updatesSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val removed = frameBuffer.removeLastReference()
            assertThat(removed).isSameInstanceAs(frameRef2)
            assertThat(frameBuffer.size.value).isEqualTo(1)
            assertThat(frameBuffer.peekFirstReference()).isSameInstanceAs(frameRef1)

            val removedNext = frameBuffer.removeLastReference()
            assertThat(removedNext).isSameInstanceAs(frameRef1)
            assertThat(frameBuffer.size.value).isEqualTo(0)
        }

    @Test
    fun removeLastReference_whenClosed_returnsNull() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.removeLastReference()).isNull()
        }

    @Test
    fun removeAllReferences_emptyBuffer_returnsEmptyList() =
        testScope.runTest {
            assertThat(frameBuffer.removeAllReferences()).isEmpty()
            assertThat(frameBuffer.size.value).isEqualTo(0)
        }

    @Test
    fun removeAllReferences_returnsAllFramesInOrder_updatesSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val removed = frameBuffer.removeAllReferences()
            assertThat(removed).containsExactly(frameRef1, frameRef2).inOrder()
            assertThat(frameBuffer.size.value).isEqualTo(0)
            assertThat(frameBuffer.peekFirstReference()).isNull()
        }

    @Test
    fun removeAllReferences_whenClosed_returnsEmptyList() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.removeAllReferences()).isEmpty()
        }

    @Test
    fun peekFirstReference_emptyBuffer_returnsNull() =
        testScope.runTest { assertThat(frameBuffer.peekFirstReference()).isNull() }

    @Test
    fun peekFirstReference_returnsFrame_doesNotChangeSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            val peeked = frameBuffer.peekFirstReference()
            assertThat(peeked).isSameInstanceAs(frameRef1)
            assertThat(frameBuffer.size.value).isEqualTo(1)
        }

    @Test
    fun peekFirstReference_whenClosed_returnsNull() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.peekFirstReference()).isNull()
        }

    @Test
    fun peekLastReference_emptyBuffer_returnsNull() =
        testScope.runTest { assertThat(frameBuffer.peekLastReference()).isNull() }

    @Test
    fun peekLastReference_returnsFrame_doesNotChangeSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val peeked = frameBuffer.peekLastReference()
            assertThat(peeked).isSameInstanceAs(frameRef2)
            assertThat(frameBuffer.size.value).isEqualTo(2)
        }

    @Test
    fun peekLastReference_whenClosed_returnsNull() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.peekLastReference()).isNull()
        }

    @Test
    fun peekAllReferences_emptyBuffer_returnsEmptyList() =
        testScope.runTest { assertThat(frameBuffer.peekAllReferences()).isEmpty() }

    @Test
    fun peekAllReferences_returnsAllFramesInOrder_doesNotChangeSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val peeked = frameBuffer.peekAllReferences()
            assertThat(peeked).containsExactly(frameRef1, frameRef2).inOrder()
            assertThat(frameBuffer.size.value).isEqualTo(2)
        }

    @Test
    fun peekAllReferences_whenClosed_returnsEmptyList() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.peekAllReferences()).isEmpty()
        }

    @Test
    fun onFrameAvailable_flowEmitted() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val ready = CompletableDeferred<Unit>()
            val resultsChannel = Channel<FrameReference>(Channel.UNLIMITED)

            val job =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready.complete(Unit) }
                        .collect { frame -> resultsChannel.send(frame) }
                }

            ready.await()
            frameBuffer.onFrameStarted(frameRef1)

            val receivedFrame = resultsChannel.receive()
            assertThat(receivedFrame).isEqualTo(frameRef1)
            job.cancel()
        }

    @Test
    fun onFrameAvailableCalls_multipleCalls_multipleEmitted() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val ready = CompletableDeferred<Unit>()
            val resultsChannel = Channel<FrameReference>(Channel.UNLIMITED)
            val job =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready.complete(Unit) }
                        .collect { frame -> resultsChannel.send(frame) }
                }

            ready.await()
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)

            assertThat(resultsChannel.receive()).isEqualTo(frameRef1)
            assertThat(resultsChannel.receive()).isEqualTo(frameRef2)
            job.cancel()
        }

    @Test
    fun onFrameAvailable_exceedsExtraCapacity_oldestDropped() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val frameRef3 = mockFrameReference(3)
            val frameRef4 = mockFrameReference(4)
            val frameRef5 = mockFrameReference(5)
            val ready = CompletableDeferred<Unit>()
            val resultsChannel = Channel<FrameReference>(Channel.UNLIMITED)
            val job =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready.complete(Unit) }
                        .collect { frame -> resultsChannel.send(frame) }
                }

            ready.await()
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            frameBuffer.onFrameStarted(frameRef3)
            frameBuffer.onFrameStarted(frameRef4)
            frameBuffer.onFrameStarted(frameRef5)

            // frameRef1 will drop because the extraBufferCapacity of the flow is 4
            assertThat(resultsChannel.receive()).isEqualTo(frameRef2)
            assertThat(resultsChannel.receive()).isEqualTo(frameRef3)
            assertThat(resultsChannel.receive()).isEqualTo(frameRef4)
            assertThat(resultsChannel.receive()).isEqualTo(frameRef5)
            job.cancel()
        }

    @Test
    fun onFrameAvailable_multipleConsumers_allReceiveFrames() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val ready1 = CompletableDeferred<Unit>()
            val ready2 = CompletableDeferred<Unit>()
            val resultsChannel1 = Channel<FrameReference>(Channel.UNLIMITED)
            val resultsChannel2 = Channel<FrameReference>(Channel.UNLIMITED)
            val job1 =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready1.complete(Unit) }
                        .collect { frame -> resultsChannel1.send(frame) }
                }
            val job2 =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready2.complete(Unit) }
                        .collect { frame -> resultsChannel2.send(frame) }
                }

            ready1.await()
            ready2.await()
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)

            assertThat(resultsChannel1.receive()).isEqualTo(frameRef1)
            assertThat(resultsChannel1.receive()).isEqualTo(frameRef2)
            assertThat(resultsChannel2.receive()).isEqualTo(frameRef1)
            assertThat(resultsChannel2.receive()).isEqualTo(frameRef2)
            job1.cancel()
            job2.cancel()
        }

    @Test
    fun onFrameAvailable_slowAndFastConsumers_fastConsumerDoesNotDropFrames() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val frameRef3 = mockFrameReference(3)
            val ready1 = CompletableDeferred<Unit>()
            val ready2 = CompletableDeferred<Unit>()
            val resultsChannel1 = Channel<FrameReference>(capacity = 1)
            val resultsChannel2 = Channel<FrameReference>(Channel.UNLIMITED)
            val job1 =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready1.complete(Unit) }
                        .collect { frame -> resultsChannel1.send(frame) }
                }
            val job2 =
                backgroundScope.launch {
                    frameBuffer.frameFlow
                        .onStart { ready2.complete(Unit) }
                        .collect { frame -> resultsChannel2.send(frame) }
                }

            ready1.await()
            ready2.await()
            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            frameBuffer.onFrameStarted(frameRef3)

            // Channel 1 is full, so the next frame will be dropped for this consumer.
            assertThat(resultsChannel1.receive()).isEqualTo(frameRef1)
            assertThat(resultsChannel2.receive()).isEqualTo(frameRef1)
            assertThat(resultsChannel2.receive()).isEqualTo(frameRef2)
            assertThat(resultsChannel2.receive()).isEqualTo(frameRef3)
            job1.cancel()
            job2.cancel()
        }

    @Test
    fun close_clearsQueue_updatesSize() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()
            frameBuffer.close()
            advanceUntilIdle()

            assertThat(frameBuffer.size.value).isEqualTo(0)
            assertThat(frameBuffer.peekFirstReference()).isNull()
        }

    @Test
    fun peekFirst_callsPeekFirstReferenceAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val mockFrame: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame)

            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            val frame = frameBuffer.tryPeekFirst()
            assertThat(frame).isSameInstanceAs(mockFrame)
            verify(frameRef1).tryAcquire()
        }

    @Test
    fun peekFirst_emptyBuffer_returnsNull() =
        testScope.runTest { assertThat(frameBuffer.tryPeekFirst()).isNull() }

    @Test
    fun peekLast_callsPeekLastReferenceAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val mockFrame1: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame1)

            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            val frame = frameBuffer.tryPeekLast()
            assertThat(frame).isSameInstanceAs(mockFrame1)
            verify(frameRef1).tryAcquire()
        }

    @Test
    fun peekAll_callsPeekAllReferencesAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val mockFrame1: Frame = mock()
            val mockFrame2: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame1)
            whenever(frameRef2.tryAcquire()).thenReturn(mockFrame2)

            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val frames = frameBuffer.tryPeekAll()
            assertThat(frames).containsExactly(mockFrame1, mockFrame2).inOrder()
            verify(frameRef1).tryAcquire()
            verify(frameRef2).tryAcquire()
        }

    @Test
    fun removeFirst_callsRemoveFirstReferenceAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val mockFrame: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame)

            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            val frame = frameBuffer.tryRemoveFirst()
            assertThat(frame).isSameInstanceAs(mockFrame)

            verify(frameRef1).tryAcquire()
        }

    @Test
    fun removeLast_callsRemoveLastReferenceAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val mockFrame: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame)

            frameBuffer.onFrameStarted(frameRef1)
            advanceUntilIdle()

            val frame = frameBuffer.tryRemoveLast()
            assertThat(frame).isSameInstanceAs(mockFrame)
            verify(frameRef1).tryAcquire()
        }

    @Test
    fun removeAll_callsRemoveAllReferencesAndAcquires() =
        testScope.runTest {
            val frameRef1 = mockFrameReference(1)
            val frameRef2 = mockFrameReference(2)
            val mockFrame1: Frame = mock()
            val mockFrame2: Frame = mock()
            whenever(frameRef1.tryAcquire()).thenReturn(mockFrame1)
            whenever(frameRef2.tryAcquire()).thenReturn(mockFrame2)

            frameBuffer.onFrameStarted(frameRef1)
            frameBuffer.onFrameStarted(frameRef2)
            advanceUntilIdle()

            val frames = frameBuffer.tryRemoveAll()
            assertThat(frames).containsExactly(mockFrame1, mockFrame2).inOrder()
            verify(frameRef1).tryAcquire()
            verify(frameRef2).tryAcquire()
        }
}
