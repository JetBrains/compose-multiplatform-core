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
import androidx.window.layout.util.EngagementModeHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [InputDeviceTracker]. */
class InputDeviceTrackerTest {
    private val engagementModeHelper = mock<EngagementModeHelper>()
    private lateinit var tracker: InputDeviceTracker

    @Before
    fun setUp() {
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf())
        tracker = InputDeviceTracker(engagementModeHelper)
    }

    @Test
    fun testAddListenerStartsTracking() {
        val listener = mock<InputDeviceTracker.Listener>()
        tracker.addListener(listener)

        verify(engagementModeHelper).registerInputDeviceListener(any(), anyOrNull())
        verify(engagementModeHelper).getInputDeviceIds()
    }

    @Test
    fun testRemoveLastListenerStopsTracking() {
        val listener = mock<InputDeviceTracker.Listener>()
        tracker.addListener(listener)
        tracker.removeListener(listener)

        verify(engagementModeHelper).unregisterInputDeviceListener(any())
    }

    @Test
    fun testIsMouseAndKeyboardConnected_initial() {
        assertThat(tracker.isMouseAndKeyboardConnected()).isFalse()
    }

    @Test
    fun testNotifyListener_whenKeyboardAndMouseAdded() {
        val listener = mock<InputDeviceTracker.Listener>()
        tracker.addListener(listener)

        val inputCaptor = argumentCaptor<InputManager.InputDeviceListener>()
        verify(engagementModeHelper).registerInputDeviceListener(inputCaptor.capture(), anyOrNull())
        val inputListener = inputCaptor.firstValue

        // Add Keyboard
        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        inputListener.onInputDeviceAdded(1)
        assertThat(tracker.isMouseAndKeyboardConnected()).isFalse()
        verify(listener, times(0)).onInputDeviceConnectionChanged(any())

        // Add Mouse
        whenever(engagementModeHelper.isMouseDeviceEnabled(2)).thenReturn(true)
        inputListener.onInputDeviceAdded(2)
        assertThat(tracker.isMouseAndKeyboardConnected()).isTrue()
        verify(listener).onInputDeviceConnectionChanged(true)
    }

    @Test
    fun testNotifyListener_whenMouseRemoved() {
        val listener = mock<InputDeviceTracker.Listener>()

        // Initial state: Keyboard (1) and Mouse (2) connected
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf(1, 2))
        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        whenever(engagementModeHelper.isMouseDeviceEnabled(2)).thenReturn(true)

        tracker.addListener(listener)
        assertThat(tracker.isMouseAndKeyboardConnected()).isTrue()

        val inputCaptor = argumentCaptor<InputManager.InputDeviceListener>()
        verify(engagementModeHelper).registerInputDeviceListener(inputCaptor.capture(), anyOrNull())
        val inputListener = inputCaptor.firstValue

        // Remove Mouse
        inputListener.onInputDeviceRemoved(2)
        assertThat(tracker.isMouseAndKeyboardConnected()).isFalse()
        verify(listener).onInputDeviceConnectionChanged(false)
    }

    @Test
    fun testMultipleListeners() {
        val listener1 = mock<InputDeviceTracker.Listener>()
        val listener2 = mock<InputDeviceTracker.Listener>()

        tracker.addListener(listener1)
        tracker.addListener(listener2)

        val inputCaptor = argumentCaptor<InputManager.InputDeviceListener>()
        verify(engagementModeHelper).registerInputDeviceListener(inputCaptor.capture(), anyOrNull())
        val inputListener = inputCaptor.firstValue

        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        whenever(engagementModeHelper.isMouseDeviceEnabled(2)).thenReturn(true)
        inputListener.onInputDeviceAdded(1)
        inputListener.onInputDeviceAdded(2)

        verify(listener1).onInputDeviceConnectionChanged(true)
        verify(listener2).onInputDeviceConnectionChanged(true)

        tracker.removeListener(listener1)
        inputListener.onInputDeviceRemoved(2)

        // listener1 was removed, so it shouldn't get the second update
        verify(listener1, times(1)).onInputDeviceConnectionChanged(any())
        verify(listener2).onInputDeviceConnectionChanged(false)
    }
}
