/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.camera2.internal;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Display;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import androidx.camera.camera2.internal.compat.workaround.DisplaySizeCorrector;
import androidx.camera.camera2.internal.compat.workaround.MaxPreviewSize;
import androidx.camera.core.internal.utils.SizeUtil;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A singleton class to retrieve display related information.
 *
 * <p>This class uses a caching strategy to reduce calls to the system's {@link DisplayManager}.
 *
 * <p>The cached information is lazy-loaded. It is only fetched from the {@link DisplayManager}
 * when first needed. A {@link DisplayManager.DisplayListener} invalidates the cache (by setting
 * it to null) whenever the display configuration changes. The next call to
 * {@link #getDisplays()} or {@link #getPreviewSize()} will then fetch the fresh data.
 */
public class DisplayInfoManager {
    private static final Size MAX_PREVIEW_SIZE = new Size(1920, 1080);
    /**
     * This is the smallest size from a device which had issue reported to CameraX.
     */
    private static final Size ABNORMAL_DISPLAY_SIZE_THRESHOLD = new Size(320, 240);
    /**
     * The fallback display size for the case that the retrieved display size is abnormally small
     * and no correct display size can be retrieved from DisplaySizeCorrector.
     */
    private static final Size FALLBACK_DISPLAY_SIZE = new Size(640, 480);

    /**
     * A lock to ensure thread-safe access to the singleton instance and the cached display
     * information. Using a static lock is a robust way to protect the singleton's shared
     * resources, even if the singleton pattern is accidentally violated.
     */
    private static final Object INSTANCE_LOCK = new Object();

    @GuardedBy("INSTANCE_LOCK")
    private static volatile DisplayInfoManager sInstance;

    private final @NonNull DisplayManager mDisplayManager;

    /**
     * A listener to detect display changes.
     *
     * <p>This listener invalidates the cached display information (by setting it to null)
     * whenever the display configuration changes. The next call to {@link #getDisplays()} or
     * {@link #getPreviewSize()} will then fetch the fresh data.
     */
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    synchronized (INSTANCE_LOCK) {
                        mDisplays = null;
                        mPreviewSize = null;
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    synchronized (INSTANCE_LOCK) {
                        mDisplays = null;
                        mPreviewSize = null;
                    }
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (INSTANCE_LOCK) {
                        mDisplays = null;
                        mPreviewSize = null;
                    }
                }
            };

    /**
     * A cache for the array of {@link Display} objects.
     * It is invalidated (set to null) by the {@link #mDisplayListener} and re-populated on the
     * next call to {@link #getDisplays()}.
     */
    @GuardedBy("INSTANCE_LOCK")
    private volatile Display[] mDisplays = null;

    @GuardedBy("INSTANCE_LOCK")
    private volatile Size mPreviewSize = null;
    private final MaxPreviewSize mMaxPreviewSize = new MaxPreviewSize();
    private final DisplaySizeCorrector mDisplaySizeCorrector = new DisplaySizeCorrector();

    private DisplayInfoManager(@NonNull Context context) {
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    /**
     * Gets the singleton instance of DisplayInfoManager.
     */
    public static @NonNull DisplayInfoManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (INSTANCE_LOCK) {
                if (sInstance == null) {
                    sInstance = new DisplayInfoManager(context);
                    sInstance.mDisplayManager.registerDisplayListener(sInstance.mDisplayListener,
                            new Handler(Looper.getMainLooper()));
                }
            }
        }
        return sInstance;
    }

    /**
     * Releases the instance so that tests can create new instances.
     */
    @VisibleForTesting
    static void releaseInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance != null) {
                sInstance.mDisplayManager.unregisterDisplayListener(sInstance.mDisplayListener);
                sInstance = null;
            }
        }
    }

    /**
     * Refreshes the preview size.
     */
    void refreshPreviewSize() {
        synchronized (INSTANCE_LOCK) {
            mPreviewSize = calculatePreviewSize();
        }
    }

    /**
     * Retrieves the display which has the max size among all displays.
     *
     * @param skipStateOffDisplay true to skip the displays with off state
     */
    public @NonNull Display getMaxSizeDisplay(boolean skipStateOffDisplay) {
        Display[] displays = getDisplays();

        if (displays.length == 1) {
            return displays[0];
        }

        // Try to find the max size display according to the skipStateOffDisplay parameter
        Display maxDisplay = getMaxSizeDisplayInternal(displays, skipStateOffDisplay);

        // Try to find the max size display from all displays again if no display can be found
        // when only checking the non-state-off displays.
        if (maxDisplay == null && skipStateOffDisplay) {
            maxDisplay = getMaxSizeDisplayInternal(displays, false);
        }

        // If still no display found, throw IllegalArgumentException.
        if (maxDisplay == null) {
            throw new IllegalArgumentException("No display can be found from the input display "
                    + "manager!");
        }

        return maxDisplay;
    }

    /**
     * Gets the array of displays, using a cache to avoid unnecessary calls to the system.
     * The cache is lazily populated.
     */
    private Display[] getDisplays() {
        synchronized (INSTANCE_LOCK) {
            if (mDisplays == null) {
                mDisplays = mDisplayManager.getDisplays();
            }
            return mDisplays;
        }
    }

    @SuppressWarnings("deprecation") /* getRealSize */
    private @Nullable Display getMaxSizeDisplayInternal(Display @NonNull [] displays,
            boolean skipStateOffDisplay) {
        Display maxDisplay = null;
        int maxDisplaySize = -1;

        for (Display display : displays) {
            // Skips displays with state off if the input skipStateOffDisplay parameter is true
            if (skipStateOffDisplay && display.getState() == Display.STATE_OFF) {
                continue;
            }

            Point displaySize = new Point();
            display.getRealSize(displaySize);
            if (displaySize.x * displaySize.y > maxDisplaySize) {
                maxDisplaySize = displaySize.x * displaySize.y;
                maxDisplay = display;
            }
        }

        return maxDisplay;
    }

    /**
     * PREVIEW refers to the best size match to the device's screen resolution, or to 1080p
     * (1920x1080), whichever is smaller.
     */
    @NonNull Size getPreviewSize() {
        synchronized (INSTANCE_LOCK) {
            // Use cached value to speed up since this would be called multiple times.
            if (mPreviewSize != null) {
                return mPreviewSize;
            }

            mPreviewSize = calculatePreviewSize();
            return mPreviewSize;
        }
    }

    private Size calculatePreviewSize() {
        Size displayViewSize = getCorrectedDisplaySize();
        if (displayViewSize.getWidth() * displayViewSize.getHeight()
                > MAX_PREVIEW_SIZE.getWidth() * MAX_PREVIEW_SIZE.getHeight()) {
            displayViewSize = MAX_PREVIEW_SIZE;
        }
        return mMaxPreviewSize.getMaxPreviewResolution(displayViewSize);
    }

    @SuppressWarnings("deprecation") /* getRealSize */
    private @NonNull Size getCorrectedDisplaySize() {
        Point displaySize = new Point();
        // The PREVIEW size should be determined by the max display size among all displays on
        // the device no matter its state is on or off. The PREVIEW size is used for the
        // guaranteed configurations tables which are related to the camera's capability. The
        // PREVIEW size should not be affected by the display state.
        Display display = getMaxSizeDisplay(false);
        display.getRealSize(displaySize);
        Size displayViewSize = new Size(displaySize.x, displaySize.y);

        // Checks whether the display size is abnormally small.
        if (SizeUtil.isSmallerByArea(displayViewSize, ABNORMAL_DISPLAY_SIZE_THRESHOLD)) {
            // Gets the display size from DisplaySizeCorrector if the display size retrieved from
            // DisplayManager is abnormally small.
            displayViewSize = mDisplaySizeCorrector.getDisplaySize();

            // Falls back the display size to 640x480 if DisplaySizeCorrector doesn't contain the
            // device's display size info.
            if (displayViewSize == null) {
                displayViewSize = FALLBACK_DISPLAY_SIZE;
            }
        }

        // Flips the size to landscape orientation
        if (displayViewSize.getHeight() > displayViewSize.getWidth()) {
            displayViewSize = new Size(/* width= */ displayViewSize.getHeight(), /* height=
            */ displayViewSize.getWidth());
        }

        return displayViewSize;
    }
}
