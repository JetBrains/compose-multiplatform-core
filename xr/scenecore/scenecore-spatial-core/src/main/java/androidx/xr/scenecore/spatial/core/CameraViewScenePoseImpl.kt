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

package androidx.xr.scenecore.spatial.core

import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.impl.perception.PerceptionLibrary
import androidx.xr.scenecore.impl.perception.ViewProjection
import androidx.xr.scenecore.runtime.CameraViewScenePose
import androidx.xr.scenecore.runtime.HitTestResult
import androidx.xr.scenecore.runtime.PixelDimensions
import androidx.xr.scenecore.runtime.ScenePose

/**
 * A ScenePose representing a user's camera. This can be used to determine the location and field of
 * view of the camera.
 */
internal class CameraViewScenePoseImpl(
    @CameraViewScenePose.CameraType override val cameraType: Int,
    private val activitySpace: ActivitySpaceImpl,
    activitySpaceRoot: AndroidXrEntity,
    private val perceptionLibrary: PerceptionLibrary,
) : BaseScenePose(), CameraViewScenePose {

    private val openXrScenePoseHelper: OpenXrScenePoseHelper =
        OpenXrScenePoseHelper(activitySpace, activitySpaceRoot)
    // Default the pose to null. A null pose indicates that the camera is not ready yet.
    private var lastOpenXrPose: Pose? = null

    override val poseInActivitySpace: Pose
        get() = openXrScenePoseHelper.getPoseInActivitySpace(poseInOpenXrReferenceSpace)

    override val activitySpacePose: Pose
        get() = openXrScenePoseHelper.getActivitySpacePose(poseInOpenXrReferenceSpace)

    override val activitySpaceScale: Vector3
        // This WorldPose is assumed to always have a scale of 1.0f in the OpenXR reference space.
        get() = openXrScenePoseHelper.getActivitySpaceScale(Vector3(1f, 1f, 1f))

    override suspend fun hitTest(
        origin: Vector3,
        direction: Vector3,
        @ScenePose.HitTestFilterValue hitTestFilter: Int,
    ): HitTestResult =
        activitySpace.hitTestRelativeToActivityPose(origin, direction, hitTestFilter, this)

    private val viewProjection: ViewProjection?
        get() {
            val session = perceptionLibrary.session ?: return null
            val perceptionViews = session.stereoViews ?: return null

            return when (cameraType) {
                CameraViewScenePose.CameraType.CAMERA_TYPE_LEFT_EYE -> perceptionViews.leftEye
                CameraViewScenePose.CameraType.CAMERA_TYPE_RIGHT_EYE -> perceptionViews.rightEye
                else -> null
            }
        }

    /** Gets the pose in the OpenXR reference space. Can be null if it is not yet ready. */
    val poseInOpenXrReferenceSpace: Pose?
        get() {
            viewProjection?.let { lastOpenXrPose = RuntimeUtils.fromPerceptionPose(it.pose) }
            return lastOpenXrPose
        }

    override val fov: CameraViewScenePose.Fov
        get() {
            val vp = viewProjection ?: return CameraViewScenePose.Fov(0f, 0f, 0f, 0f)
            return RuntimeUtils.fovFromPerceptionFov(vp.fov)
        }

    // Suppress warnings: windowManager's getDefaultDisplay and getRealMetrics.
    @Suppress("DEPRECATION")
    override val displayResolutionInPixels: PixelDimensions
        get() {
            val activity = perceptionLibrary.activity
            val windowManager =
                activity.getSystemService(WindowManager::class.java)
                    // WindowManager not available, cannot get display resolution. Returning (0, 0).
                    ?: return PixelDimensions(0, 0)

            val display =
                windowManager.defaultDisplay
                    // Default display not available, cannot get display resolution. Returning
                    // (0,0).
                    ?: return PixelDimensions(0, 0)

            val displayMetrics = DisplayMetrics()
            display.getRealMetrics(displayMetrics)

            // Divide the width by 2 because we want single eye resolution, not full display
            // resolution
            return PixelDimensions(displayMetrics.widthPixels / 2, displayMetrics.heightPixels)
        }
}
