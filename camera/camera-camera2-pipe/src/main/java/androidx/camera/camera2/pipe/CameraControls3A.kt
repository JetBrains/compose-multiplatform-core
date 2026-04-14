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

package androidx.camera.camera2.pipe

import android.hardware.camera2.params.MeteringRectangle
import androidx.annotation.RestrictTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/**
 * [CameraControls3A] represents the methods to control and change the camera's 3A state via
 * [CameraGraph].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CameraControls3A {
    /**
     * Updates the camera's 3A (Auto-Exposure, Auto-Focus, and Auto-White Balance) settings.
     *
     * This method updates the camera device's repeating request with the appropriate parameters for
     * the passed in arguments. Note that this function allows for partial updates. Any parameter
     * left as `null` will result in the corresponding 3A setting remaining unchanged.
     *
     * @param aeMode the desired Auto-Exposure mode. Corresponds to
     *   [CaptureRequest.CONTROL_AE_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_MODE).
     *   If `null`, the current AE mode is not modified.
     * @param afMode the desired Auto-Focus mode. Corresponds to
     *   [CaptureRequest.CONTROL_AF_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AF_MODE).
     *   If `null`, the current AF mode is not modified.
     * @param awbMode the desired Auto-White Balance mode. Corresponds to
     *   [CaptureRequest.CONTROL_AWB_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_MODE).
     *   If `null`, the current AWB mode is not modified.
     * @param controlMode the desired overall mode of 3A. Corresponds to
     *   [CaptureRequest.CONTROL_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_MODE.
     *   If `null`, the current flash mode is not modified.
     * @param flashMode the desired flash mode. Corresponds to
     *   [CaptureRequest.FLASH_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#FLASH_MODE).
     *   If `null`, the current flash mode is not modified.
     * @param aeRegions a list of MeteringRectangles for Auto-Exposure metering. Corresponds to
     *   [CaptureRequest.CONTROL_AE_REGIONS](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_REGIONS).
     *   If `null`, the AE metering regions are not updated.
     * @param afRegions a list of MeteringRectangles for Auto-Focus metering. Corresponds to
     *   [CaptureRequest.CONTROL_AF_REGIONS](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AF_REGIONS).
     *   If `null`, the AF metering regions are not updated.
     * @param awbRegions a list of MeteringRectangle for Auto-White Balance metering. Corresponds to
     *   [CaptureRequest.CONTROL_AWB_REGIONS](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_REGIONS).
     *   If `null`, the AWB metering regions are not updated.
     * @return A [Deferred] of [Result3A] value which will contain the frame number at which the
     *   capture result has all the needed applied parameters. It may be canceled with a
     *   [CancellationException] if a newer request is submitted before completion.
     */
    public fun update3A(
        aeMode: AeMode? = null,
        afMode: AfMode? = null,
        awbMode: AwbMode? = null,
        controlMode: ControlMode? = null,
        flashMode: FlashMode? = null,
        aeRegions: List<MeteringRectangle>? = null,
        afRegions: List<MeteringRectangle>? = null,
        awbRegions: List<MeteringRectangle>? = null,
    ): Deferred<Result3A>

    /**
     * Applies the given 3A parameters to the camera device but for only one frame.
     *
     * @return the FrameNumber for which these parameters were applied.
     */
    public fun submit3A(
        aeMode: AeMode? = null,
        afMode: AfMode? = null,
        awbMode: AwbMode? = null,
        aeRegions: List<MeteringRectangle>? = null,
        afRegions: List<MeteringRectangle>? = null,
        awbRegions: List<MeteringRectangle>? = null,
    ): Deferred<Result3A>

    /**
     * Turns the torch to ON.
     *
     * This method has a side effect on the currently set AE mode. Ref:
     * https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#FLASH_MODE To
     * use the flash control, AE mode must be set to ON or OFF. So if the AE mode is already not
     * either ON or OFF, we will need to update the AE mode to one of those states, here we will
     * choose ON. It is the responsibility of the application layer above CameraPipe to restore the
     * AE mode after the torch control has been used. The [setTorchOff] or [update3A] method can be
     * used to restore the AE state to a previous value.
     *
     * @return the FrameNumber at which the turn was fully turned on if switch was ON, or the
     *   FrameNumber at which it was completely turned off when the switch was OFF.
     */
    public fun setTorchOn(): Deferred<Result3A>

    /**
     * Turns the torch to OFF.
     *
     * @param aeMode The [AeMode] to set while disabling the torch value. If null which is the
     *   default value, the current AE mode is used.
     * @return the FrameNumber at which the turn was fully turned on if switch was ON, or the
     *   FrameNumber at which it was completely turned off when the switch was OFF.
     */
    public fun setTorchOff(aeMode: AeMode? = null): Deferred<Result3A>
}
