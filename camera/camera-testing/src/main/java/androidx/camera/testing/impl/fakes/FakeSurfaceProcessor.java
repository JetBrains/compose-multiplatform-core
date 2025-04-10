/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.testing.impl.fakes;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import androidx.camera.core.SurfaceOutput;
import androidx.camera.core.SurfaceProcessor;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.impl.DeferrableSurface;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fake {@link SurfaceProcessor} used in tests.
 */
public class FakeSurfaceProcessor implements SurfaceProcessor {

    final SurfaceTexture mSurfaceTexture;
    final Surface mInputSurface;
    private final Executor mExecutor;
    private final boolean mAutoCloseSurfaceOutput;

    private @Nullable SurfaceRequest mSurfaceRequest;
    private final @NonNull Map<Integer, SurfaceOutput> mSurfaceOutputs = new HashMap<>();
    boolean mIsInputSurfaceReleased;
    private final Map<Integer, Boolean> mIsOutputSurfaceRequestedToClose = new HashMap<>();

    private final Map<Integer, Surface> mOutputSurfaces = new HashMap<>();

    /**
     * Creates a {@link SurfaceProcessor} that closes the {@link SurfaceOutput} automatically.
     */
    public FakeSurfaceProcessor(@NonNull Executor executor) {
        this(executor, true);
    }

    /**
     * @param autoCloseSurfaceOutput if true, automatically close the {@link SurfaceOutput} once
     *                               the close request is received. Otherwise, the test needs to
     *                               get {@link #getSurfaceOutputs()} and call
     *                               {@link SurfaceOutput#close()} to avoid the "Completer GCed"
     *                               error in {@link DeferrableSurface}.
     */
    FakeSurfaceProcessor(@NonNull Executor executor, boolean autoCloseSurfaceOutput) {
        mSurfaceTexture = new SurfaceTexture(0);
        mInputSurface = new Surface(mSurfaceTexture);
        mExecutor = executor;
        mIsInputSurfaceReleased = false;
        mAutoCloseSurfaceOutput = autoCloseSurfaceOutput;
    }

    @Override
    public void onInputSurface(@NonNull SurfaceRequest request) {
        mSurfaceRequest = request;
        request.provideSurface(mInputSurface, mExecutor, result -> {
            mSurfaceTexture.release();
            mInputSurface.release();
            mIsInputSurfaceReleased = true;
        });
    }

    @Override
    public void onOutputSurface(@NonNull SurfaceOutput surfaceOutput) {
        mSurfaceOutputs.put(surfaceOutput.getTargets(), surfaceOutput);
        mOutputSurfaces.put(surfaceOutput.getTargets(), surfaceOutput.getSurface(mExecutor,
                output -> {
                    if (mAutoCloseSurfaceOutput) {
                        surfaceOutput.close();
                    }
                    mIsOutputSurfaceRequestedToClose.put(surfaceOutput.getTargets(), true);
                }
        ));
    }

    public @Nullable SurfaceRequest getSurfaceRequest() {
        return mSurfaceRequest;
    }

    public @NonNull Map<Integer, SurfaceOutput> getSurfaceOutputs() {
        return mSurfaceOutputs;
    }

    public @NonNull Surface getInputSurface() {
        return mInputSurface;
    }

    public @NonNull Map<Integer, Surface> getOutputSurfaces() {
        return mOutputSurfaces;
    }

    public boolean isInputSurfaceReleased() {
        return mIsInputSurfaceReleased;
    }

    public @NonNull Map<Integer, Boolean> isOutputSurfaceRequestedToClose() {
        return mIsOutputSurfaceRequestedToClose;
    }

    /**
     * Clear up the instance to avoid the "{@link DeferrableSurface} garbage collected" error.
     */
    public void cleanUp() {
        for (SurfaceOutput surfaceOutput : mSurfaceOutputs.values()) {
            surfaceOutput.close();
        }
        mSurfaceTexture.release();
        mInputSurface.release();
    }
}
