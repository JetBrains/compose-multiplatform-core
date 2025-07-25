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

package androidx.compose.foundation

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.indirect.IndirectTouchEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performIndirectTouchEvent

/** Synthetically range the x movements from 1000 to 0 */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectSwipeEvent(
    from: Float = TouchPadStart,
    to: Float = TouchPadEnd,
    touchpadWidth: Float = TouchPadEnd - TouchPadStart,
    stepCount: Int = 10,
    delayTimeMills: Long = 16L,
) {
    require(stepCount > 0) { "Step count should be at least 1" }
    val stepSize = touchpadWidth / stepCount
    val sign = if (from > to) -1 else 1

    var currentTime = SystemClock.uptimeMillis()
    var currentValue = from

    sendIndirectTouchPressEvent(currentTime, currentValue)
    currentTime += delayTimeMills
    currentValue += sign * stepSize

    val (newCurrentTime, newCurrentValue) =
        sendIndirectTouchMoveEvents(
            stepCount,
            currentTime,
            currentValue,
            delayTimeMills,
            sign,
            stepSize,
        )

    sendIndirectTouchReleaseEvent(newCurrentTime, newCurrentValue)
}

@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectTouchMoveEvents(
    stepCount: Int,
    currentTime: Long,
    currentValue: Float,
    delayTimeMills: Long,
    sign: Int,
    stepSize: Float,
): Pair<Long, Float> {
    var currentTime1 = currentTime
    var currentValue1 = currentValue
    repeat(stepCount) {
        val move =
            MotionEvent.obtain(
                currentTime1,
                currentTime1,
                MotionEvent.ACTION_MOVE,
                currentValue1,
                Offset.Zero.y,
                0,
            )
        if (it != stepCount - 1) {
            currentTime1 += delayTimeMills
            currentValue1 += sign * stepSize
        }
        performIndirectTouchEvent(IndirectTouchEvent(move))
    }
    return Pair(currentTime1, currentValue1)
}

@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectTouchReleaseEvent(
    currentTime: Long = SystemClock.uptimeMillis(),
    currentValue: Float = (TouchPadEnd - TouchPadStart) / 2f,
) {
    val up =
        MotionEvent.obtain(
            currentTime,
            currentTime,
            MotionEvent.ACTION_UP,
            currentValue,
            Offset.Zero.y,
            0,
        )
    performIndirectTouchEvent(IndirectTouchEvent(up))
}

@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectTouchPressEvent(
    currentTime: Long = SystemClock.uptimeMillis(),
    currentValue: Float = (TouchPadEnd - TouchPadStart) / 2f,
) {
    val down =
        MotionEvent.obtain(
            currentTime, // downTime,
            currentTime, // eventTime,
            MotionEvent.ACTION_DOWN,
            currentValue,
            Offset.Zero.y,
            0,
        )
    performIndirectTouchEvent(IndirectTouchEvent(down))
}

/** Swiping away from the start of the touchpad. */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectSwipeBackward() {
    sendIndirectSwipeEvent(TouchPadEnd, TouchPadStart)
}

/** Swiping towards the start of the touchpad. */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectSwipeForward() {
    sendIndirectSwipeEvent(TouchPadStart, TouchPadEnd)
}

@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun SemanticsNodeInteraction.sendIndirectTouchCancelEvent(sendMoveEvents: Boolean = true) {
    val stepSize = (TouchPadEnd - TouchPadStart) / 5
    var currentTime = SystemClock.uptimeMillis()
    var currentValue = TouchPadStart

    sendIndirectTouchPressEvent(currentTime, currentValue)
    currentTime += 16L
    currentValue += stepSize

    if (sendMoveEvents) {
        sendIndirectTouchMoveEvents(5, currentTime, currentValue, 16L, 1, stepSize)
    }

    val cancel =
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, Offset.Zero.y, Offset.Zero.y, 0)
    performIndirectTouchEvent(IndirectTouchEvent(cancel))
}

internal fun SemanticsNodeInteraction.sendIndirectPressReleaseEvent() {
    val currentTime = SystemClock.uptimeMillis()
    val currentValue = (TouchPadEnd - TouchPadStart) / 2
    sendIndirectTouchPressEvent(currentTime, currentValue)
    val (newCurrentTime, newCurrentValue) =
        sendIndirectTouchMoveEvents(1, currentTime, currentValue, 16L, 1, 0f)
    sendIndirectTouchReleaseEvent(newCurrentTime, newCurrentValue)
}

internal const val TouchPadEnd = 1000f
internal const val TouchPadStart = 0f
