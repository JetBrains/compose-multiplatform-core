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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.xr.runtime.internal.CameraViewActivityPose;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.common.BaseActivityPose;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;
import androidx.xr.scenecore.impl.perception.ViewProjection;
import androidx.xr.scenecore.impl.perception.ViewProjections;

/**
 * A ActivityPose representing a user's camera. This can be used to determine the location and field
 * of view of the camera.
 */
final class CameraViewActivityPoseImpl extends BaseActivityPose implements CameraViewActivityPose {
    private static final String TAG = "CameraViewActivityPose";
    private final PerceptionLibrary mPerceptionLibrary;
    @CameraType private final int mCameraType;
    private final OpenXrActivityPoseHelper mOpenXrActivityPoseHelper;
    // Default the pose to null. A null pose indicates that the camera is not ready yet.
    private Pose mLastOpenXrPose = null;

    CameraViewActivityPoseImpl(
            @CameraType int cameraType,
            ActivitySpaceImpl activitySpace,
            AndroidXrEntity activitySpaceRoot,
            PerceptionLibrary perceptionLibrary) {
        mCameraType = cameraType;
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

    @Nullable
    private ViewProjection getViewProjection() {
        final Session session = mPerceptionLibrary.getSession();
        if (session == null) {
            Log.w(TAG, "Cannot retrieve the camera pose with a null perception session.");
            return null;
        }
        ViewProjections perceptionViews = session.getStereoViews();
        if (perceptionViews == null) {
            Log.e(TAG, "Error retrieving the camera.");
            return null;
        }
        if (mCameraType == CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE) {
            return perceptionViews.getLeftEye();
        } else if (mCameraType == CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE) {
            return perceptionViews.getRightEye();
        } else {
            Log.w(TAG, "Unsupported camera type: " + mCameraType);
            return null;
        }
    }

    /** Gets the pose in the OpenXR reference space. Can be null if it is not yet ready. */
    @Nullable
    public Pose getPoseInOpenXrReferenceSpace() {
        ViewProjection viewProjection = getViewProjection();
        if (viewProjection != null) {
            mLastOpenXrPose = RuntimeUtils.fromPerceptionPose(viewProjection.getPose());
        }
        return mLastOpenXrPose;
    }

    @Override
    @CameraType
    public int getCameraType() {
        return mCameraType;
    }

    @NonNull
    @Override
    public Fov getFov() {
        ViewProjection viewProjection = getViewProjection();
        if (viewProjection == null) {
            return new Fov(0, 0, 0, 0);
        }
        return RuntimeUtils.fovFromPerceptionFov(viewProjection.getFov());
    }
}
