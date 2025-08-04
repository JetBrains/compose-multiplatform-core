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
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.SceneRuntime;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Implementation of [SceneRuntime] for devices that support the [Feature.SPATIAL] system feature.
 */
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

    private SpatialSceneRuntime(
            @NonNull Activity activity,
            @NonNull ScheduledExecutorService executor,
            @NonNull XrExtensions extensions,
            @NonNull EntityManager entityManager,
            @NonNull PerceptionLibrary perceptionLibrary,
            @NonNull Node sceneRootNode,
            @NonNull Node taskWindowLeashNode) {
        mActivity = activity;
        mExecutor = executor;
        mExtensions = extensions;
        mSceneRootNode = sceneRootNode;
        mTaskWindowLeashNode = taskWindowLeashNode;
        mEntityManager = entityManager;
        mPerceptionLibrary = perceptionLibrary;
        mOpenXrReferenceSpaceType = extensions.getOpenXrWorldReferenceSpaceType();
    }

    static @NonNull SpatialSceneRuntime create(
            @NonNull Activity activity,
            @NonNull ScheduledExecutorService executor,
            @NonNull XrExtensions extensions,
            @NonNull EntityManager entityManager,
            @NonNull PerceptionLibrary perceptionLibrary) {
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
                        taskWindowLeashNode);
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
                new PerceptionLibrary());
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
}
