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

package androidx.camera.core.impl.utils;

import android.graphics.ImageFormat;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SurfaceUtil {
    private static final String TAG = "SurfaceUtil";
    public static final String JNI_LIB_NAME = "surface_util_jni";

    static {
        System.loadLibrary(JNI_LIB_NAME);
    }

    private SurfaceUtil() {
    }

    /**
     * A class to store surface related information.
     */
    public static class SurfaceInfo {
        /**
         * The surface format.
         */
        public int format = ImageFormat.UNKNOWN;
        /**
         * The surface width;
         */
        public int width = 0;
        /**
         * The surface height.
         */
        public int height = 0;
    }

    /**
     * Returns the surface pixel format.
     */
    @NonNull
    public static SurfaceInfo getSurfaceInfo(@NonNull Surface surface) {
        int[] surfaceInfoArray = nativeGetSurfaceInfo(surface);
        SurfaceInfo surfaceInfo = new SurfaceInfo();
        surfaceInfo.format = surfaceInfoArray[0];
        surfaceInfo.width = surfaceInfoArray[1];
        surfaceInfo.height = surfaceInfoArray[2];
        return surfaceInfo;
    }

    private static native int[] nativeGetSurfaceInfo(@Nullable Surface surface);
}
