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

import androidx.annotation.GuardedBy
import androidx.camera.camera2.pipe.FrameBuffer
import androidx.camera.camera2.pipe.FrameReference
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.internal.FrameDistributor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FrameBufferImpl(
    private val frameGraphBuffers: FrameGraphBuffers,
    override val streams: Set<StreamId>,
    override val parameters: Map<Any, Any?>,
    override val capacity: Int,
) : FrameBuffer, FrameDistributor.FrameStartedListener {

    private val lock = Any()

    @GuardedBy("lock") private val frameQueue: ArrayDeque<FrameReference> = ArrayDeque(capacity)

    @GuardedBy("lock") private var closed = false

    private val _frameFlow =
        MutableSharedFlow<FrameReference>(
            replay = 0,
            extraBufferCapacity = FRAME_FLOW_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val frameFlow: SharedFlow<FrameReference> = _frameFlow.asSharedFlow()

    init {
        require(capacity > 0) { "FrameBuffer capacity must be greater than 0" }
    }

    private val _size = MutableStateFlow(0)

    override val size: StateFlow<Int> = _size.asStateFlow()

    override fun onFrameStarted(frameReference: FrameReference) {
        synchronized(lock) {
            if (closed) {
                return
            }
            // Add new frame reference to the queue, and remove the oldest one if queue is at its
            // capacity.
            // TODO: b/421957369 - add the ability to customize this eviction policy.
            while (frameQueue.size >= capacity) {
                frameQueue.removeFirst()
            }
            // TODO: b/424797841 - Acquire the ownership of the frame received in FrameBuffer
            frameQueue.add(frameReference)
            _size.value = frameQueue.size
            _frameFlow.tryEmit(frameReference)
        }
    }

    override fun removeFirstReference(): FrameReference? =
        synchronized(lock) {
            if (closed) return null
            return frameQueue.removeFirstOrNull()?.let {
                _size.value = frameQueue.size
                it
            }
        }

    override fun removeLastReference(): FrameReference? =
        synchronized(lock) {
            if (closed) return null
            return frameQueue.removeLastOrNull()?.let {
                _size.value = frameQueue.size
                it
            }
        }

    override fun removeAllReferences(): List<FrameReference> =
        synchronized(lock) {
            if (closed) return emptyList()
            val frameReferences = frameQueue.toList()
            frameQueue.clear()
            _size.value = frameQueue.size
            return frameReferences
        }

    override fun peekFirstReference(): FrameReference? =
        synchronized(lock) {
            if (closed) return null
            frameQueue.firstOrNull()
        }

    override fun peekLastReference(): FrameReference? =
        synchronized(lock) {
            if (closed) return null
            frameQueue.lastOrNull()
        }

    override fun peekAllReferences(): List<FrameReference> =
        synchronized(lock) {
            if (closed) return emptyList()
            ArrayList(frameQueue)
        }

    override fun close() {
        synchronized(lock) {
            closed = true
            frameQueue.clear()
            _size.value = 0
        }
        frameGraphBuffers.detach(this)
    }

    private companion object {
        const val FRAME_FLOW_EXTRA_BUFFER_CAPACITY = 4
    }
}
