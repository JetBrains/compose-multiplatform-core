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

package androidx.xr.scenecore.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.xr.runtime.internal.HeadActivityPose;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.common.BaseActivityPose;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;

/**
 * An ActivityPose representing the head of the user. This can be used to determine the location of
 * the user's head.
 */
class HeadActivityPoseImpl extends BaseActivityPose implements HeadActivityPose {
    private final PerceptionLibrary mPerceptionLibrary;
    private final OpenXrActivityPoseHelper mOpenXrActivityPoseHelper;
    // Default the pose to null. A null pose indicates that the head is not ready yet.
    private Pose mLastOpenXrPose = null;

    HeadActivityPoseImpl(
            ActivitySpaceImpl activitySpace,
            AndroidXrEntity activitySpaceRoot,
            PerceptionLibrary perceptionLibrary) {
        mPerceptionLibrary = perceptionLibrary;
        mOpenXrActivityPoseHelper = new OpenXrActivityPoseHelper(activitySpace, activitySpaceRoot);
    }

    @Override
    public Pose getPoseInActivitySpace() {
        return mOpenXrActivityPoseHelper.getPoseInActivitySpace(getPoseInOpenXrReferenceSpace());
    }

    @NonNull
    @Override
    public Pose getActivitySpacePose() {
        return mOpenXrActivityPoseHelper.getActivitySpacePose(getPoseInOpenXrReferenceSpace());
    }

    @NonNull
    @Override
    public Vector3 getActivitySpaceScale() {
        // This WorldPose is assumed to always have a scale of 1.0f in the OpenXR reference space.
        return mOpenXrActivityPoseHelper.getActivitySpaceScale(new Vector3(1f, 1f, 1f));
    }

    /** Gets the pose in the OpenXR reference space. Can be null if it is not yet ready. */
    @Nullable
    public Pose getPoseInOpenXrReferenceSpace() {
        final Session session = mPerceptionLibrary.getSession();
        if (session == null) {
            return mLastOpenXrPose;
        }
        androidx.xr.scenecore.impl.perception.Pose perceptionHeadPose = session.getHeadPose();
        if (perceptionHeadPose != null) {
            mLastOpenXrPose = RuntimeUtils.fromPerceptionPose(perceptionHeadPose);
        }
        return mLastOpenXrPose;
    }
}
