/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.runtime.testing

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.ActivityPanelEntity
import androidx.xr.runtime.internal.ActivitySpace
import androidx.xr.runtime.internal.Anchor
import androidx.xr.runtime.internal.AnchorEntity
import androidx.xr.runtime.internal.AnchorPlacement
import androidx.xr.runtime.internal.AudioTrackExtensionsWrapper
import androidx.xr.runtime.internal.CameraViewActivityPose
import androidx.xr.runtime.internal.Dimensions
import androidx.xr.runtime.internal.Entity
import androidx.xr.runtime.internal.ExrImageResource
import androidx.xr.runtime.internal.GltfEntity
import androidx.xr.runtime.internal.GltfModelResource
import androidx.xr.runtime.internal.HeadActivityPose
import androidx.xr.runtime.internal.InputEventListener
import androidx.xr.runtime.internal.InteractableComponent
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.LoggingEntity
import androidx.xr.runtime.internal.MaterialResource
import androidx.xr.runtime.internal.MediaPlayerExtensionsWrapper
import androidx.xr.runtime.internal.MovableComponent
import androidx.xr.runtime.internal.PanelEntity
import androidx.xr.runtime.internal.PerceptionSpaceActivityPose
import androidx.xr.runtime.internal.PixelDimensions
import androidx.xr.runtime.internal.PlaneSemantic
import androidx.xr.runtime.internal.PlaneType
import androidx.xr.runtime.internal.PointerCaptureComponent
import androidx.xr.runtime.internal.ResizableComponent
import androidx.xr.runtime.internal.SoundPoolExtensionsWrapper
import androidx.xr.runtime.internal.SpatialCapabilities
import androidx.xr.runtime.internal.SpatialEnvironment
import androidx.xr.runtime.internal.SpatialVisibility
import androidx.xr.runtime.internal.SubspaceNodeEntity
import androidx.xr.runtime.internal.SurfaceEntity
import androidx.xr.runtime.internal.TextureResource
import androidx.xr.runtime.internal.TextureSampler
import androidx.xr.runtime.math.Pose
import com.google.androidxr.splitengine.SubspaceNode
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.function.Consumer

// TODO: b/405218432 - Implement this correctly instead of stubbing it out.
/** Test-only implementation of [JxrPlatformAdapter] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeJxrPlatformAdapter : JxrPlatformAdapter {

    override val spatialEnvironment: SpatialEnvironment = FakeSpatialEnvironment()

    override val mainPanelEntity: PanelEntity = FakePanelEntity()

    override val activitySpace: ActivitySpace = FakeActivitySpace()

    override val headActivityPose: HeadActivityPose? = null

    override val activitySpaceRootImpl: Entity = FakeEntity()

    override val spatialCapabilities: SpatialCapabilities = SpatialCapabilities(0)

    override val perceptionSpaceActivityPose: PerceptionSpaceActivityPose =
        object : PerceptionSpaceActivityPose, FakeActivityPose() {}

    override val soundPoolExtensionsWrapper: SoundPoolExtensionsWrapper =
        FakeSoundPoolExtensionsWrapper()

    override val audioTrackExtensionsWrapper: AudioTrackExtensionsWrapper =
        FakeAudioTrackExtensionsWrapper()

    override val mediaPlayerExtensionsWrapper: MediaPlayerExtensionsWrapper =
        FakeMediaPlayerExtensionsWrapper()

    override fun getCameraViewActivityPose(
        @CameraViewActivityPose.CameraType cameraType: Int
    ): CameraViewActivityPose? = null

    override fun loadGltfByAssetName(assetName: String): ListenableFuture<GltfModelResource> =
        immediateFailedFuture<GltfModelResource>(NotImplementedError())

    override fun loadGltfByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<GltfModelResource> =
        immediateFailedFuture<GltfModelResource>(NotImplementedError())

    override fun loadExrImageByAssetName(assetName: String): ListenableFuture<ExrImageResource> =
        immediateFailedFuture<ExrImageResource>(NotImplementedError())

    override fun loadExrImageByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<ExrImageResource> =
        immediateFailedFuture<ExrImageResource>(NotImplementedError())

    override fun loadTexture(
        assetName: String,
        sampler: TextureSampler,
    ): ListenableFuture<TextureResource>? =
        immediateFailedFuture<TextureResource>(NotImplementedError())

    override fun borrowReflectionTexture(): TextureResource? = null

    override fun destroyTexture(texture: TextureResource) {}

    override fun getReflectionTextureFromIbl(iblToken: ExrImageResource): TextureResource? = null

    override fun createWaterMaterial(
        isAlphaMapVersion: Boolean
    ): ListenableFuture<MaterialResource>? =
        immediateFailedFuture<MaterialResource>(NotImplementedError())

    override fun destroyWaterMaterial(material: MaterialResource) {}

    override fun setReflectionCube(material: MaterialResource, reflectionCube: TextureResource) {}

    override fun setNormalMap(material: MaterialResource, normalMap: TextureResource) {}

    override fun setNormalTiling(material: MaterialResource, normalTiling: Float) {}

    override fun setNormalSpeed(material: MaterialResource, normalSpeed: Float) {}

    override fun setAlphaStepMultiplier(material: MaterialResource, alphaStepMultiplier: Float) {}

    override fun setAlphaMap(material: MaterialResource, alphaMap: TextureResource) {}

    override fun setNormalZ(material: MaterialResource, normalZ: Float) {}

    override fun setNormalBoundary(material: MaterialResource, normalBoundary: Float) {}

    override fun createGltfEntity(
        pose: Pose,
        loadedGltf: GltfModelResource,
        parentEntity: Entity,
    ): GltfEntity = FakeGltfEntity()

    override fun createSurfaceEntity(
        stereoMode: Int,
        canvasShape: SurfaceEntity.CanvasShape,
        pose: Pose,
        parentEntity: Entity,
    ): SurfaceEntity = FakeSurfaceEntity()

    override fun addSpatialCapabilitiesChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialCapabilities>,
    ) {}

    override fun removeSpatialCapabilitiesChangedListener(
        listener: Consumer<SpatialCapabilities>
    ) {}

    override fun setSpatialVisibilityChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialVisibility>,
    ) {}

    override fun clearSpatialVisibilityChangedListener() {}

    override fun createLoggingEntity(pose: Pose): LoggingEntity =
        object : LoggingEntity, FakeEntity() {}

    override fun requestFullSpaceMode() {}

    override fun requestHomeSpaceMode() {}

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

    override fun createAnchorEntity(
        bounds: Dimensions,
        planeType: PlaneType,
        planeSemantic: PlaneSemantic,
        searchTimeout: Duration,
    ): AnchorEntity = FakeAnchorEntity()

    override fun createAnchorEntity(anchor: Anchor): AnchorEntity = FakeAnchorEntity()

    override fun createEntity(pose: Pose, name: String, parent: Entity): Entity = FakeEntity()

    override fun createSubspaceNodeEntity(
        subspaceNode: SubspaceNode,
        size: Dimensions,
    ): SubspaceNodeEntity = FakeSubspaceNodeEntity(subspaceNode, size)

    @Suppress("ExecutorRegistration")
    override fun createInteractableComponent(
        executor: Executor,
        listener: InputEventListener,
    ): InteractableComponent = object : InteractableComponent, FakeComponent() {}

    override fun createMovableComponent(
        systemMovable: Boolean,
        scaleInZ: Boolean,
        anchorPlacement: Set<@JvmSuppressWildcards AnchorPlacement>,
        shouldDisposeParentAnchor: Boolean,
    ): MovableComponent = FakeMovableComponent()

    override fun createAnchorPlacementForPlanes(
        planeTypeFilter: Set<@JvmSuppressWildcards PlaneType>,
        planeSemanticFilter: Set<@JvmSuppressWildcards PlaneSemantic>,
    ): AnchorPlacement = object : AnchorPlacement {}

    override fun createResizableComponent(
        minimumSize: Dimensions,
        maximumSize: Dimensions,
    ): ResizableComponent = FakeResizableComponent()

    @Suppress("ExecutorRegistration")
    override fun createPointerCaptureComponent(
        executor: Executor,
        stateListener: PointerCaptureComponent.StateListener,
        inputListener: InputEventListener,
    ): PointerCaptureComponent = object : PointerCaptureComponent, FakeComponent() {}

    override fun createPersistedAnchorEntity(uuid: UUID, searchTimeout: Duration): AnchorEntity =
        FakeAnchorEntity()

    override fun setFullSpaceMode(bundle: Bundle): Bundle = bundle

    override fun setFullSpaceModeWithEnvironmentInherited(bundle: Bundle): Bundle = bundle

    override fun setPreferredAspectRatio(activity: Activity, preferredRatio: Float) {}

    override fun startRenderer() {}

    override fun stopRenderer() {}

    override fun dispose() {}
}
