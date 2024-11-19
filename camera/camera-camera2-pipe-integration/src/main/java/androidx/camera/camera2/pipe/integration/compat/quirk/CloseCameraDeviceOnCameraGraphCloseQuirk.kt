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

package androidx.camera.camera2.pipe.integration.compat.quirk

import android.annotation.SuppressLint
import android.os.Build
import androidx.camera.core.impl.Quirk

/**
 * Quirk needed on devices where not closing the camera device can lead to undesirable behaviors,
 * such as switching to a new session without closing the camera device may cause native camera HAL
 * crashes, or the app getting "frozen" while CameraPipe awaits on a 1s cooldown to finally close
 * the camera device.
 *
 * QuirkSummary
 * - Bug Id: 282871038, 369300443
 * - Description: Instructs CameraPipe to close the camera device before creating a new capture
 *   session to avoid undesirable behaviors
 *
 * TODO(b/270421716): enable CameraXQuirksClassDetector lint check when kotlin is supported.
 */
@SuppressLint("CameraXQuirksClassDetector")
public class CloseCameraDeviceOnCameraGraphCloseQuirk : Quirk {
    public companion object {
        @JvmStatic
        public fun isEnabled(): Boolean {
            if (Build.HARDWARE == "samsungexynos7870") {
                // On Exynos7870 platforms, when their 3A pipeline times out, recreating a capture
                // session has a high chance of triggering use-after-free crashes. Closing the
                // camera device helps reduce the likelihood of this happening.
                return true
            } else if (
                Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.TIRAMISU &&
                    (Device.isOppoDevice() || Device.isOnePlusDevice() || Device.isRealmeDevice())
            ) {
                // On Oppo-family devices from Android 11 to Android 13, a process called
                // OplusHansManager actively "freezes" app processes, which means we cannot delay
                // closing the camera device for any amount of time.
                return true
            }
            return false
        }
    }
}
