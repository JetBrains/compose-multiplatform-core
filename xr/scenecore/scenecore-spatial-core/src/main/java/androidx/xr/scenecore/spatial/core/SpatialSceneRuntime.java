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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.xr.arcore.internal.Anchor;
import androidx.xr.runtime.math.Pose;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;
import androidx.xr.scenecore.internal.ActivityPanelEntity;
import androidx.xr.scenecore.internal.ActivitySpace;
import androidx.xr.scenecore.internal.AnchorEntity;
import androidx.xr.scenecore.internal.AnchorPlacement;
import androidx.xr.scenecore.internal.AudioTrackExtensionsWrapper;
import androidx.xr.scenecore.internal.CameraViewActivityPose;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.Entity;
import androidx.xr.scenecore.internal.GltfEntity;
import androidx.xr.scenecore.internal.GltfFeature;
import androidx.xr.scenecore.internal.HeadActivityPose;
import androidx.xr.scenecore.internal.InputEventListener;
import androidx.xr.scenecore.internal.InteractableComponent;
import androidx.xr.scenecore.internal.LoggingEntity;
import androidx.xr.scenecore.internal.MediaPlayerExtensionsWrapper;
import androidx.xr.scenecore.internal.MovableComponent;
import androidx.xr.scenecore.internal.PanelEntity;
import androidx.xr.scenecore.internal.PerceptionSpaceActivityPose;
import androidx.xr.scenecore.internal.PixelDimensions;
import androidx.xr.scenecore.internal.PlaneSemantic;
import androidx.xr.scenecore.internal.PlaneType;
import androidx.xr.scenecore.internal.PointerCaptureComponent;
import androidx.xr.scenecore.internal.RenderingEntityFactory;
import androidx.xr.scenecore.internal.ResizableComponent;
import androidx.xr.scenecore.internal.SceneRuntime;
import androidx.xr.scenecore.internal.SoundPoolExtensionsWrapper;
import androidx.xr.scenecore.internal.Space;
import androidx.xr.scenecore.internal.SpatialCapabilities;
import androidx.xr.scenecore.internal.SpatialEnvironment;
import androidx.xr.scenecore.internal.SpatialModeChangeListener;
import androidx.xr.scenecore.internal.SpatialPointerComponent;
import androidx.xr.scenecore.internal.SpatialVisibility;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;
import com.android.extensions.xr.space.ActivityPanel;
import com.android.extensions.xr.space.ActivityPanelLaunchParameters;
import com.android.extensions.xr.space.SpatialState;

import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
class SpatialSceneRuntime implements SceneRuntime, RenderingEntityFactory {
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

    private final SoundPoolExtensionsWrapper mSoundPoolExtensionsWrapper;
    private final AudioTrackExtensionsWrapper mAudioTrackExtensionsWrapper;
    private final MediaPlayerExtensionsWrapper mMediaPlayerExtensionsWrapper;

    private final Map<Consumer<SpatialCapabilities>, Executor>
            mSpatialCapabilitiesChangedListeners = new ConcurrentHashMap<>();

    private @Nullable Pair<Executor, Consumer<SpatialVisibility>> mSpatialVisibilityHandler = null;
    private final Map<Consumer<PixelDimensions>, Executor> mPerceivedResolutionChangedListeners =
            new ConcurrentHashMap<>();
    @VisibleForTesting boolean mIsExtensionVisibilityStateCallbackRegistered = false;

    // TODO b/373481538: remove lazy initialization once XR Extensions bug is fixed. This will allow
    // us to remove the lazySpatialStateProvider instance and pass the spatialState directly.
    private final AtomicReference<SpatialState> mSpatialState = new AtomicReference<>(null);

    // Returns the currently-known spatial state, or fetches it from the extensions if it has never
    // been set. The spatial state is kept updated in the SpatialStateCallback.
    private final Supplier<SpatialState> mLazySpatialStateProvider;

    private SpatialModeChangeListener mSpatialModeChangeListener = null;

    private final ActivitySpaceImpl mActivitySpace;
    private final HeadActivityPoseImpl mHeadActivityPose;
    private final List<CameraViewActivityPoseImpl> mCameraActivityPoses = new ArrayList<>();

    /** Returns the PerceptionSpaceActivityPose for the Session. */
    // TODO b/439932057 - Rename mPerceptionSpaceActivityPose to mPerceptionSpaceScenePose.
    public final PerceptionSpaceActivityPoseImpl mPerceptionSpaceActivityPose;

    private final PanelEntity mMainPanelEntity;

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

        mSoundPoolExtensionsWrapper =
                new SoundPoolExtensionsWrapperImpl(
                        extensions.getXrSpatialAudioExtensions().getSoundPoolExtensions());
        mAudioTrackExtensionsWrapper =
                new AudioTrackExtensionsWrapperImpl(
                        extensions.getXrSpatialAudioExtensions().getAudioTrackExtensions(),
                        entityManager);
        mMediaPlayerExtensionsWrapper =
                new MediaPlayerExtensionsWrapperImpl(
                        extensions.getXrSpatialAudioExtensions().getMediaPlayerExtensions());

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
        mHeadActivityPose =
                new HeadActivityPoseImpl(mActivitySpace, mActivitySpace, perceptionLibrary);
        mEntityManager.addSystemSpaceActivityPose(mHeadActivityPose);
        mPerceptionSpaceActivityPose =
                new PerceptionSpaceActivityPoseImpl(mActivitySpace, mActivitySpace);
        mEntityManager.addSystemSpaceActivityPose(mPerceptionSpaceActivityPose);
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
        mMainPanelEntity =
                new MainPanelEntityImpl(
                        activity, taskWindowLeashNode, extensions, entityManager, executor);
        mMainPanelEntity.setParent(mActivitySpace);
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
        mSpatialModeChangeListener = null;
        mExtensions.clearSpatialStateCallback(mActivity);

        clearSpatialVisibilityChangedListener();
        mPerceivedResolutionChangedListeners.clear();
        // This will trigger clearing the callback from XrExtensions if it was registered
        updateExtensionsVisibilityCallback();

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
    public @Nullable HeadActivityPose getHeadActivityPose() {
        // If it is unable to retrieve a pose the head in not yet loaded in openXR so return null.
        if (mHeadActivityPose.getPoseInOpenXrReferenceSpace() == null) {
            return null;
        }
        return mHeadActivityPose;
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
    public @NonNull PerceptionSpaceActivityPose getPerceptionSpaceActivityPose() {
        return mPerceptionSpaceActivityPose;
    }

    @Override
    public @NonNull SpatialEnvironment getSpatialEnvironment() {
        return mEnvironment;
    }

    @Override
    public void setSpatialModeChangeListener(SpatialModeChangeListener spatialModeChangeListener) {
        mSpatialModeChangeListener = spatialModeChangeListener;
        mActivitySpace.setSpatialModeChangeListener(spatialModeChangeListener);
    }

    @Override
    public SpatialModeChangeListener getSpatialModeChangeListener() {
        return mSpatialModeChangeListener;
    }

    @Override
    public @NonNull SoundPoolExtensionsWrapper getSoundPoolExtensionsWrapper() {
        return mSoundPoolExtensionsWrapper;
    }

    @Override
    public @NonNull AudioTrackExtensionsWrapper getAudioTrackExtensionsWrapper() {
        return mAudioTrackExtensionsWrapper;
    }

    @Override
    public @NonNull MediaPlayerExtensionsWrapper getMediaPlayerExtensionsWrapper() {
        return mMediaPlayerExtensionsWrapper;
    }

    @Override
    public @NonNull PanelEntity createPanelEntity(
            @NonNull Context context,
            @NonNull Pose pose,
            @NonNull View view,
            @NonNull Dimensions dimensions,
            @NonNull String name,
            @NonNull Entity parent) {

        Node node = mExtensions.createNode();
        PanelEntity panelEntity =
                new PanelEntityImpl(
                        context,
                        node,
                        view,
                        mExtensions,
                        mEntityManager,
                        dimensions,
                        name,
                        mExecutor);
        panelEntity.setParent(parent);
        panelEntity.setPose(pose, Space.PARENT);
        return panelEntity;
    }

    @Override
    public @NonNull PanelEntity createPanelEntity(
            @NonNull Context context,
            @NonNull Pose pose,
            @NonNull View view,
            @NonNull PixelDimensions pixelDimensions,
            @NonNull String name,
            @NonNull Entity parent) {

        Node node = mExtensions.createNode();
        PanelEntity panelEntity =
                new PanelEntityImpl(
                        context,
                        node,
                        view,
                        mExtensions,
                        mEntityManager,
                        pixelDimensions,
                        name,
                        mExecutor);
        panelEntity.setParent(parent);
        panelEntity.setPose(pose, Space.PARENT);
        return panelEntity;
    }

    @Override
    public @NonNull PanelEntity getMainPanelEntity() {
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setVisibility(mTaskWindowLeashNode, true).apply();
        }
        return mMainPanelEntity;
    }

    @Override
    public @NonNull ActivityPanelEntity createActivityPanelEntity(
            @NonNull Pose pose,
            @NonNull PixelDimensions windowBoundsPx,
            @NonNull String name,
            @NonNull Activity hostActivity,
            @NonNull Entity parent) {

        // TODO(b/352630140): Move this into a static factory method of ActivityPanelEntityImpl.
        Rect windowBoundsRect = new Rect(0, 0, windowBoundsPx.width, windowBoundsPx.height);
        ActivityPanel activityPanel =
                mExtensions.createActivityPanel(
                        hostActivity, new ActivityPanelLaunchParameters(windowBoundsRect));

        activityPanel.setWindowBounds(windowBoundsRect);
        ActivityPanelEntityImpl activityPanelEntity =
                new ActivityPanelEntityImpl(
                        hostActivity,
                        activityPanel.getNode(),
                        name,
                        mExtensions,
                        mEntityManager,
                        activityPanel,
                        windowBoundsPx,
                        mExecutor);
        activityPanelEntity.setParent(parent);
        activityPanelEntity.setPose(pose, Space.PARENT);
        return activityPanelEntity;
    }

    @Override
    public @NonNull AnchorEntity createAnchorEntity(
            @NonNull Dimensions bounds,
            @NonNull PlaneType planeType,
            @NonNull PlaneSemantic planeSemantic,
            @NonNull Duration searchTimeout) {
        Node node = mExtensions.createNode();
        return AnchorEntityImpl.createSemanticAnchor(
                mActivity,
                node,
                bounds,
                planeType,
                planeSemantic,
                searchTimeout,
                getActivitySpace(),
                getActivitySpace(),
                mExtensions,
                mEntityManager,
                mExecutor,
                mPerceptionLibrary);
    }

    @Override
    public @NonNull AnchorEntity createAnchorEntity(@NonNull Anchor anchor) {
        Node node = mExtensions.createNode();
        return AnchorEntityImpl.createAnchorFromRuntimeAnchor(
                mActivity,
                node,
                anchor,
                getActivitySpace(),
                getActivitySpace(),
                mExtensions,
                mEntityManager,
                mExecutor,
                mPerceptionLibrary);
    }

    @Override
    public @NonNull AnchorEntity createPersistedAnchorEntity(
            @NonNull UUID uuid, @NonNull Duration searchTimeout) {
        Node node = mExtensions.createNode();
        return AnchorEntityImpl.createPersistedAnchor(
                mActivity,
                node,
                uuid,
                searchTimeout,
                getActivitySpace(),
                getActivitySpace(),
                mExtensions,
                mEntityManager,
                mExecutor,
                mPerceptionLibrary);
    }

    @Override
    @NonNull
    public GltfEntity createGltfEntity(
            @NonNull GltfFeature feature, @NonNull Pose pose, @Nullable Entity parentEntity) {
        GltfEntity entity =
                new GltfEntityImpl(
                        mActivity, feature, parentEntity, mExtensions, mEntityManager, mExecutor);
        entity.setPose(pose, Space.PARENT);
        return entity;
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

    @Override
    public @NonNull LoggingEntity createLoggingEntity(@NonNull Pose pose) {
        LoggingEntityImpl entity = new LoggingEntityImpl(mActivity);
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

    @Override
    public void setSpatialVisibilityChangedListener(
            @NonNull Executor callbackExecutor, @NonNull Consumer<SpatialVisibility> listener) {
        mSpatialVisibilityHandler = new Pair<>(callbackExecutor, listener);
        updateExtensionsVisibilityCallback();
    }

    @Override
    public void clearSpatialVisibilityChangedListener() {
        mSpatialVisibilityHandler = null;
        updateExtensionsVisibilityCallback();
    }

    @Override
    public void addPerceivedResolutionChangedListener(
            @NonNull Executor callbackExecutor, @NonNull Consumer<PixelDimensions> listener) {
        mPerceivedResolutionChangedListeners.put(listener, callbackExecutor);
        updateExtensionsVisibilityCallback();
    }

    @Override
    public void removePerceivedResolutionChangedListener(
            @NonNull Consumer<PixelDimensions> listener) {
        mPerceivedResolutionChangedListeners.remove(listener);
        updateExtensionsVisibilityCallback();
    }

    private synchronized void updateExtensionsVisibilityCallback() {
        boolean shouldHaveCallback =
                mSpatialVisibilityHandler != null
                        || !mPerceivedResolutionChangedListeners.isEmpty();

        if (shouldHaveCallback && !mIsExtensionVisibilityStateCallbackRegistered) {
            // Register the combined callback
            try {
                mExtensions.setVisibilityStateCallback(
                        mActivity,
                        mExecutor, // Executor for the combined callback itself
                        (com.android.extensions.xr.space.VisibilityState visibilityStateEvent) -> {
                            // Dispatch to SpatialVisibility listener
                            if (mSpatialVisibilityHandler != null) {
                                SpatialVisibility jxrSpatialVisibility =
                                        RuntimeUtils.convertSpatialVisibility(
                                                visibilityStateEvent.getVisibility());
                                mSpatialVisibilityHandler.first.execute(
                                        () ->
                                                mSpatialVisibilityHandler.second.accept(
                                                        jxrSpatialVisibility));
                            }

                            // Dispatch to PerceivedResolution listeners
                            if (!mPerceivedResolutionChangedListeners.isEmpty()) {
                                PixelDimensions jxrPerceivedResolution =
                                        RuntimeUtils.convertPerceivedResolution(
                                                visibilityStateEvent.getPerceivedResolution());
                                mPerceivedResolutionChangedListeners.forEach(
                                        (listener, executor) ->
                                                executor.execute(
                                                        () ->
                                                                listener.accept(
                                                                        jxrPerceivedResolution)));
                            }
                        });
                mIsExtensionVisibilityStateCallbackRegistered = true;
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        "Could not set combined VisibilityStateCallback: " + e.getMessage());
            }
        } else if (!shouldHaveCallback && mIsExtensionVisibilityStateCallbackRegistered) {
            // Clear the combined callback
            try {
                mExtensions.clearVisibilityStateCallback(mActivity);
                mIsExtensionVisibilityStateCallbackRegistered = false;
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        "Could not clear VisibilityStateCallback: " + e.getMessage());
            }
        }
    }

    @Override
    public void requestFullSpaceMode() {
        // TODO: b/376934871 - Check async results.
        mExtensions.requestFullSpaceMode(
                mActivity, /* requestEnter= */ true, Runnable::run, (result) -> {});
    }

    @Override
    public void requestHomeSpaceMode() {
        // TODO: b/376934871 - Check async results.
        mExtensions.requestFullSpaceMode(
                mActivity, /* requestEnter= */ false, Runnable::run, (result) -> {});
    }

    @Override
    public @NonNull Bundle setFullSpaceMode(@NonNull Bundle bundle) {
        return mExtensions.setFullSpaceStartMode(bundle);
    }

    @Override
    public @NonNull Bundle setFullSpaceModeWithEnvironmentInherited(@NonNull Bundle bundle) {
        return mExtensions.setFullSpaceStartModeWithEnvironmentInherited(bundle);
    }

    @Override
    // TODO: b/441105591 - Add more unit tests.
    public void enablePanelDepthTest(boolean enabled) {
        mExtensions.enablePanelDepthTest(mActivity, enabled);
    }

    @Override
    public void setPreferredAspectRatio(@NonNull Activity activity, float preferredRatio) {
        // TODO: b/376934871 - Check async results.
        mExtensions.setPreferredAspectRatio(
                activity, preferredRatio, Runnable::run, (result) -> {});
    }

    @Override
    public @NonNull InteractableComponent createInteractableComponent(
            @NonNull Executor executor, @NonNull InputEventListener listener) {
        return new InteractableComponentImpl(executor, listener);
    }

    @Override
    public @NonNull AnchorPlacement createAnchorPlacementForPlanes(
            @NonNull Set<PlaneType> planeTypeFilter,
            @NonNull Set<PlaneSemantic> planeSemanticFilter) {
        AnchorPlacementImpl anchorPlacement = new AnchorPlacementImpl();
        anchorPlacement.mPlaneTypeFilter.addAll(planeTypeFilter);
        anchorPlacement.mPlaneSemanticFilter.addAll(planeSemanticFilter);
        return anchorPlacement;
    }

    @Override
    public @NonNull MovableComponent createMovableComponent(
            boolean systemMovable,
            boolean scaleInZ,
            @NonNull Set<AnchorPlacement> anchorPlacement,
            boolean shouldDisposeParentAnchor) {
        return new MovableComponentImpl(
                systemMovable,
                scaleInZ,
                anchorPlacement,
                shouldDisposeParentAnchor,
                mPerceptionLibrary,
                mExtensions,
                mActivitySpace,
                mActivitySpace,
                mPerceptionSpaceActivityPose,
                mEntityManager,
                new PanelShadowRenderer(
                        mActivitySpace, mPerceptionSpaceActivityPose, mActivity, mExtensions),
                mExecutor);
    }

    @Override
    public @NonNull ResizableComponent createResizableComponent(
            @NonNull Dimensions minimumSize, @NonNull Dimensions maximumSize) {
        return new ResizableComponentImpl(mExecutor, mExtensions, minimumSize, maximumSize);
    }

    // Suppress warnings for factory function
    @Override
    @SuppressLint("ExecutorRegistration")
    @SuppressWarnings("ExecutorRegistration")
    public @NonNull PointerCaptureComponent createPointerCaptureComponent(
            @NonNull Executor executor,
            PointerCaptureComponent.@NonNull StateListener stateListener,
            @NonNull InputEventListener inputListener) {
        return new PointerCaptureComponentImpl(executor, stateListener, inputListener);
    }

    @Override
    public @NonNull SpatialPointerComponent createSpatialPointerComponent() {
        return new SpatialPointerComponentImpl(mExtensions);
    }
}
