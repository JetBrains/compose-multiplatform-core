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

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.util.DisplayMetrics
import androidx.core.util.Consumer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.window.layout.WindowLayoutInfo.EngagementMode
import androidx.window.layout.util.EngagementModeHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [EngagementModeBackendApi0] class. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class EngagementModeBackendTest {
    private val context = mock<Activity>()
    private val appContext = mock<Context>()
    private val engagementModeHelper = mock<EngagementModeHelper>()

    private lateinit var tracker: EngagementModeBackend

    @Before
    fun setUp() {
        whenever(context.applicationContext).thenReturn(appContext)
        whenever(context.resources).thenReturn(mock())
        whenever(context.resources.displayMetrics).thenReturn(DisplayMetrics())
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf())
        EngagementModeBackendApi0.resetInstance()
        tracker = EngagementModeBackendApi0(engagementModeHelper)
    }

    @Test
    fun testDefaultEngagementMode() {
        assertThat(tracker.engagementMode(context)).isEqualTo(EngagementMode.ENGAGEMENT_TOUCH)
    }

    @Test
    fun testRegisterCallbackStartsTracking() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)

        verify(engagementModeHelper).registerInputDeviceListener(any(), anyOrNull())
        verify(context).registerComponentCallbacks(any())
    }

    @Test
    fun testUnregisterCallbackStopsTracking() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)
        tracker.removeEngagementLayoutChangeCallback(callback)

        verify(engagementModeHelper).unregisterInputDeviceListener(any())
        verify(context).unregisterComponentCallbacks(any())
    }

    @Test
    fun testRegisterTwoActivities() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)

        verify(engagementModeHelper).registerInputDeviceListener(any(), anyOrNull())
        verify(context).registerComponentCallbacks(any())

        val secondActivity = mock<Activity>()

        tracker.addEngagementLayoutChangeCallback(secondActivity, executor, callback)
        verify(context).registerComponentCallbacks(any())
    }

    @Test
    fun testRegisterSameCallbackTwice() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)
        tracker.addEngagementLayoutChangeCallback(context, executor, callback)

        verify(callback, org.mockito.kotlin.times(1)).accept(any())
        verify(context, org.mockito.kotlin.times(1)).registerComponentCallbacks(any())
    }

    @Test
    fun testUnregisterTwoActivities_differentCallbacks() {
        val callback = mock<Consumer<EngagementMode>>()
        val secondCallback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run
        val secondActivity = mock<Activity>()

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)
        tracker.addEngagementLayoutChangeCallback(secondActivity, executor, secondCallback)
        tracker.removeEngagementLayoutChangeCallback(callback)

        verify(context).unregisterComponentCallbacks(any())

        tracker.removeEngagementLayoutChangeCallback(secondCallback)

        verify(secondActivity).unregisterComponentCallbacks(any())
        verify(engagementModeHelper).unregisterInputDeviceListener(any())
    }

    @Test
    fun testUnregisterTwoActivities_sameCallback() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run
        val secondActivity = mock<Activity>()

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)
        verify(context).registerComponentCallbacks(any())

        tracker.addEngagementLayoutChangeCallback(secondActivity, executor, callback)
        verify(secondActivity, never()).registerComponentCallbacks(any())

        tracker.removeEngagementLayoutChangeCallback(callback)

        verify(context).unregisterComponentCallbacks(any())
        verify(engagementModeHelper).unregisterInputDeviceListener(any())
    }

    @Test
    fun testEngagementModeChanges_whenCriteriaMet() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run
        val inputCaptor = argumentCaptor<InputManager.InputDeviceListener>()

        // Mock large display but NO devices initially
        whenever(engagementModeHelper.hasLargeEnoughDisplay(any())).thenReturn(true)
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf())

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)

        // Should receive initial DIRECT_TOUCH
        verify(callback).accept(EngagementMode.ENGAGEMENT_TOUCH)

        verify(engagementModeHelper).registerInputDeviceListener(inputCaptor.capture(), anyOrNull())
        val inputListener = inputCaptor.firstValue

        // Mock Keyboard added
        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        inputListener.onInputDeviceAdded(1)

        // Still DIRECT_TOUCH as mouse is missing
        verify(callback).accept(EngagementMode.ENGAGEMENT_TOUCH)

        // Mock Mouse added
        whenever(engagementModeHelper.isMouseDeviceEnabled(2)).thenReturn(true)
        inputListener.onInputDeviceAdded(2)

        assertThat(tracker.engagementMode(context))
            .isEqualTo(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
        verify(callback).accept(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
    }

    @Test
    fun testEngagementModeChanges_mouseAndKeyboardSource() {
        val callback = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run
        val inputCaptor = argumentCaptor<InputManager.InputDeviceListener>()

        // Mock large display but NO devices initially
        whenever(engagementModeHelper.hasLargeEnoughDisplay(any())).thenReturn(true)
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf())

        tracker.addEngagementLayoutChangeCallback(context, executor, callback)

        // Should receive initial DIRECT_TOUCH
        verify(callback).accept(EngagementMode.ENGAGEMENT_TOUCH)

        verify(engagementModeHelper).registerInputDeviceListener(inputCaptor.capture(), anyOrNull())
        val inputListener = inputCaptor.firstValue

        // Mock Keyboard added
        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        whenever(engagementModeHelper.isMouseDeviceEnabled(1)).thenReturn(true)
        inputListener.onInputDeviceAdded(1)

        assertThat(tracker.engagementMode(context))
            .isEqualTo(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
        verify(callback).accept(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
    }

    @Test
    fun testEngagementMode_multipleActivities() {
        val activity1 = mock<Activity>()
        val activity2 = mock<Activity>()
        whenever(activity1.applicationContext).thenReturn(appContext)
        whenever(activity2.applicationContext).thenReturn(appContext)

        val callback1 = mock<Consumer<EngagementMode>>()
        val callback2 = mock<Consumer<EngagementMode>>()
        val executor = Runnable::run

        // Set up: activity1 on large display, activity2 on small display
        whenever(engagementModeHelper.hasLargeEnoughDisplay(activity1)).thenReturn(true)
        whenever(engagementModeHelper.hasLargeEnoughDisplay(activity2)).thenReturn(false)

        // Add mouse and keyboard
        whenever(engagementModeHelper.getInputDeviceIds()).thenReturn(intArrayOf(1, 2))
        whenever(engagementModeHelper.isPhysicalKeyboardDevice(1)).thenReturn(true)
        whenever(engagementModeHelper.isMouseDeviceEnabled(2)).thenReturn(true)

        tracker.addEngagementLayoutChangeCallback(activity1, executor, callback1)
        tracker.addEngagementLayoutChangeCallback(activity2, executor, callback2)

        // Activity1 should be PRECISE_POINTER, Activity2 should be DIRECT_TOUCH
        assertThat(tracker.engagementMode(activity1))
            .isEqualTo(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
        assertThat(tracker.engagementMode(activity2)).isEqualTo(EngagementMode.ENGAGEMENT_TOUCH)

        verify(callback1).accept(EngagementMode.ENGAGEMENT_PRECISE_POINTER)
        verify(callback2).accept(EngagementMode.ENGAGEMENT_TOUCH)
    }
}
