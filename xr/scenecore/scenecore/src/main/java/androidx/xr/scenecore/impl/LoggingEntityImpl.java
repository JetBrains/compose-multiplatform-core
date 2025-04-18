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
import androidx.concurrent.futures.ResolvableFuture;
import androidx.xr.runtime.internal.ActivityPose;
import androidx.xr.runtime.internal.ActivityPose.HitTestRangeValue;
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.HitTestResult;
import androidx.xr.runtime.internal.InputEventListener;
import androidx.xr.runtime.internal.LoggingEntity;
import androidx.xr.runtime.internal.SpaceValue;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.common.BaseEntity;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/** Implementation of a RealityCore Entity that logs its function calls. */
class LoggingEntityImpl extends BaseEntity implements LoggingEntity {

    private static final String TAG = "RealityCoreRuntime";

    LoggingEntityImpl() {
        Log.i(TAG, "Creating LoggingEntity.");
    }

    @NonNull
    @Override
    public Pose getPose(@SpaceValue int relativeTo) {
        Log.i(
                TAG,
                "Getting Logging Entity pose: "
                        + super.getPose(relativeTo)
                        + " relativeTo: "
                        + relativeTo);
        return super.getPose(relativeTo);
    }

    @Override
    public void setPose(@NonNull Pose pose, @SpaceValue int relativeTo) {
        Log.i(TAG, "Setting Logging Entity pose to: " + pose + " relativeTo: " + relativeTo);
        super.setPose(pose, relativeTo);
    }

    @NonNull
    @Override
    public Pose getActivitySpacePose() {
        Log.i(TAG, "Getting Logging Entity activitySpacePose.");
        return new Pose();
    }

    @NonNull
    @Override
    public Pose transformPoseTo(@NonNull Pose pose, @NonNull ActivityPose destination) {
        Log.i(
                TAG,
                "Transforming pose "
                        + pose
                        + " to be relative to the destination ActivityPose: "
                        + destination);
        return new Pose();
    }

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @NonNull
    @SuppressWarnings("RestrictTo")
    @Override
    public ListenableFuture<HitTestResult> hitTest(
            @NonNull Vector3 origin,
            @NonNull Vector3 direction,
            @HitTestRangeValue int hitTestRange) {
        Log.i(
                TAG,
                "Hit testing Logging Entity with origin: "
                        + origin
                        + " direction: "
                        + direction
                        + " hitTestRange: "
                        + hitTestRange);
        ResolvableFuture<HitTestResult> future = ResolvableFuture.create();
        future.set(
                new HitTestResult(
                        new Vector3(),
                        new Vector3(),
                        HitTestResult.HitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_UNKNOWN,
                        1f));
        return future;
    }

    @Override
    public void addChild(@NonNull Entity child) {
        Log.i(TAG, "Adding child Entity: " + child);
        super.addChild(child);
    }

    @Override
    public void addChildren(@NonNull List<? extends Entity> children) {
        Log.i(TAG, "Adding child Entities: " + children);
        super.addChildren(children);
    }

    @Override
    public Entity getParent() {
        Log.i(TAG, "Getting Logging Entity parent: " + super.getParent());
        return super.getParent();
    }

    @Override
    public void setParent(Entity parent) {
        if (!(parent instanceof LoggingEntityImpl)) {
            Log.e(TAG, "Parent of a LoggingEntity must be a Logging entity");
            return;
        }
        Log.i(TAG, "Setting Logging Entity parent to: " + parent);
        super.setParent(parent);
    }

    @NonNull
    @Override
    public List<Entity> getChildren() {
        Log.i(TAG, "Getting Logging Entity children: " + super.getChildren());
        return super.getChildren();
    }

    @Override
    public void addInputEventListener(
            @NonNull Executor executor, @NonNull InputEventListener consumer) {
        Log.i(TAG, "Add input consumer " + consumer + " executor " + executor);
    }

    @Override
    public void removeInputEventListener(@NonNull InputEventListener consumer) {
        Log.i(TAG, "Remove input consumer " + consumer);
    }

    @Override
    public void dispose() {
        Log.i(TAG, "dispose");
    }
}
