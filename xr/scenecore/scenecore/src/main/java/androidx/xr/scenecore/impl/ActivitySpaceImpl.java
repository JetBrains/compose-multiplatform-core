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

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.ResolvableFuture;
import androidx.xr.runtime.internal.ActivityPose;
import androidx.xr.runtime.internal.ActivityPose.HitTestFilterValue;
import androidx.xr.runtime.internal.ActivitySpace;
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.HitTestResult;
import androidx.xr.runtime.internal.SpaceValue;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.Vec3;
import com.android.extensions.xr.space.Bounds;
import com.android.extensions.xr.space.SpatialState;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementation of a RealityCore ActivitySpaceImpl.
 *
 * <p>This is used to create an entity that contains the task node.
 */
@SuppressWarnings({"UnnecessarilyFullyQualified"})
final class ActivitySpaceImpl extends SystemSpaceEntityImpl implements ActivitySpace {

    private static final String TAG = "ActivitySpaceImpl";

    private final Set<OnBoundsChangedListener> mBoundsListeners =
            Collections.synchronizedSet(new HashSet<>());

    private final Activity mActivity;
    private final Supplier<SpatialState> mSpatialStateProvider;
    private final AtomicReference<Dimensions> mBounds = new AtomicReference<>();

    ActivitySpaceImpl(
            Node taskNode,
            Activity activity,
            XrExtensions extensions,
            EntityManager entityManager,
            Supplier<SpatialState> spatialStateProvider,
            ScheduledExecutorService executor) {
        super(taskNode, extensions, entityManager, executor);
        mActivity = activity;
        mSpatialStateProvider = spatialStateProvider;
    }

    /** Returns the identity pose since this entity defines the origin of the activity space. */
    @Override
    public Pose getPoseInActivitySpace() {
        return new Pose();
    }

    /** Returns the identity pose since we assume the activity space is the world space root. */
    @NonNull
    @Override
    public Pose getActivitySpacePose() {

        return new Pose();
    }

    @NonNull
    @Override
    public Vector3 getActivitySpaceScale() {
        return new Vector3(1.0f, 1.0f, 1.0f);
    }

    @Override
    public void setParent(Entity parent) {
        Log.e(TAG, "Cannot set parent for the ActivitySpace.");
    }

    @Override
    public void setScale(@NonNull Vector3 scale, @SpaceValue int relativeTo) {
        // TODO(b/349391097): make this behavior consistent with AnchorEntityImpl
        Log.e(TAG, "Cannot set scale for the ActivitySpace.");
    }

    @SuppressWarnings("ObjectToString")
    @Override
    public void dispose() {
        Log.i(TAG, "Disposing " + this);
        super.dispose();
    }

    @NonNull
    @Override
    public Dimensions getBounds() {
        // The bounds are kept in sync with the Extensions in the onBoundsChangedEvent callback. We
        // only
        // invoke getSpatialState if they've never been set.
        return mBounds.updateAndGet(
                oldBounds -> {
                    if (oldBounds == null) {
                        Bounds bounds = mSpatialStateProvider.get().getBounds();
                        return new Dimensions(
                                bounds.getWidth(), bounds.getHeight(), bounds.getDepth());
                    }
                    return oldBounds;
                });
    }

    @Override
    public void addOnBoundsChangedListener(@NonNull OnBoundsChangedListener listener) {
        mBoundsListeners.add(listener);
    }

    @Override
    public void removeOnBoundsChangedListener(@NonNull OnBoundsChangedListener listener) {
        mBoundsListeners.remove(listener);
    }

    /**
     * This method is called by the Runtime when the bounds of the Activity change. We dispatch the
     * event upwards to the JXRCoreSession via ActivitySpace.
     *
     * <p>Note that this call happens on the Activity's UI thread, so we should be careful not to
     * block it.
     */
    public void onBoundsChanged(Bounds newBounds) {
        Dimensions newDimensions =
                mBounds.updateAndGet(
                        oldBounds ->
                                new Dimensions(
                                        newBounds.getWidth(),
                                        newBounds.getHeight(),
                                        newBounds.getDepth()));
        for (OnBoundsChangedListener listener : mBoundsListeners) {
            listener.onBoundsChanged(newDimensions);
        }
    }

    @SuppressWarnings("RestrictTo")
    static class HitTestResultConsumer
            implements com.android.extensions.xr.function.Consumer<
                    com.android.extensions.xr.space.HitTestResult> {
        ResolvableFuture<HitTestResult> mFuture;

        HitTestResultConsumer(ResolvableFuture<HitTestResult> future) {
            mFuture = future;
        }

        @Override
        @SuppressWarnings("RestrictTo")
        public void accept(com.android.extensions.xr.space.HitTestResult hitTestResultExt) {
            mFuture.set(RuntimeUtils.getHitTestResult(hitTestResultExt));
        }
    }

    @Override
    @SuppressWarnings("RestrictTo")
    public ListenableFuture<HitTestResult> hitTest(
            @NonNull Vector3 origin,
            @NonNull Vector3 direction,
            @HitTestFilterValue int hitTestFilter) {
        ResolvableFuture<HitTestResult> hitTestFuture = ResolvableFuture.create();
        HitTestResultConsumer hitTestConsumer = new HitTestResultConsumer(hitTestFuture);

        mExtensions.hitTest(
                mActivity,
                new Vec3(origin.getX(), origin.getY(), origin.getZ()),
                new Vec3(direction.getX(), direction.getY(), direction.getZ()),
                RuntimeUtils.getHitTestFilter(hitTestFilter),
                mExecutor,
                hitTestConsumer);
        return hitTestFuture;
    }

    @Override
    @SuppressWarnings("RestrictTo")
    public ListenableFuture<HitTestResult> hitTestRelativeToActivityPose(
            @NonNull Vector3 origin,
            @NonNull Vector3 direction,
            @HitTestFilterValue int hitTestFilter,
            ActivityPose activityPose) {

        // Get the Translation of the origin relative to the ActivitySpace.
        Vector3 originInActivitySpace =
                activityPose.transformPoseTo(new Pose(origin), this).getTranslation();

        // Get the Translation of the direction pose relative to the ActivitySpace.
        Pose directionPoseInActivitySpace = activityPose.transformPoseTo(new Pose(direction), this);

        // Convert the direction pose to a direction vector relative to the ActivitySpace.
        Vector3 directionInActivitySpace =
                directionPoseInActivitySpace
                        .compose(activityPose.getActivitySpacePose().getInverse())
                        .getTranslation();

        ResolvableFuture<HitTestResult> updatedHitTestFuture = ResolvableFuture.create();

        // Perform the hit test then convert the result to be relative to the provided ActivityPose.
        ListenableFuture<HitTestResult> hitTestFuture =
                hitTest(originInActivitySpace, directionInActivitySpace, hitTestFilter);
        hitTestFuture.addListener(
                () -> {
                    try {
                        // Convert the hit test result to be relative to the provided ActivityPose.
                        HitTestResult result = hitTestFuture.get();
                        // No need to do a conversion if the hit test result is not a hit.
                        if (result.getDistance() == Float.POSITIVE_INFINITY) {
                            updatedHitTestFuture.set(result);
                        }
                        // Update the hit position and surface normal to be relative to the
                        // ActivityPose.
                        Vector3 updatedHitPosition =
                                result.getHitPosition() == null
                                        ? null
                                        : transformPoseTo(
                                                        new Pose(result.getHitPosition()),
                                                        activityPose)
                                                .getTranslation();
                        Vector3 updatedSurfaceNormal =
                                result.getSurfaceNormal() == null
                                        ? null
                                        : transformPoseTo(
                                                        new Pose(
                                                                new Vector3(
                                                                        result.getSurfaceNormal())),
                                                        activityPose)
                                                .compose(
                                                        this.transformPoseTo(
                                                                        Pose.Identity, activityPose)
                                                                .getInverse())
                                                .getTranslation();
                        updatedHitTestFuture.set(
                                new HitTestResult(
                                        updatedHitPosition,
                                        updatedSurfaceNormal,
                                        result.getSurfaceType(),
                                        result.getDistance()));
                    } catch (InterruptedException | ExecutionException e) {
                        Log.e(TAG, "Failed to get hit test result: " + e.getMessage());
                        updatedHitTestFuture.setException(e);
                    }
                },
                mExecutor);
        return updatedHitTestFuture;
    }
}
