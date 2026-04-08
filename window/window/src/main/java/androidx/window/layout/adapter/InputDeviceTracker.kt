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

package androidx.window.layout.adapter

import android.hardware.input.InputManager
import androidx.annotation.GuardedBy
import androidx.window.layout.util.EngagementModeHelper
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A class that tracks input devices and notifies listeners when the state of qualified keyboard and
 * pointer devices changes.
 */
internal class InputDeviceTracker(private val engagementModeHelper: EngagementModeHelper) {
    private val lock = ReentrantLock()

    @GuardedBy("lock") private val listeners = mutableListOf<Listener>()

    @GuardedBy("lock") private var lastMouseAndKeyboardConnected: Boolean = false
    @GuardedBy("lock") private val qualifiedKeyboards = mutableSetOf<Int>()
    @GuardedBy("lock") private val qualifiedPointers = mutableSetOf<Int>()

    private val inputListener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                val changed = lock.withLock { refreshInput(deviceId) }
                if (changed) {
                    notifyListeners()
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                val changed =
                    lock.withLock {
                        qualifiedKeyboards.remove(deviceId)
                        qualifiedPointers.remove(deviceId)

                        val currQualifiedInputDevice =
                            qualifiedKeyboards.isNotEmpty() && qualifiedPointers.isNotEmpty()
                        if (lastMouseAndKeyboardConnected != currQualifiedInputDevice) {
                            lastMouseAndKeyboardConnected = currQualifiedInputDevice
                            true
                        } else {
                            false
                        }
                    }
                if (changed) {
                    notifyListeners()
                }
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                val changed = lock.withLock { refreshInput(deviceId) }
                if (changed) {
                    notifyListeners()
                }
            }
        }

    fun addListener(listener: Listener) {
        lock.withLock {
            val isFirstListener = listeners.isEmpty()
            listeners.add(listener)
            if (isFirstListener) {
                startTracking()
            }
        }
    }

    fun removeListener(listener: Listener) {
        lock.withLock {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                stopTracking()
            }
        }
    }

    fun isMouseAndKeyboardConnected(): Boolean {
        return lock.withLock { lastMouseAndKeyboardConnected }
    }

    private fun startTracking() {
        engagementModeHelper.registerInputDeviceListener(inputListener)
        for (deviceId in engagementModeHelper.getInputDeviceIds()) {
            refreshInput(deviceId)
        }
    }

    private fun stopTracking() {
        engagementModeHelper.unregisterInputDeviceListener(inputListener)
    }

    /**
     * Recalculate and cache whether the device is connected to any qualified keyboard or pointer
     * device when listener detects that input device is added, changed or removed.
     */
    @GuardedBy("lock")
    private fun refreshInput(deviceId: Int): Boolean {
        qualifiedKeyboards.remove(deviceId)
        qualifiedPointers.remove(deviceId)
        if (engagementModeHelper.isPhysicalKeyboardDevice(deviceId)) {
            qualifiedKeyboards.add(deviceId)
        }
        if (engagementModeHelper.isMouseDeviceEnabled(deviceId)) {
            qualifiedPointers.add(deviceId)
        }
        val currQualifiedInputDevice =
            qualifiedKeyboards.isNotEmpty() && qualifiedPointers.isNotEmpty()
        if (lastMouseAndKeyboardConnected != currQualifiedInputDevice) {
            lastMouseAndKeyboardConnected = currQualifiedInputDevice
            return true
        }
        return false
    }

    private fun notifyListeners() {
        val currentListeners = lock.withLock { listeners.toList() }
        val isConnected = isMouseAndKeyboardConnected()
        currentListeners.forEach { it.onInputDeviceConnectionChanged(isConnected) }
    }

    interface Listener {
        fun onInputDeviceConnectionChanged(isMouseAndKeyboardConnected: Boolean)
    }
}
