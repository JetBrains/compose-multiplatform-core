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
import android.view.InputDevice
import androidx.annotation.RequiresApi

/** Helper class to interact with [InputManager] and [InputDevice]. */
internal interface InputHelper {
    /** Returns the list of input device IDs. */
    fun getInputDeviceIds(): IntArray

    /** Returns `true` if the device with the given [deviceId] is a virtual device. */
    fun isVirtual(deviceId: Int): Boolean

    /** Returns `true` if the device with the given [deviceId] is enabled. */
    fun isEnabled(deviceId: Int): Boolean

    /** Returns `true` if the device with the given [deviceId] supports the given [source]. */
    fun supportsSource(deviceId: Int, source: Int): Boolean

    /** Returns the keyboard type of the device with the given [deviceId]. */
    fun getKeyboardType(deviceId: Int): Int

    /**
     * Registers an [InputManager.InputDeviceListener] to be notified about changes in input
     * devices.
     */
    fun registerInputDeviceListener(listener: InputManager.InputDeviceListener, handler: Handler?)

    /** Unregisters an [InputManager.InputDeviceListener]. */
    fun unregisterInputDeviceListener(listener: InputManager.InputDeviceListener)
}

/** Implementation of [InputHelper]. */
internal class InputHelperImpl(context: Context) : InputHelper {
    private val inputManager =
        context.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    override fun getInputDeviceIds(): IntArray = inputManager.inputDeviceIds

    override fun isVirtual(deviceId: Int): Boolean =
        inputManager.getInputDevice(deviceId)?.isVirtual ?: true

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun isEnabled(deviceId: Int): Boolean =
        inputManager.getInputDevice(deviceId)?.isEnabled ?: false

    override fun supportsSource(deviceId: Int, source: Int): Boolean =
        inputManager.getInputDevice(deviceId)?.supportsSource(source) ?: false

    override fun getKeyboardType(deviceId: Int): Int =
        inputManager.getInputDevice(deviceId)?.keyboardType ?: InputDevice.KEYBOARD_TYPE_NONE

    override fun registerInputDeviceListener(
        listener: InputManager.InputDeviceListener,
        handler: Handler?,
    ) {
        inputManager.registerInputDeviceListener(listener, handler)
    }

    override fun unregisterInputDeviceListener(listener: InputManager.InputDeviceListener) {
        inputManager.unregisterInputDeviceListener(listener)
    }
}
