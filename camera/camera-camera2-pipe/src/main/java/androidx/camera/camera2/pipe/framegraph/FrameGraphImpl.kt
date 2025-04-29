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

import android.view.Surface
import androidx.camera.camera2.pipe.AudioRestrictionMode
import androidx.camera.camera2.pipe.CameraGraphId
import androidx.camera.camera2.pipe.FrameGraph
import androidx.camera.camera2.pipe.GraphState
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.config.CameraGraphScope
import androidx.camera.camera2.pipe.graph.CameraGraphImpl
import androidx.camera.camera2.pipe.internal.CameraGraphParametersImpl
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

@CameraGraphScope
internal class FrameGraphImpl
@Inject
constructor(
    override val parameters: CameraGraphParametersImpl,
    private val cameraGraph: CameraGraphImpl,
    override val id: CameraGraphId,
) : FrameGraph {
    override val streams = cameraGraph.streams

    override val graphState: StateFlow<GraphState> = cameraGraph.graphState

    override var isForeground: Boolean = cameraGraph.isForeground

    override fun start() {
        cameraGraph.start()
    }

    override fun stop() {
        cameraGraph.stop()
    }

    override fun setSurface(stream: StreamId, surface: Surface?) {
        cameraGraph.setSurface(stream, surface)
    }

    override fun attach(
        streamIds: Set<StreamId>,
        parameters: Map<Any, Any?>
    ): FrameGraph.FrameBuffer {
        TODO("Not yet implemented")
    }

    override fun updateAudioRestrictionMode(mode: AudioRestrictionMode) {
        cameraGraph.updateAudioRestrictionMode(mode)
    }

    override fun close() {
        cameraGraph.close()
    }

    override suspend fun acquireSession(): FrameGraph.Session {
        TODO("Not yet implemented")
    }

    override fun acquireSessionOrNull(): FrameGraph.Session? {
        TODO("Not yet implemented")
    }

    override suspend fun <T> useSession(
        action: suspend CoroutineScope.(FrameGraph.Session) -> T
    ): T {
        TODO("Not yet implemented")
    }

    override fun <T> useSessionIn(
        scope: CoroutineScope,
        action: suspend CoroutineScope.(FrameGraph.Session) -> T
    ): Deferred<T> {
        TODO("Not yet implemented")
    }
}
