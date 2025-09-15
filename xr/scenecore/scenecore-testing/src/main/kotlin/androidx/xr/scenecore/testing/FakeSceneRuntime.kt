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

package androidx.xr.scenecore.testing

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.xr.arcore.internal.Anchor
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.internal.ActivityPanelEntity
import androidx.xr.scenecore.internal.ActivitySpace
import androidx.xr.scenecore.internal.AnchorEntity
import androidx.xr.scenecore.internal.AnchorPlacement
import androidx.xr.scenecore.internal.AudioTrackExtensionsWrapper
import androidx.xr.scenecore.internal.CameraViewActivityPose
import androidx.xr.scenecore.internal.Dimensions
import androidx.xr.scenecore.internal.Entity
import androidx.xr.scenecore.internal.GltfEntity
import androidx.xr.scenecore.internal.GltfFeature
import androidx.xr.scenecore.internal.HeadActivityPose
import androidx.xr.scenecore.internal.InputEventListener
import androidx.xr.scenecore.internal.InteractableComponent
import androidx.xr.scenecore.internal.LoggingEntity
import androidx.xr.scenecore.internal.MediaPlayerExtensionsWrapper
import androidx.xr.scenecore.internal.PanelEntity
import androidx.xr.scenecore.internal.PerceptionSpaceActivityPose
import androidx.xr.scenecore.internal.PixelDimensions
import androidx.xr.scenecore.internal.PlaneSemantic
import androidx.xr.scenecore.internal.PlaneType
import androidx.xr.scenecore.internal.PointerCaptureComponent
import androidx.xr.scenecore.internal.RenderingEntityFactory
import androidx.xr.scenecore.internal.SceneRuntime
import androidx.xr.scenecore.internal.SoundPoolExtensionsWrapper
import androidx.xr.scenecore.internal.SpatialCapabilities
import androidx.xr.scenecore.internal.SpatialEnvironment
import androidx.xr.scenecore.internal.SpatialModeChangeListener
import androidx.xr.scenecore.internal.SpatialPointerComponent
import androidx.xr.scenecore.internal.SpatialVisibility
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Test-only implementation of [androidx.xr.scenecore.internal.SceneRuntime] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeSceneRuntime() : SceneRuntime, RenderingEntityFactory {
    override val spatialCapabilities: SpatialCapabilities = SpatialCapabilities(0)

    override val activitySpace: ActivitySpace = FakeActivitySpace()

    override val headActivityPose: HeadActivityPose? = FakeHeadActivityPose()

    override val perceptionSpaceActivityPose: PerceptionSpaceActivityPose =
        FakePerceptionSpaceActivityPose()

    override val soundPoolExtensionsWrapper: SoundPoolExtensionsWrapper =
        FakeSoundPoolExtensionsWrapper()

    override val audioTrackExtensionsWrapper: AudioTrackExtensionsWrapper =
        FakeAudioTrackExtensionsWrapper()

    override val mediaPlayerExtensionsWrapper: MediaPlayerExtensionsWrapper =
        FakeMediaPlayerExtensionsWrapper()

    override val mainPanelEntity: PanelEntity = FakePanelEntity()

    override val spatialEnvironment: SpatialEnvironment = FakeSpatialEnvironment()

    override var spatialModeChangeListener: SpatialModeChangeListener? =
        FakeSpatialModeChangeListener()

    override fun getCameraViewActivityPose(
        @CameraViewActivityPose.CameraType cameraType: Int
    ): CameraViewActivityPose? = FakeCameraViewActivityPose()

    override fun createPanelEntity(
        context: Context,
        pose: Pose,
        view: View,
        dimensions: Dimensions,
        name: String,
        parent: Entity,
    ): PanelEntity = FakePanelEntity()

    override fun createPanelEntity(
        context: Context,
        pose: Pose,
        view: View,
        pixelDimensions: PixelDimensions,
        name: String,
        parent: Entity,
    ): PanelEntity = FakePanelEntity()

    override fun createActivityPanelEntity(
        pose: Pose,
        windowBoundsPx: PixelDimensions,
        name: String,
        hostActivity: Activity,
        parent: Entity,
    ): ActivityPanelEntity = FakeActivityPanelEntity()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createAnchorEntity(
        bounds: Dimensions,
        planeType: PlaneType,
        planeSemantic: PlaneSemantic,
        searchTimeout: Duration,
    ): AnchorEntity {
        val anchorCreationData =
            FakeAnchorEntity.AnchorCreationData(
                bounds = bounds,
                planeType = planeType,
                planeSemantic = planeSemantic,
                searchTimeout = searchTimeout,
            )
        return FakeAnchorEntity(anchorCreationData)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createAnchorEntity(anchor: Anchor): AnchorEntity {
        return FakeAnchorEntity(anchor = anchor)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createPersistedAnchorEntity(
        uuid: UUID,
        searchTimeout: Duration,
    ): FakeAnchorEntity {
        val anchorCreationData =
            FakeAnchorEntity.AnchorCreationData(searchTimeout = searchTimeout, uuid = uuid)
        return FakeAnchorEntity(anchorCreationData)
    }

    override fun createGltfEntity(
        feature: GltfFeature,
        pose: Pose,
        parentEntity: Entity,
    ): GltfEntity = FakeGltfEntity()

    override fun createGroupEntity(pose: Pose, name: String, parent: Entity): Entity = FakeEntity()

    override fun createLoggingEntity(pose: Pose): LoggingEntity =
        object : LoggingEntity, FakeEntity() {}

    override fun addSpatialCapabilitiesChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialCapabilities>,
    ) {}

    override fun removeSpatialCapabilitiesChangedListener(
        listener: Consumer<SpatialCapabilities>
    ) {}

    /**
     * For test purposes only.
     *
     * A map tracking the listener registered for spatial visibility changes. The key is the
     * [Executor] on which the listener should be invoked, and the value is the [Consumer] listener
     * itself.
     *
     * This map is populated by calls to [setSpatialVisibilityChangedListener] and cleared by
     * [clearSpatialVisibilityChangedListener]. Tests can inspect its contents to verify that the
     * correct listener is registered or that it has been successfully cleared.
     */
    public val spatialVisibilityChangedMap: Map<Executor, Consumer<SpatialVisibility>>
        get() = _spatialVisibilityChangedMap

    private val _spatialVisibilityChangedMap: MutableMap<Executor, Consumer<SpatialVisibility>> =
        mutableMapOf()

    override fun setSpatialVisibilityChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialVisibility>,
    ) {
        _spatialVisibilityChangedMap[callbackExecutor] = listener
    }

    override fun clearSpatialVisibilityChangedListener() {
        _spatialVisibilityChangedMap.clear()
    }

    /**
     * For test purposes only.
     *
     * A map tracking the listeners registered for perceived resolution changes. The key is the
     * [Executor] on which the listener should be invoked, and the value is the [Consumer] listener
     * itself.
     *
     * This map is populated by calls to [addPerceivedResolutionChangedListener] and modified by
     * [removePerceivedResolutionChangedListener]. Tests can inspect its contents to verify that the
     * correct listeners are registered or that they have been successfully removed.
     */
    public val perceivedResolutionChangedMap: Map<Executor, Consumer<PixelDimensions>>
        get() = _perceivedResolutionChangedMap

    private val _perceivedResolutionChangedMap: MutableMap<Executor, Consumer<PixelDimensions>> =
        mutableMapOf()

    override fun addPerceivedResolutionChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<PixelDimensions>,
    ) {
        _perceivedResolutionChangedMap[callbackExecutor] = listener
    }

    override fun removePerceivedResolutionChangedListener(listener: Consumer<PixelDimensions>) {
        _perceivedResolutionChangedMap.values.remove(listener)
    }

    /**
     * For test purposes only.
     *
     * Stores the [Activity] that was last provided to the [setPreferredAspectRatio] method. Tests
     * can inspect this property to verify the correct activity was used.
     */
    public var lastSetPreferredAspectRatioActivity: Activity? = null

    /**
     * For test purposes only.
     *
     * Stores the ratio that was last provided to the [setPreferredAspectRatio] method. Tests can
     * inspect this property to verify the correct ratio was set.
     */
    public var lastSetPreferredAspectRatioRatio: Float = -1f

    override fun setPreferredAspectRatio(activity: Activity, preferredRatio: Float) {
        lastSetPreferredAspectRatioActivity = activity
        lastSetPreferredAspectRatioRatio = preferredRatio
    }

    /** This value is used to verify [requestedFullSpaceMode] is invoked. */
    public var requestedFullSpaceMode: Boolean = false

    override fun requestFullSpaceMode() {
        requestedFullSpaceMode = true
    }

    /** This value is used to verify [requestHomeSpaceMode] is invoked. */
    public var requestedHomeSpaceMode: Boolean = false

    override fun requestHomeSpaceMode() {
        requestedHomeSpaceMode = true
    }

    override fun setFullSpaceMode(bundle: Bundle): Bundle = bundle

    override fun setFullSpaceModeWithEnvironmentInherited(bundle: Bundle): Bundle = bundle

    /** This value is used to verify the result of [enablePanelDepthTest] in tests. */
    internal var enabledPanelDepthTest: Boolean = false

    override fun enablePanelDepthTest(enabled: Boolean) {
        enabledPanelDepthTest = enabled
    }

    override fun createInteractableComponent(
        executor: Executor,
        listener: InputEventListener,
    ): InteractableComponent = FakeInteractableComponent()

    override fun createAnchorPlacementForPlanes(
        planeTypeFilter: Set<@JvmSuppressWildcards PlaneType>,
        planeSemanticFilter: Set<@JvmSuppressWildcards PlaneSemantic>,
    ): AnchorPlacement = object : AnchorPlacement {}

    override fun createMovableComponent(
        systemMovable: Boolean,
        scaleInZ: Boolean,
        anchorPlacement: Set<@JvmSuppressWildcards AnchorPlacement>,
        shouldDisposeParentAnchor: Boolean,
    ): FakeMovableComponent = FakeMovableComponent()

    override fun createResizableComponent(
        minimumSize: Dimensions,
        maximumSize: Dimensions,
    ): FakeResizableComponent {
        val resizableComponent = FakeResizableComponent(minimumSize, maximumSize)

        return resizableComponent
    }

    @Suppress("ExecutorRegistration")
    override fun createPointerCaptureComponent(
        executor: Executor,
        stateListener: PointerCaptureComponent.StateListener,
        inputListener: InputEventListener,
    ): FakePointerCaptureComponent {
        return FakePointerCaptureComponent(executor, stateListener)
    }

    override fun createSpatialPointerComponent(): SpatialPointerComponent =
        FakeSpatialPointerComponent()

    override fun dispose() {}
}
