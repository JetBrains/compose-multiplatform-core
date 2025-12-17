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

import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.impl.perception.PerceptionLibrary
import androidx.xr.scenecore.runtime.HeadScenePose
import androidx.xr.scenecore.runtime.HitTestResult
import androidx.xr.scenecore.runtime.ScenePose

/**
 * An ScenePose representing the head of the user. This can be used to determine the location of the
 * user's head.
 */
internal class HeadScenePoseImpl(
    private val activitySpace: ActivitySpaceImpl,
    activitySpaceRoot: AndroidXrEntity,
    private val perceptionLibrary: PerceptionLibrary,
) : BaseScenePose(), HeadScenePose {

    private val openXrScenePoseHelper = OpenXrScenePoseHelper(activitySpace, activitySpaceRoot)
    // Default the pose to null. A null pose indicates that the head is not ready yet.
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

    /** Gets the pose in the OpenXR reference space. Can be null if it is not yet ready. */
    val poseInOpenXrReferenceSpace: Pose?
        get() {
            val session = perceptionLibrary.session ?: return lastOpenXrPose
            val perceptionHeadPose = session.headPose
            if (perceptionHeadPose != null) {
                lastOpenXrPose = RuntimeUtils.fromPerceptionPose(perceptionHeadPose)
            }
            return lastOpenXrPose
        }
}
