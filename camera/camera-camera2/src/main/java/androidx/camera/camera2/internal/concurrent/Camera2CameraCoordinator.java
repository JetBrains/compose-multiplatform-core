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

package androidx.camera.camera2.internal.concurrent;

import static androidx.camera.camera2.internal.CameraIdUtil.isBackwardCompatible;

import android.hardware.camera2.CameraCharacteristics;

import androidx.annotation.GuardedBy;
import androidx.annotation.OptIn;
import androidx.camera.camera2.internal.compat.CameraAccessExceptionCompat;
import androidx.camera.camera2.internal.compat.CameraCharacteristicsCompat;
import androidx.camera.camera2.internal.compat.CameraManagerCompat;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.InitializationException;
import androidx.camera.core.Logger;
import androidx.camera.core.concurrent.CameraCoordinator;
import androidx.camera.core.impl.CameraUpdateException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation for {@link CameraCoordinator}.
 */
public class Camera2CameraCoordinator implements CameraCoordinator {

    private static final String TAG = "Camera2CameraCoordinator";

    private final Object mLock = new Object();
    private final @NonNull CameraManagerCompat mCameraManager;
    private final @NonNull List<ConcurrentCameraModeListener> mConcurrentCameraModeListeners;

    @GuardedBy("mLock")
    private @NonNull Map<String, List<String>> mConcurrentCameraIdMap = new HashMap<>();
    @GuardedBy("mLock")
    private @NonNull Set<Set<String>> mConcurrentCameraIds = new HashSet<>();
    @GuardedBy("mLock")
    private @NonNull List<CameraInfo> mActiveConcurrentCameraInfos = new ArrayList<>();
    @GuardedBy("mLock")
    @CameraOperatingMode
    private int mCameraOperatingMode = CAMERA_OPERATING_MODE_UNSPECIFIED;

    public Camera2CameraCoordinator(@NonNull CameraManagerCompat cameraManager) {
        mCameraManager = cameraManager;
        mConcurrentCameraModeListeners = new ArrayList<>();
        try {
            // Populate initial state
            List<String> cameraIds = Arrays.asList(cameraManager.getCameraIdList());
            onCamerasUpdated(cameraIds);
        } catch (CameraAccessExceptionCompat | CameraUpdateException e) {
            Logger.e(TAG, "Failed to get concurrent camera ids", e);
        }
    }

    @Override
    public void onCamerasUpdated(@NonNull List<String> cameraIds) throws CameraUpdateException {
        // --- Stage 1: Pre-computation (outside the lock) ---
        final Map<String, List<String>> tempConcurrentCameraIdMap = new HashMap<>();
        final Set<Set<String>> tempConcurrentCameraIds = new HashSet<>();

        try {
            Set<Set<String>> allConcurrentSets = mCameraManager.getConcurrentCameraIds();
            for (Set<String> concurrentSet : allConcurrentSets) {
                if (!cameraIds.containsAll(concurrentSet)) {
                    continue;
                }

                List<String> cameraIdList = new ArrayList<>(concurrentSet);
                if (cameraIdList.size() >= 2) {
                    String id1 = cameraIdList.get(0);
                    String id2 = cameraIdList.get(1);
                    try {
                        if (isBackwardCompatible(mCameraManager, id1) && isBackwardCompatible(
                                mCameraManager, id2)) {
                            tempConcurrentCameraIds.add(new HashSet<>(Arrays.asList(id1, id2)));

                            if (!tempConcurrentCameraIdMap.containsKey(id1)) {
                                tempConcurrentCameraIdMap.put(id1, new ArrayList<>());
                            }
                            tempConcurrentCameraIdMap.get(id1).add(id2);

                            if (!tempConcurrentCameraIdMap.containsKey(id2)) {
                                tempConcurrentCameraIdMap.put(id2, new ArrayList<>());
                            }
                            tempConcurrentCameraIdMap.get(id2).add(id1);
                        }
                    } catch (InitializationException e) {
                        Logger.d(TAG, "Concurrent camera id pair: (" + id1 + ", "
                                + id1 + ") is not backward compatible");
                    }
                }
            }
        } catch (CameraAccessExceptionCompat e) {
            throw new CameraUpdateException("Failed to retrieve concurrent camera id info.", e);
        }

        // --- Stage 2: Commit (inside a brief lock) ---
        synchronized (mLock) {
            mConcurrentCameraIdMap = tempConcurrentCameraIdMap;
            mConcurrentCameraIds = tempConcurrentCameraIds;
            Logger.d(TAG, "Updated concurrent camera map: " + mConcurrentCameraIdMap);
        }
    }

    @Override
    public @NonNull List<List<CameraSelector>> getConcurrentCameraSelectors() {
        List<List<CameraSelector>> concurrentCameraSelectorLists = new ArrayList<>();
        synchronized (mLock) {
            for (Set<String> concurrentCameraIdList : mConcurrentCameraIds) {
                List<CameraSelector> cameraSelectors = new ArrayList<>();
                for (String concurrentCameraId : concurrentCameraIdList) {
                    cameraSelectors.add(
                            createCameraSelectorById(mCameraManager, concurrentCameraId));
                }
                concurrentCameraSelectorLists.add(cameraSelectors);
            }
        }
        return concurrentCameraSelectorLists;
    }

    @Override
    public @NonNull List<CameraInfo> getActiveConcurrentCameraInfos() {
        synchronized (mLock) {
            return new ArrayList<>(mActiveConcurrentCameraInfos);
        }
    }

    @Override
    public void setActiveConcurrentCameraInfos(@NonNull List<CameraInfo> cameraInfos) {
        synchronized (mLock) {
            mActiveConcurrentCameraInfos = new ArrayList<>(cameraInfos);
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    @Override
    public @Nullable String getPairedConcurrentCameraId(@NonNull String cameraId) {
        synchronized (mLock) {
            if (!mConcurrentCameraIdMap.containsKey(cameraId)) {
                return null;
            }
            List<String> pairedIds = mConcurrentCameraIdMap.get(cameraId);
            if (pairedIds == null) {
                return null;
            }
            for (String pairedCameraId : pairedIds) {
                for (CameraInfo cameraInfo : mActiveConcurrentCameraInfos) {
                    if (pairedCameraId.equals(Camera2CameraInfo.from(cameraInfo).getCameraId())) {
                        return pairedCameraId;
                    }
                }
            }
        }
        return null;
    }

    @CameraOperatingMode
    @Override
    public int getCameraOperatingMode() {
        synchronized (mLock) {
            return mCameraOperatingMode;
        }
    }

    @Override
    public void setCameraOperatingMode(@CameraOperatingMode int cameraOperatingMode) {
        List<ConcurrentCameraModeListener> listenersSnapshot = null;
        int previousMode;

        synchronized (mLock) {
            if (cameraOperatingMode == mCameraOperatingMode) {
                return;
            }

            // A mode change occurred. Store the previous mode for the notification.
            previousMode = mCameraOperatingMode;
            mCameraOperatingMode = cameraOperatingMode;

            // Take a snapshot of the listeners inside the lock to iterate over safely.
            listenersSnapshot = new ArrayList<>(mConcurrentCameraModeListeners);

            // Clear the cached active camera infos if concurrent mode is turned off.
            if (previousMode == CAMERA_OPERATING_MODE_CONCURRENT
                    && cameraOperatingMode != CAMERA_OPERATING_MODE_CONCURRENT) {
                mActiveConcurrentCameraInfos.clear();
            }
        }

        notifyCameraModeListener(listenersSnapshot, previousMode, cameraOperatingMode);
    }

    private void notifyCameraModeListener(@NonNull List<ConcurrentCameraModeListener> listeners,
            int prevMode, int currMode) {
        for (ConcurrentCameraModeListener listener : listeners) {
            listener.onCameraOperatingModeUpdated(prevMode, currMode);
        }
    }

    @Override
    public void addListener(@NonNull ConcurrentCameraModeListener listener) {
        synchronized (mLock) {
            mConcurrentCameraModeListeners.add(listener);
        }
    }

    @Override
    public void removeListener(@NonNull ConcurrentCameraModeListener listener) {
        synchronized (mLock) {
            mConcurrentCameraModeListeners.remove(listener);
        }
    }

    @Override
    public void shutdown() {
        synchronized (mLock) {
            mConcurrentCameraModeListeners.clear();
            mConcurrentCameraIdMap.clear();
            mActiveConcurrentCameraInfos.clear();
            mConcurrentCameraIds.clear();
            mCameraOperatingMode = CAMERA_OPERATING_MODE_UNSPECIFIED;
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private static CameraSelector createCameraSelectorById(
            @NonNull CameraManagerCompat cameraManager,
            @NonNull String cameraId) {
        CameraSelector.Builder builder =
                new CameraSelector.Builder().addCameraFilter(cameraInfos -> {
                    for (CameraInfo cameraInfo : cameraInfos) {
                        if (cameraId.equals(Camera2CameraInfo.from(cameraInfo).getCameraId())) {
                            return Collections.singletonList(cameraInfo);
                        }
                    }

                    throw new IllegalArgumentException("No camera can be find for id: " + cameraId);
                });

        try {
            CameraCharacteristicsCompat cameraCharacteristics =
                    cameraManager.getCameraCharacteristicsCompat(cameraId);
            Integer cameraLensFacing = cameraCharacteristics.get(
                    CameraCharacteristics.LENS_FACING);
            if (cameraLensFacing != null) {
                builder.requireLensFacing(cameraLensFacing);
            }
        } catch (CameraAccessExceptionCompat e) {
            // This should not happen, as the camera ID comes from a list of available cameras.
            // Throw a runtime exception to signal a critical state inconsistency.
            throw new RuntimeException("Unable to get camera characteristics for " + cameraId, e);
        }

        return builder.build();
    }
}
