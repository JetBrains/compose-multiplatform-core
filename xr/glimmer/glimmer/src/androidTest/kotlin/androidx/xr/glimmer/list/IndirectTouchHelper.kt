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

package androidx.xr.glimmer.list

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.indirect.IndirectTouchEvent
import androidx.compose.ui.input.indirect.IndirectTouchEventPrimaryDirectionalMotionAxis
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performIndirectTouchEvent
import androidx.core.view.InputDeviceCompat.SOURCE_TOUCH_NAVIGATION

/** Synthetic indirect swipe. */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.performIndirectSwipe(distance: Float) {
    val currentTime = SystemClock.uptimeMillis()

    val down =
        MotionEvent.obtain(
            currentTime, // downTime,
            currentTime, // eventTime,
            MotionEvent.ACTION_DOWN,
            0f,
            Offset.Zero.y,
            0,
        )
    down.source = SOURCE_TOUCH_NAVIGATION
    performIndirectTouchEvent(
        IndirectTouchEvent(
            motionEvent = down,
            primaryDirectionalMotionAxis = IndirectTouchEventPrimaryDirectionalMotionAxis.X,
        )
    )

    val move =
        MotionEvent.obtain(
            currentTime + 200L,
            currentTime + 200L,
            MotionEvent.ACTION_MOVE,
            distance,
            Offset.Zero.y,
            0,
        )
    move.source = SOURCE_TOUCH_NAVIGATION
    performIndirectTouchEvent(
        IndirectTouchEvent(
            motionEvent = move,
            primaryDirectionalMotionAxis = IndirectTouchEventPrimaryDirectionalMotionAxis.X,
        )
    )

    val up =
        MotionEvent.obtain(
            currentTime + 200L,
            currentTime + 200L,
            MotionEvent.ACTION_UP,
            distance,
            Offset.Zero.y,
            0,
        )
    up.source = SOURCE_TOUCH_NAVIGATION
    performIndirectTouchEvent(
        IndirectTouchEvent(
            motionEvent = up,
            primaryDirectionalMotionAxis = IndirectTouchEventPrimaryDirectionalMotionAxis.X,
        )
    )
}
