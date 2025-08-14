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

package androidx.xr.scenecore.spatial.core;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;
import androidx.xr.runtime.internal.ActivitySpace;
import androidx.xr.runtime.internal.CameraViewActivityPose;
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.SceneRuntime;
import androidx.xr.runtime.internal.Space;
import androidx.xr.runtime.internal.SpatialCapabilities;
import androidx.xr.runtime.math.Pose;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;
import com.android.extensions.xr.space.SpatialState;

import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of [SceneRuntime] for devices that support the [Feature.SPATIAL] system feature.
 */
// Suppress BanSynchronizedMethods for onSpatialStateChanged().
// Suppress BanConcurrentHashMap for mSpatialCapabilitiesChangedListeners since XR minSdk is 24.
@SuppressWarnings({"BanSynchronizedMethods", "BanConcurrentHashMap"})
class SpatialSceneRuntime implements SceneRuntime {
    private @Nullable Activity mActivity;
    private final ScheduledExecutorService mExecutor;
    private final XrExtensions mExtensions;
    private final Node mSceneRootNode;
    private final Node mTaskWindowLeashNode;
    private final int mOpenXrReferenceSpaceType;
    private boolean mIsDisposed;
    private final EntityManager mEntityManager;
    private final PerceptionLibrary mPerceptionLibrary;
    private final SpatialEnvironmentImpl mEnvironment;

    private final Map<Consumer<SpatialCapabilities>, Executor>
            mSpatialCapabilitiesChangedListeners = new ConcurrentHashMap<>();

    // TODO b/373481538: remove lazy initialization once XR Extensions bug is fixed. This will allow
    // us to remove the lazySpatialStateProvider instance and pass the spatialState directly.
    private final AtomicReference<SpatialState> mSpatialState = new AtomicReference<>(null);

    // Returns the currently-known spatial state, or fetches it from the extensions if it has never
    // been set. The spatial state is kept updated in the SpatialStateCallback.
    private final Supplier<SpatialState> mLazySpatialStateProvider;

    private final ActivitySpaceImpl mActivitySpace;
    private final List<CameraViewActivityPoseImpl> mCameraActivityPoses = new ArrayList<>();

    private SpatialSceneRuntime(
            @NonNull Activity activity,
            @NonNull ScheduledExecutorService executor,
            @NonNull XrExtensions extensions,
            @NonNull EntityManager entityManager,
            @NonNull PerceptionLibrary perceptionLibrary,
            @NonNull Node sceneRootNode,
            @NonNull Node taskWindowLeashNode,
            boolean unscaledGravityAlignedActivitySpace) {
        mActivity = activity;
        mExecutor = executor;
        mExtensions = extensions;
        mSceneRootNode = sceneRootNode;
        mTaskWindowLeashNode = taskWindowLeashNode;
        mEntityManager = entityManager;
        mPerceptionLibrary = perceptionLibrary;
        mOpenXrReferenceSpaceType = extensions.getOpenXrWorldReferenceSpaceType();

        mLazySpatialStateProvider =
                () ->
                        mSpatialState.updateAndGet(
                                oldSpatialState -> {
                                    if (oldSpatialState == null) {
                                        oldSpatialState = mExtensions.getSpatialState(activity);
                                    }
                                    return oldSpatialState;
                                });
        setSpatialStateCallback();

        mEnvironment =
                new SpatialEnvironmentImpl(
                        activity, extensions, sceneRootNode, mLazySpatialStateProvider);

        mActivitySpace =
                new ActivitySpaceImpl(
                        sceneRootNode,
                        activity,
                        extensions,
                        entityManager,
                        mLazySpatialStateProvider,
                        unscaledGravityAlignedActivitySpace,
                        executor);
        mEntityManager.addSystemSpaceActivityPose(mActivitySpace);
        mCameraActivityPoses.add(
                new CameraViewActivityPoseImpl(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        mActivitySpace,
                        mActivitySpace,
                        perceptionLibrary));
        mCameraActivityPoses.add(
                new CameraViewActivityPoseImpl(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        mActivitySpace,
                        mActivitySpace,
                        perceptionLibrary));
        mCameraActivityPoses.forEach(mEntityManager::addSystemSpaceActivityPose);
    }

    static @NonNull SpatialSceneRuntime create(
            @NonNull Activity activity,
            @NonNull ScheduledExecutorService executor,
            @NonNull XrExtensions extensions,
            @NonNull EntityManager entityManager,
            @NonNull PerceptionLibrary perceptionLibrary,
            boolean unscaledGravityAlignedActivitySpace) {
        Node sceneRootNode = extensions.createNode();
        Node taskWindowLeashNode = extensions.createNode();
        // TODO: b/376934871 - Check async results.
        extensions.attachSpatialScene(
                activity, sceneRootNode, taskWindowLeashNode, executor, (result) -> {});
        try (NodeTransaction transaction = extensions.createNodeTransaction()) {
            transaction
                    .setName(sceneRootNode, "SpatialSceneAndActivitySpaceRootNode")
                    .setParent(taskWindowLeashNode, sceneRootNode)
                    .setName(taskWindowLeashNode, "MainPanelAndTaskWindowLeashNode")
                    .apply();
        }

        SpatialSceneRuntime runtime =
                new SpatialSceneRuntime(
                        activity,
                        executor,
                        extensions,
                        entityManager,
                        perceptionLibrary,
                        sceneRootNode,
                        taskWindowLeashNode,
                        unscaledGravityAlignedActivitySpace);
        runtime.initPerceptionLibrary();
        return runtime;
    }

    /** Create a new @c SpatialSceneRuntime. */
    public static @NonNull SpatialSceneRuntime create(
            @NonNull Activity activity, @NonNull ScheduledExecutorService executor) {
        return create(
                activity,
                executor,
                Objects.requireNonNull(XrExtensionsProvider.getXrExtensions()),
                new EntityManager(),
                new PerceptionLibrary(),
                false);
    }

    private void initPerceptionLibrary() {
        // Already initialized. Skip init perception session.
        if (mPerceptionLibrary.getSession() != null) return;

        ListenableFuture<Session> sessionFuture =
                mPerceptionLibrary.initSession(mActivity, mOpenXrReferenceSpaceType, mExecutor);
        Objects.requireNonNull(sessionFuture)
                .addListener(
                        () -> {
                            try {
                                sessionFuture.get();
                            } catch (Exception e) {
                                if (e instanceof InterruptedException) {
                                    Thread.currentThread().interrupt();
                                }
                                throw new RuntimeException(
                                        "Failed to init perception session with error: "
                                                + e.getMessage());
                            }
                        },
                        mExecutor);
    }

    @Override
    public void dispose() {
        if (mIsDisposed) {
            return;
        }
        mEnvironment.dispose();

        // TODO: b/376934871 - Check async results.
        mExtensions.detachSpatialScene(mActivity, Runnable::run, (result) -> {});
        mActivity = null;
        mEntityManager.getAllEntities().forEach(Entity::dispose);
        mEntityManager.clear();
        mIsDisposed = true;
    }

    @VisibleForTesting
    @NonNull Node getSceneRootNode() {
        return mSceneRootNode;
    }

    @VisibleForTesting
    @NonNull Node getTaskWindowLeashNode() {
        return mTaskWindowLeashNode;
    }

    @Override
    public @NonNull SpatialCapabilities getSpatialCapabilities() {
        return RuntimeUtils.convertSpatialCapabilities(
                mLazySpatialStateProvider.get().getSpatialCapabilities());
    }

    @Override
    public @NonNull ActivitySpace getActivitySpace() {
        return mActivitySpace;
    }

    @Override
    public @Nullable CameraViewActivityPose getCameraViewActivityPose(
            @CameraViewActivityPose.CameraType int cameraType) {
        CameraViewActivityPoseImpl cameraViewActivityPose = null;
        if (cameraType == CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE) {
            cameraViewActivityPose = mCameraActivityPoses.get(0);
        } else if (cameraType == CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE) {
            cameraViewActivityPose = mCameraActivityPoses.get(1);
        }
        // If it is unable to retrieve a pose the camera in not yet loaded in openXR so return null.
        if (cameraViewActivityPose == null
                || cameraViewActivityPose.getPoseInOpenXrReferenceSpace() == null) {
            return null;
        }
        return cameraViewActivityPose;
    }

    @Override
    public @NonNull Entity createGroupEntity(
            @NonNull Pose pose, @NonNull String name, @NonNull Entity parent) {
        Node node = mExtensions.createNode();
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setName(node, name).apply();
        }

        // This entity is used to back SceneCore's GroupEntity.
        Entity entity =
                new AndroidXrEntity(mActivity, node, mExtensions, mEntityManager, mExecutor) {};
        entity.setParent(parent);
        entity.setPose(pose, Space.PARENT);
        return entity;
    }

    // Note that this is called on the Activity's UI thread so we should be careful to not block it.
    // It is synchronized because we assume this.spatialState cannot be updated elsewhere during the
    // execution of this method.
    @VisibleForTesting
    synchronized void onSpatialStateChanged(@NonNull SpatialState newSpatialState) {
        SpatialState previousSpatialState = mSpatialState.getAndSet(newSpatialState);
        boolean spatialCapabilitiesChanged =
                previousSpatialState == null
                        || !newSpatialState
                                .getSpatialCapabilities()
                                .equals(previousSpatialState.getSpatialCapabilities());

        boolean hasBoundsChanged =
                previousSpatialState == null
                        || !newSpatialState.getBounds().equals(previousSpatialState.getBounds());

        EnumSet<SpatialEnvironmentImpl.ChangedSpatialStates> changedSpatialStates =
                mEnvironment.setSpatialState(newSpatialState);
        boolean environmentVisibilityChanged =
                changedSpatialStates.contains(
                        SpatialEnvironmentImpl.ChangedSpatialStates.ENVIRONMENT_CHANGED);
        boolean passthroughVisibilityChanged =
                changedSpatialStates.contains(
                        SpatialEnvironmentImpl.ChangedSpatialStates.PASSTHROUGH_CHANGED);

        // Fire the state change events only after all the states have been updated.
        if (environmentVisibilityChanged) {
            mEnvironment.fireOnSpatialEnvironmentChangedEvent();
        }
        if (passthroughVisibilityChanged) {
            mEnvironment.firePassthroughOpacityChangedEvent();
        }

        // Get the scene parent transform and update the activity space.
        if (newSpatialState.getSceneParentTransform() != null) {
            mActivitySpace.handleOriginUpdate(
                    RuntimeUtils.getMatrix(newSpatialState.getSceneParentTransform()));
        }

        if (spatialCapabilitiesChanged) {
            SpatialCapabilities spatialCapabilities =
                    RuntimeUtils.convertSpatialCapabilities(
                            newSpatialState.getSpatialCapabilities());

            mSpatialCapabilitiesChangedListeners.forEach(
                    (listener, executor) ->
                            executor.execute(() -> listener.accept(spatialCapabilities)));
        }

        if (hasBoundsChanged) {
            mActivitySpace.onBoundsChanged(newSpatialState.getBounds());
        }
    }

    private void setSpatialStateCallback() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mExtensions.setSpatialStateCallback(
                mActivity, mainHandler::post, this::onSpatialStateChanged);
    }

    @Override
    public void addSpatialCapabilitiesChangedListener(
            @NonNull Executor executor, @NonNull Consumer<SpatialCapabilities> listener) {
        mSpatialCapabilitiesChangedListeners.put(listener, executor);
    }

    @Override
    public void removeSpatialCapabilitiesChangedListener(
            @NonNull Consumer<SpatialCapabilities> listener) {
        mSpatialCapabilitiesChangedListeners.remove(listener);
    }
}
