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

package androidx.xr.scenecore.spatial.rendering;

import android.app.Activity;

import androidx.annotation.VisibleForTesting;
import androidx.xr.runtime.internal.RenderingRuntime;
import androidx.xr.runtime.internal.SceneRuntime;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;

import com.android.extensions.xr.XrExtensions;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.ar.imp.apibindings.ImpressApi;
import com.google.ar.imp.apibindings.ImpressApiImpl;
import com.google.ar.imp.view.splitengine.ImpSplitEngine;
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of [RenderingRuntime] for devices that support the [Feature.SPATIAL] system
 * feature.
 */
class SpatialRenderingRuntime implements RenderingRuntime {
    private static final String SPLIT_ENGINE_LIBRARY_NAME = "impress_api_jni";

    private final @NonNull SceneRuntime mSceneRuntime;
    private @Nullable Activity mActivity;

    private final XrExtensions mExtensions;
    private final ImpressApi mImpressApi;
    private SplitEngineSubspaceManager mSplitEngineSubspaceManager;
    private ImpSplitEngineRenderer mSplitEngineRenderer;
    private boolean mIsDisposed = false;
    private boolean mFrameLoopStarted;

    private SpatialRenderingRuntime(
            @NonNull SceneRuntime sceneRuntime,
            @NonNull Activity activity,
            @NonNull XrExtensions extensions,
            @NonNull ImpressApi impressApi,
            @NonNull SplitEngineSubspaceManager subspaceManager,
            @NonNull ImpSplitEngineRenderer renderer) {
        mSceneRuntime = sceneRuntime;
        mActivity = activity;
        mExtensions = extensions;
        mImpressApi = impressApi;
        mSplitEngineRenderer = renderer;
        mSplitEngineSubspaceManager = subspaceManager;

        startRenderer();
    }

    /** Create a new @c RenderingRuntime. */
    @VisibleForTesting
    static @NonNull SpatialRenderingRuntime create(
            @NonNull SceneRuntime sceneRuntime,
            @NonNull Activity activity,
            @Nullable ImpressApi impressApi,
            @Nullable SplitEngineSubspaceManager splitEngineSubspaceManager,
            @Nullable ImpSplitEngineRenderer splitEngineRenderer) {
        XrExtensions extensions = XrExtensionsProvider.getXrExtensions();
        if (extensions == null) throw new IllegalStateException("XrExtensions is null");
        if (impressApi == null) impressApi = new ImpressApiImpl();
        if (splitEngineRenderer == null) {
            ImpSplitEngine.SplitEngineSetupParams impApiSetupParams =
                    new ImpSplitEngine.SplitEngineSetupParams();
            impApiSetupParams.jniLibraryName = SPLIT_ENGINE_LIBRARY_NAME;
            splitEngineRenderer =
                    ImpSplitEngineRenderer.create(activity, impApiSetupParams, extensions);
        }
        if (splitEngineSubspaceManager == null) {
            splitEngineSubspaceManager =
                    new SplitEngineSubspaceManager(
                            splitEngineRenderer, extensions, null, null, SPLIT_ENGINE_LIBRARY_NAME);
        }
        impressApi.setup(splitEngineRenderer.getView());
        return new SpatialRenderingRuntime(
                sceneRuntime,
                activity,
                extensions,
                impressApi,
                splitEngineSubspaceManager,
                splitEngineRenderer);
    }

    /**
     * Create a new @c SpatialRenderingRuntime.
     *
     * @param sceneRuntime The SceneRuntime provide basic function for creating entities.
     * @param activity The Activity to use.
     * @return A new SpatialRenderingRuntime.
     */
    static @NonNull SpatialRenderingRuntime create(
            @NonNull SceneRuntime sceneRuntime, @NonNull Activity activity) {
        return SpatialRenderingRuntime.create(sceneRuntime, activity, null, null, null);
    }

    @Override
    public void startRenderer() {
        if (mSplitEngineRenderer == null || mFrameLoopStarted) {
            return;
        }
        mFrameLoopStarted = true;
        mSplitEngineRenderer.startFrameLoop();
    }

    @Override
    public void stopRenderer() {
        if (mSplitEngineRenderer == null || !mFrameLoopStarted) {
            return;
        }
        mFrameLoopStarted = false;
        mSplitEngineRenderer.stopFrameLoop();
    }

    @Override
    public void dispose() {
        if (mIsDisposed) {
            return;
        }
        stopRenderer();
        mActivity = null;
        if (mSplitEngineRenderer != null && mSplitEngineSubspaceManager != null) {
            mSplitEngineSubspaceManager.destroy();
            mSplitEngineRenderer.destroy();
            mSplitEngineSubspaceManager = null;
            mSplitEngineRenderer = null;
        }
        mIsDisposed = true;
    }

    @VisibleForTesting
    boolean isFrameLoopStarted() {
        return mFrameLoopStarted;
    }
}
