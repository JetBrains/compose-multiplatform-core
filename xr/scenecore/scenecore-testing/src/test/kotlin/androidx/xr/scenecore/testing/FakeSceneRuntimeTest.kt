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
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.test.filters.SdkSuppress
import androidx.xr.arcore.testing.FakeRuntimeAnchor
import androidx.xr.arcore.testing.FakeRuntimePlane
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.runtime.AnchorPlacement
import androidx.xr.scenecore.runtime.CameraViewActivityPose
import androidx.xr.scenecore.runtime.Dimensions
import androidx.xr.scenecore.runtime.InputEvent
import androidx.xr.scenecore.runtime.InputEventListener
import androidx.xr.scenecore.runtime.PixelDimensions
import androidx.xr.scenecore.runtime.PlaneSemantic
import androidx.xr.scenecore.runtime.PlaneType
import androidx.xr.scenecore.runtime.PointerCaptureComponent.PointerCaptureState
import androidx.xr.scenecore.runtime.PointerCaptureComponent.StateListener
import androidx.xr.scenecore.runtime.SpatialCapabilities
import androidx.xr.scenecore.runtime.SpatialVisibility
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakeSceneRuntimeTest {
    private lateinit var fakeSceneRuntime: FakeSceneRuntime

    @Before
    fun setUp() {
        fakeSceneRuntime = FakeSceneRuntime()
    }

    @Test
    fun getState_whenCreated_returnsCreatedState() {
        assertThat(fakeSceneRuntime.state).isEqualTo(FakeSceneRuntime.State.CREATED)
    }

    @Test
    fun getCameraViewActivityPose_returnsCameraViewActivityPoseWithCorrectType() {
        val cameraViewActivityPose =
            fakeSceneRuntime.getCameraViewActivityPose(
                CameraViewActivityPose.CameraType.CAMERA_TYPE_UNKNOWN
            )
        val cameraViewActivityPoseL =
            fakeSceneRuntime.getCameraViewActivityPose(
                CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE
            )
        val cameraViewActivityPoseR =
            fakeSceneRuntime.getCameraViewActivityPose(
                CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE
            )

        assertThat(cameraViewActivityPose).isNull()
        assertThat(cameraViewActivityPoseL).isNotNull()
        assertThat((cameraViewActivityPoseL as FakeCameraViewActivityPose).cameraType)
            .isEqualTo(CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE)
        assertThat(cameraViewActivityPoseR).isNotNull()
        assertThat((cameraViewActivityPoseR as FakeCameraViewActivityPose).cameraType)
            .isEqualTo(CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE)
    }

    @Test
    fun enablePanelDepthTest_setsValueCorrectly() {
        check(!fakeSceneRuntime.enabledPanelDepthTest)

        fakeSceneRuntime.enablePanelDepthTest(true)

        assertThat(fakeSceneRuntime.enabledPanelDepthTest).isTrue()
    }

    @Test
    fun addRemoveSpatialCapabilitiesChangedListener_spatialCapabilitiesChangedMapUpdated() {
        check(fakeSceneRuntime.spatialCapabilitiesChangedMap.isEmpty())

        val consumer = Consumer<SpatialCapabilities> {}
        fakeSceneRuntime.addSpatialCapabilitiesChangedListener(
            { command -> command.run() },
            consumer,
        )

        assertThat(fakeSceneRuntime.spatialCapabilitiesChangedMap).hasSize(1)

        fakeSceneRuntime.removeSpatialCapabilitiesChangedListener(consumer)

        assertThat(fakeSceneRuntime.spatialCapabilitiesChangedMap).isEmpty()
    }

    @Test
    fun setClearSpatialVisibilityChangedListener_spatialVisibilityChangedMapUpdated() {
        check(fakeSceneRuntime.spatialVisibilityChangedMap.isEmpty())

        val consumer = Consumer<SpatialVisibility> {}
        fakeSceneRuntime.setSpatialVisibilityChangedListener({ command -> command.run() }, consumer)

        assertThat(fakeSceneRuntime.spatialVisibilityChangedMap).hasSize(1)

        fakeSceneRuntime.clearSpatialVisibilityChangedListener()

        assertThat(fakeSceneRuntime.spatialVisibilityChangedMap).isEmpty()
    }

    @Test
    fun addRemovePerceivedResolutionChangedListener_perceivedResolutionChangedMapUpdated() {
        check(fakeSceneRuntime.perceivedResolutionChangedMap.isEmpty())

        val consumer = Consumer<PixelDimensions> {}
        fakeSceneRuntime.addPerceivedResolutionChangedListener(
            { command -> command.run() },
            consumer,
        )

        assertThat(fakeSceneRuntime.perceivedResolutionChangedMap).hasSize(1)

        fakeSceneRuntime.removePerceivedResolutionChangedListener(consumer)

        assertThat(fakeSceneRuntime.perceivedResolutionChangedMap).isEmpty()
    }

    @Test
    fun requestHomeSpaceMode_requestFullSpaceMode_spatialCapabilitiesIsUpdated() {
        assertThat(fakeSceneRuntime.spatialCapabilities.capabilities)
            .isEqualTo(ALL_SPATIAL_CAPABILITIES)

        fakeSceneRuntime.requestHomeSpaceMode()

        assertThat(fakeSceneRuntime.spatialCapabilities.capabilities).isEqualTo(0)

        fakeSceneRuntime.requestFullSpaceMode()

        assertThat(fakeSceneRuntime.spatialCapabilities.capabilities)
            .isEqualTo(ALL_SPATIAL_CAPABILITIES)
    }

    @Test
    fun createPanelEntity_withDimensionsInMeter_returnsInitialValue() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val pose = Pose.Identity
        val view = View(activity)
        val dimensions = Dimensions(2f, 1f, 0f)
        val name = "test_panel"
        val parent = FakeEntity()
        val panelEntity =
            fakeSceneRuntime.createPanelEntity(activity, pose, view, dimensions, name, parent)

        assertThat(panelEntity).isInstanceOf(FakePanelEntity::class.java)
        assertThat(panelEntity.getPose()).isEqualTo(pose)
        assertThat(panelEntity.size.width).isWithin(0.001f).of(dimensions.width)
        assertThat(panelEntity.size.height).isWithin(0.001f).of(dimensions.height)
        assertThat(panelEntity.size.depth).isWithin(0.001f).of(dimensions.depth)
        assertThat(panelEntity.parent).isEqualTo(parent)
    }

    @Test
    fun createPanelEntity_withDimensionsInPixel_returnsInitialValue() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val pose = Pose.Identity
        val view = View(activity)
        val pixelDimensions = PixelDimensions(640, 480)
        val name = "test_panel"
        val parent = FakeEntity()
        val panelEntity =
            fakeSceneRuntime.createPanelEntity(activity, pose, view, pixelDimensions, name, parent)

        assertThat(panelEntity).isInstanceOf(FakePanelEntity::class.java)
        assertThat(panelEntity.getPose()).isEqualTo(pose)
        assertThat(panelEntity.sizeInPixels).isEqualTo(pixelDimensions)
        assertThat(panelEntity.parent).isEqualTo(parent)
    }

    @Test
    fun createActivityPanelEntity_returnsInitialValue() {
        val pose = Pose.Identity
        val windowBoundsPx = PixelDimensions(640, 480)
        val name = "test_activity_panel"
        val hostActivity = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val parent = FakeEntity()
        val activityPanelEntity =
            fakeSceneRuntime.createActivityPanelEntity(
                pose,
                windowBoundsPx,
                name,
                hostActivity,
                parent,
            )

        assertThat(activityPanelEntity).isInstanceOf(FakeActivityPanelEntity::class.java)
        assertThat(activityPanelEntity.getPose()).isEqualTo(pose)
        assertThat(activityPanelEntity.sizeInPixels).isEqualTo(windowBoundsPx)
        assertThat(activityPanelEntity.parent).isEqualTo(parent)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun createAnchorEntity_withPlaneAttributes_returnsInitialValue() {
        val bounds = Dimensions(2f, 1f, 0f)
        val planeType = PlaneType.HORIZONTAL
        val planeSemantic = PlaneSemantic.FLOOR
        val searchTimeout = Duration.ofMillis(100)
        val anchorEntity =
            fakeSceneRuntime.createAnchorEntity(bounds, planeType, planeSemantic, searchTimeout)

        assertThat(anchorEntity).isInstanceOf(FakeAnchorEntity::class.java)
        assertThat(anchorEntity.anchorCreationData.bounds).isEqualTo(bounds)
        assertThat(anchorEntity.anchorCreationData.planeType).isEqualTo(planeType)
        assertThat(anchorEntity.anchorCreationData.planeSemantic).isEqualTo(planeSemantic)
        assertThat(anchorEntity.anchorCreationData.searchTimeout).isEqualTo(searchTimeout)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun createAnchorEntity_withAnAnchor_returnsInitialValue() {
        val anchor =
            FakeRuntimeAnchor(
                Pose.Identity,
                FakeRuntimePlane(trackingState = TrackingState.STOPPED),
            )
        val anchorEntity = fakeSceneRuntime.createAnchorEntity(anchor)

        assertThat(anchorEntity).isInstanceOf(FakeAnchorEntity::class.java)
        assertThat(anchorEntity.anchor).isEqualTo(anchor)
    }

    @Test
    fun createGroupEntity_returnsInitialValue() {
        val pose = Pose.Identity
        val name = "test_entity"
        val parent = FakeEntity()
        val groupEntity = fakeSceneRuntime.createGroupEntity(pose, name, parent)

        assertThat(groupEntity).isInstanceOf(FakeEntity::class.java)
        assertThat(groupEntity.getPose()).isEqualTo(pose)
        assertThat(groupEntity.parent).isEqualTo(parent)
    }

    @Test
    fun createInteractableComponent_returnsInitialValue() {
        val listener = TestInputEventListener()

        assertThat(
                fakeSceneRuntime.createInteractableComponent({ command -> command.run() }, listener)
            )
            .isInstanceOf(FakeInteractableComponent::class.java)
    }

    @Test
    fun createMovableComponent_returnsInitialValue() {
        val anchorPlacement: Set<@JvmSuppressWildcards AnchorPlacement> =
            setOf(
                fakeSceneRuntime.createAnchorPlacementForPlanes(
                    setOf(PlaneType.HORIZONTAL),
                    setOf(PlaneSemantic.TABLE, PlaneSemantic.FLOOR),
                )
            )

        assertThat(
                fakeSceneRuntime.createMovableComponent(
                    systemMovable = false,
                    scaleInZ = false,
                    anchorPlacement = anchorPlacement,
                    shouldDisposeParentAnchor = false,
                )
            )
            .isInstanceOf(FakeMovableComponent::class.java)
    }

    @Test
    fun createAnchorPlacementForPlanes_returnsInitialValue() {
        val planeTypeFilter = setOf(PlaneType.HORIZONTAL)
        val planeSemanticFilter = setOf(PlaneSemantic.TABLE, PlaneSemantic.FLOOR)

        val anchorPlacement =
            fakeSceneRuntime.createAnchorPlacementForPlanes(planeTypeFilter, planeSemanticFilter)

        assertThat(anchorPlacement).isInstanceOf(FakeAnchorPlacement::class.java)
        assertThat(anchorPlacement.planeTypeFilter).isEqualTo(planeTypeFilter)
        assertThat(anchorPlacement.planeSemanticFilter).isEqualTo(planeSemanticFilter)
    }

    @Test
    fun createResizableComponent_returnsInitialValue() {
        val minimumSize = Dimensions(1.0f, 1.0f, 0.0f)
        val maximumSize = Dimensions(2.0f, 2.0f, 2.0f)
        val resizableComponent = fakeSceneRuntime.createResizableComponent(minimumSize, maximumSize)

        assertThat(resizableComponent).isInstanceOf(FakeResizableComponent::class.java)
        assertThat(resizableComponent.minimumSize).isEqualTo(minimumSize)
        assertThat(resizableComponent.maximumSize).isEqualTo(maximumSize)
    }

    @Test
    fun createPointerCaptureComponent_returnsInitialValue() {
        val executor = Executor { command -> command.run() }
        val stateListener: StateListener =
            object : StateListener {
                override fun onStateChanged(@PointerCaptureState newState: Int) {}
            }
        val inputListener = TestInputEventListener()
        val pointerCaptureComponent =
            fakeSceneRuntime.createPointerCaptureComponent(executor, stateListener, inputListener)

        assertThat(pointerCaptureComponent).isInstanceOf(FakePointerCaptureComponent::class.java)
        assertThat(pointerCaptureComponent.executor).isEqualTo(executor)
        assertThat(pointerCaptureComponent.stateListener).isEqualTo(stateListener)
    }

    @Test
    fun createSpatialPointerComponent_returnsInitialValue() {
        assertThat(fakeSceneRuntime.createSpatialPointerComponent())
            .isInstanceOf(FakeSpatialPointerComponent::class.java)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun createPersistedAnchorEntity_returnsInitialValue() {
        val uuid = UUID(0L, 0L)
        val searchTimeout = Duration.ofMillis(100)
        val persistedAnchorEntity =
            fakeSceneRuntime.createPersistedAnchorEntity(uuid, searchTimeout)

        assertThat(persistedAnchorEntity).isInstanceOf(FakeAnchorEntity::class.java)
        assertThat(persistedAnchorEntity.anchorCreationData.uuid).isEqualTo(uuid)
        assertThat(persistedAnchorEntity.anchorCreationData.searchTimeout).isEqualTo(searchTimeout)
    }

    @Test
    fun setFullSpaceMode_returnsSameBundle() {
        val bundle = Bundle().apply { putString("testkey", "testval") }

        assertThat(fakeSceneRuntime.setFullSpaceMode(bundle)).isEqualTo(bundle)
    }

    @Test
    fun setFullSpaceModeWithEnvironmentInherited_returnsSameBundle() {
        val bundle = Bundle().apply { putString("testkey", "testval") }
        assertThat(fakeSceneRuntime.setFullSpaceModeWithEnvironmentInherited(bundle))
            .isEqualTo(bundle)
    }

    @Test
    fun setPreferredAspectRatio_setsLastActivityAndRatio() {
        check(fakeSceneRuntime.lastSetPreferredAspectRatioActivity == null)
        check(fakeSceneRuntime.lastSetPreferredAspectRatioRatio == -1f)

        val activity = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val preferredRatio = 1.23f
        fakeSceneRuntime.setPreferredAspectRatio(activity, preferredRatio)

        assertThat(fakeSceneRuntime.lastSetPreferredAspectRatioActivity).isEqualTo(activity)
        assertThat(fakeSceneRuntime.lastSetPreferredAspectRatioRatio).isEqualTo(preferredRatio)
    }

    private class TestInputEventListener : InputEventListener {
        override fun onInputEvent(event: InputEvent) {}
    }
}
