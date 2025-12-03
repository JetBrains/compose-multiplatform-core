/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.camera2.pipe.integration.config

import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.CameraStream
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.integration.adapter.CameraStateAdapter
import androidx.camera.camera2.pipe.integration.adapter.GraphStateToCameraStateAdapter
import androidx.camera.camera2.pipe.integration.adapter.SessionConfigAdapter
import androidx.camera.camera2.pipe.integration.compat.workaround.CapturePipelineTorchCorrection
import androidx.camera.camera2.pipe.integration.impl.Camera2Logger
import androidx.camera.camera2.pipe.integration.impl.CameraInteropStateCallbackRepository
import androidx.camera.camera2.pipe.integration.impl.CapturePipeline
import androidx.camera.camera2.pipe.integration.impl.CapturePipelineImpl
import androidx.camera.camera2.pipe.integration.impl.UseCaseCamera
import androidx.camera.camera2.pipe.integration.impl.UseCaseCameraImpl
import androidx.camera.camera2.pipe.integration.impl.UseCaseCameraRequestControlImpl
import androidx.camera.core.UseCase
import androidx.camera.core.impl.DeferrableSurface
import androidx.camera.core.impl.SessionProcessor
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import javax.inject.Provider
import javax.inject.Scope

@Scope public annotation class UseCaseCameraScope

/** Dependency bindings for building a [UseCaseCamera] */
@Module(
    includes = [UseCaseCameraImpl.Bindings::class, UseCaseCameraRequestControlImpl.Bindings::class]
)
public abstract class UseCaseCameraModule {
    // Used for dagger provider methods that are static.
    public companion object {

        @UseCaseCameraScope
        @Provides
        public fun provideCapturePipeline(
            capturePipelineImpl: CapturePipelineImpl,
            capturePipelineTorchCorrection: CapturePipelineTorchCorrection,
        ): CapturePipeline {
            if (CapturePipelineTorchCorrection.isEnabled) {
                return capturePipelineTorchCorrection
            }

            return capturePipelineImpl
        }
    }
}

/** Dagger module for binding the [UseCase]'s to the [UseCaseCamera]. */
@Module
public data class UseCaseCameraConfig(
    private val useCases: List<UseCase>,
    private val streamConfigMap: Map<CameraStream.Config, DeferrableSurface>,
    private val cameraGraphFactory: (CameraGraph.Config) -> CameraGraph,
    public val graphStateToCameraStateAdapter: GraphStateToCameraStateAdapter,
    public val sessionConfigAdapter: SessionConfigAdapter,
    public val cameraGraphConfig: CameraGraph.Config,
    private val sessionProcessor: SessionProcessor? = null,
) {
    @UseCaseCameraScope
    @Provides
    public fun provideCameraGraph(): CameraGraph {
        return cameraGraphFactory(cameraGraphConfig)
    }

    @UseCaseCameraScope
    @Provides
    public fun provideUseCaseList(): java.util.ArrayList<UseCase> {
        return java.util.ArrayList(useCases)
    }

    @UseCaseCameraScope
    @Provides
    public fun provideSessionConfigAdapter(): SessionConfigAdapter {
        return sessionConfigAdapter
    }

    @UseCaseCameraScope
    @Provides
    public fun provideSessionProcessor(): SessionProcessor? {
        return sessionProcessor
    }

    /**
     * [UseCaseGraphConfig] would store the CameraGraph and related surface map that would be used
     * for [UseCaseCamera].
     */
    @UseCaseCameraScope
    @Provides
    public fun provideUseCaseGraphConfig(
        cameraStateAdapter: CameraStateAdapter,
        cameraInteropStateCallbackRepository: CameraInteropStateCallbackRepository,
        cameraGraphProvider: Provider<CameraGraph>,
    ): UseCaseGraphConfig {
        sessionConfigAdapter.getValidSessionConfigOrNull()?.let { sessionConfig ->
            cameraInteropStateCallbackRepository.updateCallbacks(sessionConfig)
        }

        Camera2Logger.debug { "Prepared UseCaseGraphConfig (Deferred)" }
        return UseCaseGraphConfig(
            cameraGraphProvider = cameraGraphProvider,
            cameraStateAdapter = cameraStateAdapter,
            streamConfigMap = streamConfigMap,
            graphStateToCameraStateAdapter = graphStateToCameraStateAdapter,
        )
    }
}

public class UseCaseGraphConfig(
    private val cameraGraphProvider: Provider<CameraGraph>,
    private val cameraStateAdapter: CameraStateAdapter,
    private val graphStateToCameraStateAdapter: GraphStateToCameraStateAdapter,
    private val streamConfigMap: Map<CameraStream.Config, DeferrableSurface> = emptyMap(),
    defaultSurfaceToStreamMap: Map<DeferrableSurface, StreamId>? = null,
) {
    private val _graph = lazy { cameraGraphProvider.get() }

    public val graph: CameraGraph by _graph

    public val surfaceToStreamMap: Map<DeferrableSurface, StreamId> by lazy {
        defaultSurfaceToStreamMap
            ?: run {
                val map = mutableMapOf<DeferrableSurface, StreamId>()
                streamConfigMap.forEach { (streamConfig, deferrableSurface) ->
                    graph.streams[streamConfig]?.let { map[deferrableSurface] = it.id }
                }
                map.toMap()
            }
    }

    public fun closeGraph() {
        if (_graph.isInitialized()) {
            graph.close()
        }
    }

    public fun getStreamIdsFromSurfaces(
        deferrableSurfaces: Collection<DeferrableSurface>
    ): Set<StreamId> {
        val streamIds = mutableSetOf<StreamId>()
        deferrableSurfaces.forEach {
            surfaceToStreamMap[it]?.let { streamId -> streamIds.add(streamId) }
        }
        return streamIds
    }

    public fun configureCameraStateListener() {
        graphStateToCameraStateAdapter.cameraGraph = graph
        cameraStateAdapter.onGraphUpdated(graph)
    }

    public suspend inline fun <T> useGraphSession(block: (CameraGraph.Session) -> T): T {
        return graph.acquireSession().use { block(it) }
    }
}

/** Dagger subcomponent for a single [UseCaseCamera] instance. */
@UseCaseCameraScope
@Subcomponent(modules = [UseCaseCameraModule::class, UseCaseCameraConfig::class])
public interface UseCaseCameraComponent {
    public fun getUseCaseCamera(): UseCaseCamera

    public fun getUseCaseGraphConfig(): UseCaseGraphConfig

    @Subcomponent.Builder
    public interface Builder {
        public fun config(config: UseCaseCameraConfig): Builder

        public fun build(): UseCaseCameraComponent
    }
}
