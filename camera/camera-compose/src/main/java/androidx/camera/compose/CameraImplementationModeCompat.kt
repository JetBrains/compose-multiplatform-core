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

package androidx.camera.compose

import android.os.Build
import androidx.camera.core.CameraInfo
import androidx.camera.viewfinder.core.ImplementationMode

/**
 * Utility class for determining the appropriate [ImplementationMode] for [CameraXViewfinder].
 *
 * This class extends the compatibility logic from `viewfinder-core` by incorporating
 * camera-specific information, such as the hardware level.
 */
internal class CameraImplementationModeCompat {
    companion object {
        /**
         * Chooses a compatible [ImplementationMode] for the viewfinder based on the camera's
         * capabilities.
         *
         * In general, this method returns [ImplementationMode.EXTERNAL] as it typically offers
         * higher performance. However, it returns [ImplementationMode.EMBEDDED] under the following
         * conditions:
         * - If the camera device associated with the [cameraInfo] has a
         *   [LEGACY][android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY]
         *   hardware level.
         * - If the device is not LEGACY, it falls back to checking for other compatibility issues,
         *   including:
         *     - Being on API level 24 (Android N) or below.
         *     - Being on a device with known surface-related quirks.
         *
         * @param cameraInfo The [CameraInfo] used to determine the camera's hardware level.
         * @return The chosen [ImplementationMode].
         */
        fun chooseCompatibleMode(cameraInfo: CameraInfo): ImplementationMode {
            val isLegacyDevice =
                cameraInfo.getImplementationType() == CameraInfo.IMPLEMENTATION_TYPE_CAMERA2_LEGACY

            return if (isLegacyDevice) {
                ImplementationMode.EMBEDDED
            } else {
                // Fall back to the duplicated device-specific compatibility logic.
                return chooseDeviceCompatibleMode()
            }
        }

        // TODO(b/438015704): This logic is duplicated from viewfinder-core.
        //  Remove this and switch to the public API in viewfinder-core once it is
        //  available.
        private fun chooseDeviceCompatibleMode(): ImplementationMode {
            // TextureView is more compatible on older API levels.
            if (Build.VERSION.SDK_INT <= 24) {
                return ImplementationMode.EMBEDDED
            }

            // Some devices have quirks that require TextureView.
            if (
                SurfaceViewNotCroppedByParentQuirk.isCurrentDeviceAffected() ||
                    SurfaceViewStretchedQuirk.isCurrentDeviceAffected()
            ) {
                return ImplementationMode.EMBEDDED
            }

            // Default to SurfaceView for best performance on modern, non-quirk devices.
            return ImplementationMode.EXTERNAL
        }
    }
}

/**
 * A quirk where a scaled up SurfaceView is not cropped by the parent View.
 *
 * QuirkSummary Bug Id: 211370840 Description: On certain Xiaomi devices, when the scale type is
 * FILL_* and the preview is scaled up to be larger than its parent, the SurfaceView is not cropped
 * by its parent. As the result, the preview incorrectly covers the neighboring UI elements.
 * Device(s): XIAOMI M2101K7AG
 */
// TODO(b/438015704): This logic is duplicated from viewfinder-core.
//  Remove this and switch to the public API in viewfinder-core once it is
//  available.
internal object SurfaceViewNotCroppedByParentQuirk {
    private const val XIAOMI = "XIAOMI"
    private const val RED_MI_NOTE_10_MODEL = "M2101K7AG"

    @JvmStatic
    fun isCurrentDeviceAffected(): Boolean {
        return XIAOMI.equals(Build.MANUFACTURER, ignoreCase = true) &&
            RED_MI_NOTE_10_MODEL.equals(Build.MODEL, ignoreCase = true)
    }
}

/**
 * A quirk where SurfaceView is stretched.
 *
 * QuirkSummary Bug Id: 129403806 Description: On certain Samsung devices, transform APIs (e.g.
 * View#setScaleX) do not work as intended. Device(s): Samsung Fold2 F2Q, Samsung Fold3 Q2Q, Oppo
 * Find N OP4E75L1, Lenovo P12 Pro
 */
// TODO(b/438015704): This logic is duplicated from viewfinder-core.
//  Remove this and switch to the public API in viewfinder-core once it is
//  available.
internal object SurfaceViewStretchedQuirk {
    // Samsung Galaxy Z Fold2 b/129403806
    private const val SAMSUNG = "SAMSUNG"
    private const val GALAXY_Z_FOLD_2 = "F2Q"
    private const val GALAXY_Z_FOLD_3 = "Q2Q"
    private const val OPPO = "OPPO"
    private const val OPPO_FIND_N = "OP4E75L1"
    private const val LENOVO = "LENOVO"
    private const val LENOVO_TAB_P12_PRO = "Q706F"

    @JvmStatic
    fun isCurrentDeviceAffected(): Boolean {
        // The surface view issue is fixed in Android T.
        return Build.VERSION.SDK_INT < 33 &&
            (isSamsungFold2OrFold3 || isOppoFoldable || isLenovoTablet)
    }

    private val isSamsungFold2OrFold3: Boolean
        get() =
            SAMSUNG.equals(Build.MANUFACTURER, ignoreCase = true) &&
                (GALAXY_Z_FOLD_2.equals(Build.DEVICE, ignoreCase = true) ||
                    GALAXY_Z_FOLD_3.equals(Build.DEVICE, ignoreCase = true))

    private val isOppoFoldable: Boolean
        get() =
            OPPO.equals(Build.MANUFACTURER, ignoreCase = true) &&
                OPPO_FIND_N.equals(Build.DEVICE, ignoreCase = true)

    private val isLenovoTablet: Boolean
        get() =
            LENOVO.equals(Build.MANUFACTURER, ignoreCase = true) &&
                LENOVO_TAB_P12_PRO.equals(Build.DEVICE, ignoreCase = true)
}
