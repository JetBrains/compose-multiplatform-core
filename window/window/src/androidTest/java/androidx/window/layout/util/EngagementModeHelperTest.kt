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
import android.content.res.Resources
import android.hardware.input.InputManager
import android.util.DisplayMetrics
import android.view.InputDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.window.layout.WindowMetricsCalculator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for the [EngagementModeHelper] class. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class EngagementModeHelperTest {

    @Test
    fun testHasLargeEnoughDisplay_true() {
        val context = mock<Context>()
        val resources = mock<Resources>()
        val backend = mock<InputHelper>()
        val windowMetricsCalculator = mock<WindowMetricsCalculator>()
        val metrics =
            DisplayMetrics().apply {
                widthPixels = 3000
                heightPixels = 2000
                xdpi = 200f
                ydpi = 200f
            }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(metrics)
        whenever(windowMetricsCalculator.computeMaximumWindowMetrics(context))
            .thenReturn(WindowMetricsCalculator.fromDisplayMetrics(metrics))

        val helper = EngagementModeHelperImpl(context, backend, windowMetricsCalculator)

        assertThat(helper.hasLargeEnoughDisplay(context)).isTrue()
    }

    @Test
    fun testHasLargeEnoughDisplay_false() {
        val context = mock<Context>()
        val resources = mock<Resources>()
        val backend = mock<InputHelper>()
        val windowMetricsCalculator = mock<WindowMetricsCalculator>()
        val metrics =
            DisplayMetrics().apply {
                widthPixels = 1000
                heightPixels = 500
                xdpi = 200f
                ydpi = 200f
            }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(metrics)
        whenever(windowMetricsCalculator.computeMaximumWindowMetrics(context))
            .thenReturn(WindowMetricsCalculator.fromDisplayMetrics(metrics))

        val helper = EngagementModeHelperImpl(context, backend, windowMetricsCalculator)
        assertThat(helper.hasLargeEnoughDisplay(context)).isFalse()
    }

    @Test
    fun testIsPhysicalKeyboardDevice_true() {
        val backend = mock<InputHelper>()
        whenever(backend.isVirtual(1)).thenReturn(false)
        whenever(backend.supportsSource(1, InputDevice.SOURCE_KEYBOARD)).thenReturn(true)
        whenever(backend.getKeyboardType(1)).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        whenever(backend.isEnabled(1)).thenReturn(true)

        val helper = EngagementModeHelperImpl(mock(), backend)
        assertThat(helper.isPhysicalKeyboardDevice(1)).isTrue()
    }

    @Test
    fun testIsPhysicalKeyboardDevice_virtual_false() {
        val backend = mock<InputHelper>()
        whenever(backend.isVirtual(1)).thenReturn(true)
        whenever(backend.supportsSource(1, InputDevice.SOURCE_KEYBOARD)).thenReturn(true)
        whenever(backend.getKeyboardType(1)).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        whenever(backend.isEnabled(1)).thenReturn(true)

        val helper = EngagementModeHelperImpl(mock(), backend)
        assertThat(helper.isPhysicalKeyboardDevice(1)).isFalse()
    }

    @Test
    fun testIsMouseDeviceEnabled_true() {
        val backend = mock<InputHelper>()
        whenever(backend.supportsSource(1, InputDevice.SOURCE_MOUSE)).thenReturn(true)
        whenever(backend.supportsSource(1, InputDevice.SOURCE_STYLUS)).thenReturn(false)
        whenever(backend.isEnabled(1)).thenReturn(true)

        val helper = EngagementModeHelperImpl(mock(), backend)
        assertThat(helper.isMouseDeviceEnabled(1)).isTrue()
    }

    @Test
    fun testIsMouseDeviceEnabled_stylus_false() {
        val backend = mock<InputHelper>()
        whenever(backend.supportsSource(1, InputDevice.SOURCE_MOUSE)).thenReturn(true)
        whenever(backend.supportsSource(1, InputDevice.SOURCE_STYLUS)).thenReturn(true)
        whenever(backend.isEnabled(1)).thenReturn(true)

        val helper = EngagementModeHelperImpl(mock(), backend)
        assertThat(helper.isMouseDeviceEnabled(1)).isFalse()
    }

    @Test
    fun testIsPointerMode_true() {
        val context = mock<Context>()
        val resources = mock<Resources>()
        val backend = mock<InputHelper>()
        val windowMetricsCalculator = mock<WindowMetricsCalculator>()
        val metrics =
            DisplayMetrics().apply {
                widthPixels = 3000
                heightPixels = 2000
                xdpi = 200f
                ydpi = 200f
            }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(metrics)
        whenever(windowMetricsCalculator.computeMaximumWindowMetrics(context))
            .thenReturn(WindowMetricsCalculator.fromDisplayMetrics(metrics))
        whenever(backend.getInputDeviceIds()).thenReturn(intArrayOf(1, 2))

        // Device 1: Keyboard
        whenever(backend.isVirtual(1)).thenReturn(false)
        whenever(backend.supportsSource(1, InputDevice.SOURCE_KEYBOARD)).thenReturn(true)
        whenever(backend.getKeyboardType(1)).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        whenever(backend.isEnabled(1)).thenReturn(true)

        // Device 2: Mouse
        whenever(backend.supportsSource(2, InputDevice.SOURCE_MOUSE)).thenReturn(true)
        whenever(backend.supportsSource(2, InputDevice.SOURCE_STYLUS)).thenReturn(false)
        whenever(backend.isEnabled(2)).thenReturn(true)

        val helper = EngagementModeHelperImpl(context, backend, windowMetricsCalculator)
        assertThat(helper.isPointerMode(context)).isTrue()
    }

    @Test
    fun testRegisterInputDeviceListener() {
        val backend = mock<InputHelper>()
        val helper = EngagementModeHelperImpl(mock(), backend)
        val listener = mock<InputManager.InputDeviceListener>()

        helper.registerInputDeviceListener(listener)
        verify(backend).registerInputDeviceListener(eq(listener), anyOrNull())
    }
}
