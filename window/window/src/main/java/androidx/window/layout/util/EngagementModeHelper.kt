/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.window.layout.util

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.view.InputDevice
import androidx.annotation.UiContext
import androidx.annotation.VisibleForTesting
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.pow

/** Helper class to determine the engagement mode of the device. */
internal interface EngagementModeHelper {
    /**
     * Returns `true` if the app layout can be optimized for
     * [androidx.window.layout.WindowLayoutInfo.EngagementMode.ENGAGEMENT_PRECISE_POINTER].
     */
    fun isPointerMode(uiContext: Context): Boolean

    /** Returns `true` if the device with the given [deviceId] is a physical keyboard device. */
    fun isPhysicalKeyboardDevice(deviceId: Int): Boolean

    /** Returns `true` if the device with the given [deviceId] is a mouse device and is enabled. */
    fun isMouseDeviceEnabled(deviceId: Int): Boolean

    /**
     * Returns `true` if the device has a display larger than or equal to
     * [EngagementModeHelperImpl.DIAGONAL_INCH_SQUARED_LARGE_LOWER_BOUND].
     */
    fun hasLargeEnoughDisplay(uiContext: Context): Boolean

    /** Returns the list of input device IDs. */
    fun getInputDeviceIds(): IntArray

    /**
     * Registers an [InputManager.InputDeviceListener] to be notified about changes in input
     * devices.
     */
    fun registerInputDeviceListener(
        listener: InputManager.InputDeviceListener,
        handler: Handler? = null,
    )

    /** Unregisters an [InputManager.InputDeviceListener]. */
    fun unregisterInputDeviceListener(listener: InputManager.InputDeviceListener)

    companion object {
        /** Returns an instance of [EngagementModeHelper]. */
        fun getInstance(context: Context): EngagementModeHelper {
            return EngagementModeHelperImpl(context)
        }
    }
}

/** Implementation of [EngagementModeHelper]. */
internal class EngagementModeHelperImpl(
    context: Context,
    private val inputHelper: InputHelper = InputHelperImpl(context),
    private val windowMetricsCalculator: WindowMetricsCalculator =
        WindowMetricsCalculator.getOrCreate(),
) : EngagementModeHelper {

    override fun isPointerMode(uiContext: Context): Boolean {
        return hasAnyMouseDevice() &&
            hasLargeEnoughDisplay(uiContext) &&
            hasAnyPhysicalKeyboardDevice()
    }

    private fun hasAnyPhysicalKeyboardDevice() =
        inputHelper.getInputDeviceIds().any { deviceId -> isPhysicalKeyboardDevice(deviceId) }

    override fun isPhysicalKeyboardDevice(deviceId: Int) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            !inputHelper.isVirtual(deviceId) &&
                inputHelper.supportsSource(deviceId, InputDevice.SOURCE_KEYBOARD) &&
                inputHelper.getKeyboardType(deviceId) == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                inputHelper.isEnabled(deviceId)
        } else {
            false
        }

    private fun hasAnyMouseDevice() =
        inputHelper.getInputDeviceIds().any { deviceId -> isMouseDeviceEnabled(deviceId) }

    override fun isMouseDeviceEnabled(deviceId: Int) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            inputHelper.supportsSource(deviceId, InputDevice.SOURCE_MOUSE) &&
                inputHelper.isEnabled(deviceId) &&
                !inputHelper.supportsSource(deviceId, InputDevice.SOURCE_STYLUS)
        } else {
            false
        }

    override fun hasLargeEnoughDisplay(uiContext: Context): Boolean {
        val windowMetrics = windowMetricsCalculator.computeMaximumWindowMetrics(uiContext)
        val metrics = getDisplayMetrics(uiContext)
        val widthInchesSquared = (windowMetrics.bounds.width().toDouble() / metrics.xdpi).pow(2)
        val heightInchesSquared = (windowMetrics.bounds.height().toDouble() / metrics.ydpi).pow(2)

        val diagonalInchesSquared = widthInchesSquared + heightInchesSquared
        return diagonalInchesSquared >= DIAGONAL_INCH_SQUARED_LARGE_LOWER_BOUND
    }

    override fun getInputDeviceIds(): IntArray = inputHelper.getInputDeviceIds()

    override fun registerInputDeviceListener(
        listener: InputManager.InputDeviceListener,
        handler: Handler?,
    ) {
        inputHelper.registerInputDeviceListener(listener, handler)
    }

    override fun unregisterInputDeviceListener(listener: InputManager.InputDeviceListener) {
        inputHelper.unregisterInputDeviceListener(listener)
    }

    // TODO: b/486181581 - Remove once added to WindowMetrics
    /** Returns the [DisplayMetrics] for the given [uiContext], depending on the SDK level. */
    @Suppress("DEPRECATION")
    @VisibleForTesting
    fun getDisplayMetrics(@UiContext uiContext: Context): DisplayMetrics {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val realMetrics = DisplayMetrics()
                uiContext.display?.let {
                    it.getRealMetrics(realMetrics)
                    return realMetrics
                }
            } catch (e: UnsupportedOperationException) {
                // Use original context if display access is not supported
            }
        }
        return uiContext.resources.displayMetrics
    }

    companion object {
        /**
         * Lower bound of large diagonal screen size in inches, indicating device is productivity
         * and cursor-friendly.
         */
        private const val DIAGONAL_INCH_SQUARED_LARGE_LOWER_BOUND = 11 * 11
    }
}
