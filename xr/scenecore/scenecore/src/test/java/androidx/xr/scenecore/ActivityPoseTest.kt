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

package androidx.xr.scenecore

import androidx.xr.runtime.internal.ActivitySpace as RtActivitySpace
import androidx.xr.runtime.internal.CameraViewActivityPose as RtCameraViewActivityPose
import androidx.xr.runtime.internal.HeadActivityPose as RtHeadActivityPose
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.PerceptionSpaceActivityPose as RtPerceptionSpaceActivityPose
import androidx.xr.runtime.math.Pose
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityPoseTest {
    private val entityManager = EntityManager()
    private val mockRuntime = mock<JxrPlatformAdapter>()
    private val mockActivitySpace = mock<RtActivitySpace>()
    private val mockHeadActivityPose = mock<RtHeadActivityPose>()
    private val mockCameraViewActivityPose = mock<RtCameraViewActivityPose>()
    private val mockPerceptionSpaceActivityPose = mock<RtPerceptionSpaceActivityPose>()

    private lateinit var spatialUser: SpatialUser
    private var head: Head? = null
    private lateinit var activitySpace: ActivitySpace
    private var camera: CameraView? = null
    private lateinit var perceptionSpace: PerceptionSpace

    @Before
    fun setUp() {
        whenever(mockRuntime.headActivityPose).thenReturn(mockHeadActivityPose)
        whenever(mockRuntime.getCameraViewActivityPose(anyInt()))
            .thenReturn(mockCameraViewActivityPose)
        whenever(mockRuntime.activitySpace).thenReturn(mockActivitySpace)
        whenever(mockRuntime.perceptionSpaceActivityPose)
            .thenReturn(mockPerceptionSpaceActivityPose)
        head = Head.create(mockRuntime)
        camera = CameraView.createLeft(mockRuntime)
        activitySpace = ActivitySpace.create(mockRuntime, entityManager)
        perceptionSpace = PerceptionSpace.create(mockRuntime)
    }

    @Test
    fun allActivityPoseTransformPoseTo_callsRuntimeActivityPoseImplTransformPoseTo() {
        whenever(mockHeadActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        whenever(mockCameraViewActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        whenever(mockPerceptionSpaceActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        val pose = Pose.Identity

        assertThat(head?.transformPoseTo(pose, head!!)).isEqualTo(pose)
        assertThat(camera!!.transformPoseTo(pose, camera!!)).isEqualTo(pose)
        assertThat(perceptionSpace.transformPoseTo(pose, perceptionSpace)).isEqualTo(pose)

        verify(mockHeadActivityPose).transformPoseTo(any(), any())
        verify(mockCameraViewActivityPose).transformPoseTo(any(), any())
    }

    @Test
    fun allActivityPoseTransformPoseToEntity_callsRuntimeActivityPoseImplTransformPoseTo() {
        whenever(mockHeadActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        whenever(mockCameraViewActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        whenever(mockPerceptionSpaceActivityPose.transformPoseTo(any(), any())).thenReturn(Pose())
        val pose = Pose.Identity

        assertThat(head!!.transformPoseTo(pose, activitySpace)).isEqualTo(pose)
        assertThat(camera!!.transformPoseTo(pose, activitySpace)).isEqualTo(pose)
        assertThat(perceptionSpace.transformPoseTo(pose, activitySpace)).isEqualTo(pose)

        verify(mockHeadActivityPose).transformPoseTo(any(), any())
        verify(mockCameraViewActivityPose).transformPoseTo(any(), any())
        verify(mockPerceptionSpaceActivityPose).transformPoseTo(any(), any())
    }

    @Test
    fun allActivityPoseTransformPoseFromEntity_callsRuntimeActivityPoseImplTransformPoseTo() {
        whenever(mockActivitySpace.transformPoseTo(any(), any())).thenReturn(Pose())
        val pose = Pose.Identity

        assertThat(activitySpace.transformPoseTo(pose, head!!)).isEqualTo(pose)
        assertThat(activitySpace.transformPoseTo(pose, camera!!)).isEqualTo(pose)
        assertThat(activitySpace.transformPoseTo(pose, perceptionSpace)).isEqualTo(pose)

        verify(mockActivitySpace, times(3)).transformPoseTo(any(), any())
    }

    @Test
    fun allActivityPoseGetActivitySpacePose_callsRuntimeActivityPoseImplGetActivitySpacePose() {
        whenever(mockHeadActivityPose.activitySpacePose).thenReturn(Pose())
        whenever(mockCameraViewActivityPose.activitySpacePose).thenReturn(Pose())
        whenever(mockPerceptionSpaceActivityPose.activitySpacePose).thenReturn(Pose())
        val pose = Pose.Identity

        assertThat(head!!.getActivitySpacePose()).isEqualTo(pose)
        assertThat(camera!!.getActivitySpacePose()).isEqualTo(pose)
        assertThat(perceptionSpace.getActivitySpacePose()).isEqualTo(pose)

        verify(mockHeadActivityPose).activitySpacePose
        verify(mockCameraViewActivityPose).activitySpacePose
        verify(mockPerceptionSpaceActivityPose).activitySpacePose
    }

    @Test
    fun cameraView_getFov_returnsFov() {
        val rtFov = RtCameraViewActivityPose.Fov(1f, 2f, 3f, 4f)
        whenever(mockCameraViewActivityPose.fov).thenReturn(rtFov)

        assertThat(camera!!.fov).isEqualTo(Fov(1f, 2f, 3f, 4f))

        verify(mockCameraViewActivityPose).fov
    }

    @Test
    fun cameraView_getFovTwice_returnsUpdatedFov() {
        val rtFov = RtCameraViewActivityPose.Fov(1f, 2f, 3f, 4f)
        whenever(mockCameraViewActivityPose.fov)
            .thenReturn(rtFov)
            .thenReturn(RtCameraViewActivityPose.Fov(5f, 6f, 7f, 8f))

        assertThat(camera!!.fov).isEqualTo(Fov(1f, 2f, 3f, 4f))
        assertThat(camera!!.fov).isEqualTo(Fov(5f, 6f, 7f, 8f))

        verify(mockCameraViewActivityPose, times(2)).fov
    }
}
