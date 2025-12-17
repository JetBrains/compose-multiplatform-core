/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.xr.scenecore.spatial.core

import android.app.Activity
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.xr.runtime.math.Matrix4
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.math.assertPose
import androidx.xr.runtime.testing.math.assertVector3
import androidx.xr.scenecore.impl.perception.PerceptionLibrary
import androidx.xr.scenecore.impl.perception.Session
import androidx.xr.scenecore.impl.perception.ViewProjection
import androidx.xr.scenecore.impl.perception.ViewProjections
import androidx.xr.scenecore.runtime.CameraViewScenePose.CameraType
import androidx.xr.scenecore.runtime.CameraViewScenePose.Fov
import androidx.xr.scenecore.runtime.extensions.XrExtensionsProvider
import androidx.xr.scenecore.testing.FakeScheduledExecutorService
import com.android.extensions.xr.XrExtensions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq as eqArg
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Suppress warnings: windowManager's getDefaultDisplay and getRealMetrics.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.TARGET_SDK])
class CameraViewScenePoseImplTest {

    private val activity: Activity =
        Robolectric.buildActivity(Activity::class.java).create().start().get()
    private val activitySpaceRoot = Mockito.mock(AndroidXrEntity::class.java)
    private val xrExtensions: XrExtensions = XrExtensionsProvider.getXrExtensions()!!
    private val perceptionLibrary: PerceptionLibrary = Mockito.mock(PerceptionLibrary::class.java)
    private val session: Session = Mockito.mock(Session::class.java)
    private val executor = FakeScheduledExecutorService()

    private val activitySpace =
        ActivitySpaceImpl(
            xrExtensions.createNode(),
            activity,
            xrExtensions,
            EntityManager(),
            { xrExtensions.getSpatialState(activity) },
            /* unscaledGravityAlignedActivitySpace= */ false,
            executor,
        )

    /** Creates a CameraViewScenePoseImpl. */
    private fun createCameraViewScenePose(@CameraType cameraType: Int): CameraViewScenePoseImpl {
        return CameraViewScenePoseImpl(
            cameraType,
            activitySpace,
            activitySpaceRoot,
            perceptionLibrary,
        )
    }

    @Test
    fun getCameraType_returnsCameraType() {
        val cameraScenePoseLeft = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        assertThat(cameraScenePoseLeft.cameraType).isEqualTo(CameraType.CAMERA_TYPE_LEFT_EYE)

        val cameraScenePoseRight = createCameraViewScenePose(CameraType.CAMERA_TYPE_RIGHT_EYE)

        assertThat(cameraScenePoseRight.cameraType).isEqualTo(CameraType.CAMERA_TYPE_RIGHT_EYE)
    }

    @Test
    fun getFov_returnsCorrectFov() {
        val fovLeft = Fov(1f, 2f, 3f, 4f)
        val fovRight = Fov(5f, 6f, 7f, 8f)

        `when`(perceptionLibrary.session).thenReturn(session)
        val viewProjectionLeft =
            ViewProjection(
                RuntimeUtils.poseToPerceptionPose(Pose()),
                RuntimeUtils.perceptionFovFromFov(fovLeft),
            )
        val viewProjectionRight =
            ViewProjection(
                RuntimeUtils.poseToPerceptionPose(Pose()),
                RuntimeUtils.perceptionFovFromFov(fovRight),
            )
        `when`(session.stereoViews)
            .thenReturn(ViewProjections(viewProjectionLeft, viewProjectionRight))

        val cameraViewScenePose = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        assertThat(cameraViewScenePose.fov.angleLeft).isEqualTo(fovLeft.angleLeft)
        assertThat(cameraViewScenePose.fov.angleRight).isEqualTo(fovLeft.angleRight)
        assertThat(cameraViewScenePose.fov.angleUp).isEqualTo(fovLeft.angleUp)
        assertThat(cameraViewScenePose.fov.angleDown).isEqualTo(fovLeft.angleDown)

        val cameraScenePoseRight = createCameraViewScenePose(CameraType.CAMERA_TYPE_RIGHT_EYE)

        assertThat(cameraScenePoseRight.fov.angleLeft).isEqualTo(fovRight.angleLeft)
        assertThat(cameraScenePoseRight.fov.angleRight).isEqualTo(fovRight.angleRight)
        assertThat(cameraScenePoseRight.fov.angleUp).isEqualTo(fovRight.angleUp)
        assertThat(cameraScenePoseRight.fov.angleDown).isEqualTo(fovRight.angleDown)
    }

    @Test
    fun transformPoseTo_returnsCorrectPose() {
        // Set the activity space to the root of the underlying OpenXR reference space.
        activitySpace.mOpenXrReferenceSpaceTransform.set(Matrix4.Identity)
        val poseLeft = Pose(Vector3(1f, 2f, 3f), Quaternion(1f, 0f, 0f, 0f))
        val poseRight = Pose(Vector3(4f, 5f, 6f), Quaternion(0f, 1f, 0f, 0f))
        val fov = Fov(0f, 0f, 0f, 0f)

        `when`(perceptionLibrary.session).thenReturn(session)
        val viewProjectionLeft =
            ViewProjection(
                RuntimeUtils.poseToPerceptionPose(poseLeft),
                RuntimeUtils.perceptionFovFromFov(fov),
            )
        val viewProjectionRight =
            ViewProjection(
                RuntimeUtils.poseToPerceptionPose(poseRight),
                RuntimeUtils.perceptionFovFromFov(fov),
            )
        `when`(session.stereoViews)
            .thenReturn(ViewProjections(viewProjectionLeft, viewProjectionRight))

        val cameraScenePoseLeft = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        assertPose(cameraScenePoseLeft.transformPoseTo(Pose(), activitySpace), poseLeft)

        val cameraScenePoseRight = createCameraViewScenePose(CameraType.CAMERA_TYPE_RIGHT_EYE)

        assertPose(cameraScenePoseRight.transformPoseTo(Pose(), activitySpace), poseRight)
    }

    @Test
    @Throws(Exception::class)
    fun getActivitySpaceScale_returnsInverseOfActivitySpaceWorldScale() {
        val activitySpaceScale = 5f
        activitySpace.setOpenXrReferenceSpaceTransform(Matrix4.fromScale(activitySpaceScale))

        val cameraScenePoseLeft = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        assertVector3(
            cameraScenePoseLeft.activitySpaceScale,
            Vector3(1f, 1f, 1f).div(activitySpaceScale),
        )

        val cameraScenePoseRight = createCameraViewScenePose(CameraType.CAMERA_TYPE_RIGHT_EYE)

        assertVector3(
            cameraScenePoseRight.activitySpaceScale,
            Vector3(1f, 1f, 1f).div(activitySpaceScale),
        )
    }

    @Test
    fun getDisplayResolutionInPixels_returnsCorrectResolution() {
        val cameraScenePose = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        val mockActivity = Mockito.mock(Activity::class.java)
        `when`(perceptionLibrary.activity).thenReturn(mockActivity)
        val mockWindowManager = Mockito.mock(WindowManager::class.java)
        val mockDisplay = Mockito.mock(Display::class.java)
        `when`(mockActivity.getSystemService(eqArg(WindowManager::class.java)))
            .thenReturn(mockWindowManager)
        `when`(mockWindowManager.defaultDisplay).thenReturn(mockDisplay)

        val expectedDisplayWidth = 2560
        val expectedDisplayHeight = 1440

        // Mock the call to getRealMetrics to write the expected values into the metrics object
        Mockito.doAnswer { invocation ->
                val metrics = invocation.getArgument<DisplayMetrics>(0)
                metrics.widthPixels = expectedDisplayWidth
                metrics.heightPixels = expectedDisplayHeight
                null
            }
            .`when`(mockDisplay)
            .getRealMetrics(Mockito.any(DisplayMetrics::class.java))

        val resolution = cameraScenePose.displayResolutionInPixels

        // The implementation divides width by 2 for single eye resolution
        assertThat(resolution.width).isEqualTo(expectedDisplayWidth / 2)
        assertThat(resolution.height).isEqualTo(expectedDisplayHeight)
    }

    @Test
    fun getDisplayResolutionInPixels_nullWindowManager_returnsZeroDimensions() {
        val cameraScenePose = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        val mockActivity = Mockito.mock(Activity::class.java)
        `when`(perceptionLibrary.activity).thenReturn(mockActivity)
        `when`(mockActivity.getSystemService(eqArg(WindowManager::class.java))).thenReturn(null)

        val resolution = cameraScenePose.displayResolutionInPixels

        assertThat(resolution.width).isEqualTo(0)
        assertThat(resolution.height).isEqualTo(0)
    }

    @Test
    fun getDisplayResolutionInPixels_nullDisplay_returnsZeroDimensions() {
        val cameraScenePose = createCameraViewScenePose(CameraType.CAMERA_TYPE_LEFT_EYE)

        val mockActivity = Mockito.mock(Activity::class.java)
        `when`(perceptionLibrary.activity).thenReturn(mockActivity)
        val mockWindowManager = Mockito.mock(WindowManager::class.java)
        `when`(mockActivity.getSystemService(eqArg(WindowManager::class.java)))
            .thenReturn(mockWindowManager)
        `when`(mockWindowManager.defaultDisplay).thenReturn(null)

        val resolution = cameraScenePose.displayResolutionInPixels

        assertThat(resolution.width).isEqualTo(0)
        assertThat(resolution.height).isEqualTo(0)
    }
}
