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

package androidx.compose.ui.platform

import platform.UIKit.UIApplication

internal class UIKitKeepScreenOnManager private constructor(): PlatformKeepScreenOnManager {

    val isKeepScreenOnEnabled: Boolean get() = _isKeepScreenOnEnabled

    private var _isKeepScreenOnEnabled: Boolean
        get() = UIApplication.sharedApplication.idleTimerDisabled
        set(value) { UIApplication.sharedApplication.idleTimerDisabled = value }

    private var keepScreenOnCount: Int = 0

    override fun incrementKeepScreenOnCount() {
        keepScreenOnCount++
        updateIsKeepScreenOnEnabled()
    }

    override fun decrementKeepScreenOnCount() {
        keepScreenOnCount--
        updateIsKeepScreenOnEnabled()
    }

    private fun updateIsKeepScreenOnEnabled() {
        _isKeepScreenOnEnabled = keepScreenOnCount > 0
    }

    /**
     * Resets the keep screen on counter to zero and updates the idle timer state.
     *
     * This method is intended for testing purposes only.
     */
    fun reset() {
        keepScreenOnCount = 0
        updateIsKeepScreenOnEnabled()
    }

    companion object {
        val instance: UIKitKeepScreenOnManager by lazy {
            UIKitKeepScreenOnManager()
        }
    }
}