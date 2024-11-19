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

package androidx.camera.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.UseCase;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collection;

/**
 * A {@link CameraInternal} implementation that will contain {@link AdapterCameraInfo} and
 * {@link AdapterCameraControl} under the implementation. When retrieving
 * {@link androidx.camera.core.CameraInfo} or {@link androidx.camera.core.CameraControl}, the
 * associated {@link AdapterCameraInfo} or {@link AdapterCameraControl} will be returned.
 *
 * <p>For all other {@link CameraInfo} an {@link CameraControl} unrelated methods, the
 * AdapterCameraInternal forwards the invocations to the associated {@link CameraInternal}
 * instance's corresponding implementation.
 */
public class AdapterCameraInternal implements CameraInternal {
    @NonNull
    private final CameraInternal mCameraInternal;
    private final AdapterCameraInfo mAdapterCameraInfo;
    private final AdapterCameraControl mAdapterCameraControl;

    public AdapterCameraInternal(@NonNull CameraInternal cameraInternal,
            @NonNull AdapterCameraInfo adapterCameraInfo) {
        mCameraInternal = cameraInternal;
        mAdapterCameraInfo = adapterCameraInfo;
        CameraConfig cameraConfig = mAdapterCameraInfo.getCameraConfig();
        mAdapterCameraControl = new AdapterCameraControl(
                mCameraInternal.getCameraControlInternal(), cameraConfig.getSessionProcessor(null));
    }

    /**
     * Returns the associated {@link CameraInternal} implementation instance.
     */
    @NonNull
    public CameraInternal getImplementation() {
        return mCameraInternal;
    }

    @Override
    public void open() {
        mCameraInternal.open();
    }

    @Override
    public void close() {
        mCameraInternal.close();
    }

    @Override
    public void setActiveResumingMode(boolean enabled) {
        mCameraInternal.setActiveResumingMode(enabled);
    }

    @Override
    public boolean isFrontFacing() {
        return mCameraInternal.isFrontFacing();
    }

    @NonNull
    @Override
    public ListenableFuture<Void> release() {
        return mCameraInternal.release();
    }

    @NonNull
    @Override
    public Observable<State> getCameraState() {
        return mCameraInternal.getCameraState();
    }

    @Override
    public void attachUseCases(@NonNull Collection<UseCase> useCases) {
        mCameraInternal.attachUseCases(useCases);
    }

    @Override
    public void detachUseCases(@NonNull Collection<UseCase> useCases) {
        mCameraInternal.detachUseCases(useCases);
    }

    @NonNull
    @Override
    public CameraControlInternal getCameraControlInternal() {
        return mAdapterCameraControl;
    }

    @NonNull
    @Override
    public CameraInfoInternal getCameraInfoInternal() {
        return mAdapterCameraInfo;
    }

    @NonNull
    @Override
    public CameraControl getCameraControl() {
        return mAdapterCameraControl;
    }

    @NonNull
    @Override
    public CameraInfo getCameraInfo() {
        return mAdapterCameraInfo;
    }

    @Override
    public boolean getHasTransform() {
        return mCameraInternal.getHasTransform();
    }

    @Override
    public void setPrimary(boolean isPrimary) {
        mCameraInternal.setPrimary(isPrimary);
    }

    @NonNull
    @Override
    public CameraConfig getExtendedConfig() {
        return mCameraInternal.getExtendedConfig();
    }

    @Override
    public boolean isUseCasesCombinationSupported(@NonNull UseCase... useCases) {
        return mCameraInternal.isUseCasesCombinationSupported(useCases);
    }

    @Override
    public boolean isUseCasesCombinationSupportedByFramework(@NonNull UseCase... useCases) {
        return mCameraInternal.isUseCasesCombinationSupportedByFramework(useCases);
    }

    @Override
    public boolean isUseCasesCombinationSupported(boolean withStreamSharing,
            @NonNull UseCase... useCases) {
        return mCameraInternal.isUseCasesCombinationSupported(withStreamSharing, useCases);
    }

    @Override
    public void setExtendedConfig(@Nullable CameraConfig cameraConfig) {
        mCameraInternal.setExtendedConfig(cameraConfig);
    }

    @Override
    public void onUseCaseActive(@NonNull UseCase useCase) {
        mCameraInternal.onUseCaseActive(useCase);
    }

    @Override
    public void onUseCaseInactive(@NonNull UseCase useCase) {
        mCameraInternal.onUseCaseInactive(useCase);
    }

    @Override
    public void onUseCaseUpdated(@NonNull UseCase useCase) {
        mCameraInternal.onUseCaseUpdated(useCase);
    }

    @Override
    public void onUseCaseReset(@NonNull UseCase useCase) {
        mCameraInternal.onUseCaseReset(useCase);
    }
}
