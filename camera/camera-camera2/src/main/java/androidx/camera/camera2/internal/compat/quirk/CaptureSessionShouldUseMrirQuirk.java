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

package androidx.camera.camera2.internal.compat.quirk;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.params.OutputConfiguration;
import android.os.Build;

import androidx.camera.core.impl.Quirk;

/**
 * A quirk to denote the {@link CameraCaptureSession} should use
 * {@link OutputConfiguration#createInstancesForMultiResolutionOutput} to create
 * {@link OutputConfiguration}s with multi-resolution output enabled.
 *
 * <p>QuirkSummary
 *     Bug Id: 376185185
 *     Description: NIGHT mode extensions support on Google Pixel Android 15 or above devices need
 *                  to create the {@link OutputConfiguration}s with multi-resolution output enabled.
 *                  Otherwise, it will fail to capture still images until the camera focused on a
 *                  nearby object.
 *     Device(s): Google Pixel Android 15 or above devices.
 */
public class CaptureSessionShouldUseMrirQuirk implements Quirk {

    static boolean load() {
        return "google".equalsIgnoreCase(Build.BRAND) && Build.VERSION.SDK_INT >= 35;
    }
}
