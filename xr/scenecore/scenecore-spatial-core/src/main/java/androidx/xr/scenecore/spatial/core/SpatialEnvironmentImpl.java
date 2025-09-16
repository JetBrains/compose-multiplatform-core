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

import androidx.annotation.VisibleForTesting;
import androidx.xr.runtime.internal.ExrImageResource;
import androidx.xr.runtime.internal.GltfModelResource;
import androidx.xr.runtime.internal.SpatialEnvironment;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.environment.EnvironmentVisibilityState;
import com.android.extensions.xr.environment.PassthroughVisibilityState;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;
import com.android.extensions.xr.passthrough.PassthroughState;
import com.android.extensions.xr.space.SpatialState;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Concrete implementation of SpatialEnvironment / XR Wallpaper for Android XR. */
@SuppressWarnings({"BanSynchronizedMethods", "BanConcurrentHashMap"})
final class SpatialEnvironmentImpl implements SpatialEnvironment, Consumer<Consumer<Node>> {
    public static final String PASSTHROUGH_NODE_NAME = "EnvironmentPassthroughNode";
    @VisibleForTesting final Node mPassthroughNode;
    private final XrExtensions mXrExtensions;
    private @Nullable Activity mActivity;
    private Node mRootEnvironmentNode;
    private @Nullable Consumer<Node> mOnBeforeNodeAttachedListener = null;
    private boolean mIsPreferredSpatialEnvironmentActive = false;
    private final AtomicReference<SpatialEnvironmentPreference> mSpatialEnvironmentPreference =
            new AtomicReference<>(null);

    // The active passthrough opacity value is updated with every opacity change event. A null value
    // indicates it has not yet been initialized and the value should be read from the
    // spatialStateProvider.
    private float mActivePassthroughOpacity = NO_PASSTHROUGH_OPACITY_PREFERENCE;
    // Initialized to null to let system control opacity until preference is explicitly set.
    private float mPassthroughOpacityPreference = NO_PASSTHROUGH_OPACITY_PREFERENCE;
    private final Supplier<SpatialState> mSpatialStateProvider;
    private SpatialState mPreviousSpatialState = null;

    // Store listeners with their executors
    private final Map<Consumer<Boolean>, Executor> mOnSpatialEnvironmentChangedListeners =
            new ConcurrentHashMap<>();

    private final Map<Consumer<Float>, Executor> mOnPassthroughOpacityChangedListeners =
            new ConcurrentHashMap<>();

    SpatialEnvironmentImpl(
            @NonNull Activity activity,
            @NonNull XrExtensions xrExtensions,
            @NonNull Node rootSceneNode,
            @NonNull Supplier<SpatialState> spatialStateProvider) {
        mActivity = activity;
        mXrExtensions = xrExtensions;
        mPassthroughNode = xrExtensions.createNode();
        mRootEnvironmentNode = xrExtensions.createNode();
        mSpatialStateProvider = spatialStateProvider;

        try (NodeTransaction transaction = xrExtensions.createNodeTransaction()) {
            transaction
                    .setName(mPassthroughNode, PASSTHROUGH_NODE_NAME)
                    .setParent(mPassthroughNode, rootSceneNode)
                    .apply();
        }
    }

    // TODO: Remove these once we know the Equals() and Hashcode() methods are correct.
    boolean hasEnvironmentVisibilityChanged(@NonNull SpatialState spatialState) {
        if (mPreviousSpatialState == null) {
            return true;
        }

        final EnvironmentVisibilityState previousEnvironmentVisibility =
                mPreviousSpatialState.getEnvironmentVisibility();
        final EnvironmentVisibilityState currentEnvironmentVisibility =
                spatialState.getEnvironmentVisibility();

        return previousEnvironmentVisibility.getCurrentState()
                != currentEnvironmentVisibility.getCurrentState();
    }

    // TODO: Remove these once we know the Equals() and Hashcode() methods are correct.
    boolean hasPassthroughVisibilityChanged(@NonNull SpatialState spatialState) {
        if (mPreviousSpatialState == null) {
            return true;
        }

        final PassthroughVisibilityState previousPassthroughVisibility =
                mPreviousSpatialState.getPassthroughVisibility();
        final PassthroughVisibilityState currentPassthroughVisibility =
                spatialState.getPassthroughVisibility();

        if (previousPassthroughVisibility.getCurrentState()
                != currentPassthroughVisibility.getCurrentState()) {
            return true;
        }

        return previousPassthroughVisibility.getOpacity()
                != currentPassthroughVisibility.getOpacity();
    }

    @Override
    public void accept(Consumer<Node> nodeConsumer) {
        this.mOnBeforeNodeAttachedListener = nodeConsumer;
    }

    // Package Private enum to return which spatial states have changed.
    enum ChangedSpatialStates {
        ENVIRONMENT_CHANGED,
        PASSTHROUGH_CHANGED
    }

    // Package Private method to set the current passthrough opacity and
    // isPreferredSpatialEnvironmentActive from SceneRuntime.
    // This method is synchronized because it sets several internal state variables at once, which
    // should be treated as an atomic set. We could consider replacing with AtomicReferences.
    synchronized EnumSet<ChangedSpatialStates> setSpatialState(@NonNull SpatialState spatialState) {
        EnumSet<ChangedSpatialStates> changedSpatialStates =
                EnumSet.noneOf(ChangedSpatialStates.class);
        boolean passthroughVisibilityChanged = hasPassthroughVisibilityChanged(spatialState);
        if (passthroughVisibilityChanged) {
            changedSpatialStates.add(ChangedSpatialStates.PASSTHROUGH_CHANGED);
            mActivePassthroughOpacity =
                    RuntimeUtils.getPassthroughOpacity(spatialState.getPassthroughVisibility());
        }

        // TODO: b/371082454 - Check if the app is in FSM to ensure APP_VISIBLE refers to the
        // current app and not another app that is visible.
        boolean environmentVisibilityChanged = hasEnvironmentVisibilityChanged(spatialState);
        if (environmentVisibilityChanged) {
            changedSpatialStates.add(ChangedSpatialStates.ENVIRONMENT_CHANGED);
            mIsPreferredSpatialEnvironmentActive =
                    RuntimeUtils.getIsPreferredSpatialEnvironmentActive(
                            spatialState.getEnvironmentVisibility().getCurrentState());
        }

        mPreviousSpatialState = spatialState;
        return changedSpatialStates;
    }

    @Override
    public void setPreferredPassthroughOpacity(float opacity) {
        // To work around floating-point precision issues, the opacity preference is documented to
        // clamp to 0.0f if it is set below 1% opacity and it clamps to 1.0f if it is set above 99%
        // opacity.
        // TODO: b/3692012 - Publicly document the passthrough opacity threshold values with
        // constants
        float newPassthroughOpacityPreference =
                opacity == NO_PASSTHROUGH_OPACITY_PREFERENCE
                        ? NO_PASSTHROUGH_OPACITY_PREFERENCE
                        : (opacity < 0.01f ? 0.0f : (opacity > 0.99f ? 1.0f : opacity));

        if (Objects.equals(newPassthroughOpacityPreference, mPassthroughOpacityPreference)) {
            return;
        }

        mPassthroughOpacityPreference = newPassthroughOpacityPreference;

        // Passthrough should be enabled only if the user has explicitly set the
        // PassthroughOpacityPreference to a valid value, otherwise disabled.
        if (mPassthroughOpacityPreference != NO_PASSTHROUGH_OPACITY_PREFERENCE) {
            try (NodeTransaction transaction = mXrExtensions.createNodeTransaction()) {
                transaction
                        .setPassthroughState(
                                mPassthroughNode,
                                mPassthroughOpacityPreference,
                                PassthroughState.PASSTHROUGH_MODE_MAX)
                        .apply();
            }
        } else {
            try (NodeTransaction transaction = mXrExtensions.createNodeTransaction()) {
                transaction
                        .setPassthroughState(
                                mPassthroughNode,
                                0.0f, // not show the app passthrough
                                PassthroughState.PASSTHROUGH_MODE_OFF)
                        .apply();
            }
        }
    }

    // Synchronized because we may need to update the entire Spatial State if the opacity has not
    // been initialized previously.
    @Override
    public synchronized float getCurrentPassthroughOpacity() {
        if (mActivePassthroughOpacity == NO_PASSTHROUGH_OPACITY_PREFERENCE) {
            setSpatialState(mSpatialStateProvider.get());
        }
        return mActivePassthroughOpacity;
    }

    @Override
    public float getPreferredPassthroughOpacity() {
        return mPassthroughOpacityPreference;
    }

    // This is called on the Activity's UI thread - so we should be careful to not block it.
    synchronized void firePassthroughOpacityChangedEvent() {
        mOnPassthroughOpacityChangedListeners.forEach(
                (listener, executor) -> {
                    executor.execute(() -> listener.accept(getCurrentPassthroughOpacity()));
                });
    }

    @Override
    public void addOnPassthroughOpacityChangedListener(
            @NonNull Executor executor, @NonNull Consumer<Float> listener) {
        mOnPassthroughOpacityChangedListeners.put(listener, executor);
    }

    @Override
    public void removeOnPassthroughOpacityChangedListener(@NonNull Consumer<Float> listener) {
        mOnPassthroughOpacityChangedListeners.remove(listener);
    }

    @Override
    public void setPreferredSpatialEnvironment(
            @Nullable SpatialEnvironmentPreference newPreference) {
        // This synchronized block makes sure following members are updated atomically:
        // mSpatialEnvironmentPreference, mRootEnvironmentNode, mXrExtensions,
        // mGeometrySubspaceSplitEngine, mGeometrySubspaceImpressNode.
        mSpatialEnvironmentPreference.getAndUpdate(
                prevPreference -> {
                    if (Objects.equals(newPreference, prevPreference)) {
                        return prevPreference;
                    }

                    GltfModelResource newGeometry =
                            newPreference == null ? null : newPreference.getGeometry();
                    GltfModelResource prevGeometry =
                            prevPreference == null ? null : prevPreference.getGeometry();
                    ExrImageResource newSkybox =
                            newPreference == null ? null : newPreference.getSkybox();
                    ExrImageResource prevSkybox =
                            prevPreference == null ? null : prevPreference.getSkybox();

                    // TODO(b/329907079): Map GltfModelResourceImpl to GltfModelResource in Impl
                    // Layer
                    if (newGeometry != null) {
                        // TODO(b/434249465): Complete the implementation by RenderingRuntime
                    }

                    // TODO b/329907079: Map ExrImageResourceImpl to ExrImageResource in Impl Layer
                    if (newSkybox != null) {
                        // TODO(b/434249465): Complete the implementation by RenderingRuntime
                    }

                    if (!Objects.equals(newGeometry, prevGeometry)) {
                        // TODO(b/434249465): Complete the implementation by RenderingRuntime
                    }

                    // TODO: b/392948759 - Fix StrictMode violations triggered whenever skybox is
                    // set.
                    if (!Objects.equals(newSkybox, prevSkybox)
                            || (prevPreference == null && newPreference != null)) {
                        // TODO(b/434249465): Complete the implementation by RenderingRuntime
                    }

                    if (newPreference == null) {
                        // Detaching the app environment to go back to the system environment.
                        mXrExtensions.detachSpatialEnvironment(
                                mActivity,
                                Runnable::run,
                                (result) -> {
                                    // TODO: b/376934871 - Better error handling?
                                    // TODO: b/434242257 - Replace logging with appropriate
                                    //  error handling.
                                });
                    } else {
                        // TODO(b/408276187): Add unit test that verifies that the skybox mode is
                        // correctly set.
                        int skyboxMode = XrExtensions.ENVIRONMENT_SKYBOX_APP;
                        if (newSkybox == null) {
                            skyboxMode = XrExtensions.NO_SKYBOX;
                        }
                        // Transitioning to a new app environment.
                        Node currentRootEnvironmentNode;
                        if (!Objects.equals(newGeometry, prevGeometry)) {
                            // Environment geometry has changed, create a new environment node and
                            // attach the geometry subspace to it.
                            currentRootEnvironmentNode = mXrExtensions.createNode();
                            // TODO(b/434249465): Complete the implementation by RenderingRuntime
                        } else {
                            // Environment geometry has not changed, use the existing environment
                            // node.
                            currentRootEnvironmentNode = mRootEnvironmentNode;
                        }
                        if (mOnBeforeNodeAttachedListener != null) {
                            mOnBeforeNodeAttachedListener.accept(currentRootEnvironmentNode);
                        }
                        mXrExtensions.attachSpatialEnvironment(
                                mActivity,
                                currentRootEnvironmentNode,
                                skyboxMode,
                                Runnable::run,
                                (result) -> {
                                    // Update the root environment node to the current root node.
                                    mRootEnvironmentNode = currentRootEnvironmentNode;
                                    // TODO: b/376934871 - Better error handling?
                                    // TODO: b/434242257 - Replace logging with appropriate error
                                    //  handling.
                                });
                    }

                    return newPreference;
                });
    }

    @Override
    public @Nullable SpatialEnvironmentPreference getPreferredSpatialEnvironment() {
        return mSpatialEnvironmentPreference.get();
    }

    @Override
    public boolean isPreferredSpatialEnvironmentActive() {
        return mIsPreferredSpatialEnvironmentActive;
    }

    // This is called on the Activity's UI thread - so we should be careful to not block it.
    synchronized void fireOnSpatialEnvironmentChangedEvent() {
        final boolean isActive = mIsPreferredSpatialEnvironmentActive;
        mOnSpatialEnvironmentChangedListeners.forEach(
                (listener, executor) -> {
                    executor.execute(() -> listener.accept(isActive));
                });
    }

    @Override
    public void addOnSpatialEnvironmentChangedListener(
            @NonNull Executor executor, @NonNull Consumer<Boolean> listener) {
        mOnSpatialEnvironmentChangedListeners.put(listener, executor);
    }

    @Override
    public void removeOnSpatialEnvironmentChangedListener(@NonNull Consumer<Boolean> listener) {
        mOnSpatialEnvironmentChangedListeners.remove(listener);
    }

    /**
     * Disposes of the environment and all of its resources.
     *
     * <p>This should be called when the environment is no longer needed.
     */
    public void dispose() {
        mActivePassthroughOpacity = NO_PASSTHROUGH_OPACITY_PREFERENCE;
        mPassthroughOpacityPreference = NO_PASSTHROUGH_OPACITY_PREFERENCE;
        mRootEnvironmentNode = null;
        mSpatialEnvironmentPreference.set(null);
        mIsPreferredSpatialEnvironmentActive = false;
        mOnPassthroughOpacityChangedListeners.clear();
        mOnSpatialEnvironmentChangedListeners.clear();
        // TODO: b/376934871 - Check async results.
        mXrExtensions.detachSpatialEnvironment(mActivity, Runnable::run, (result) -> {});
        mActivity = null;
    }
}
