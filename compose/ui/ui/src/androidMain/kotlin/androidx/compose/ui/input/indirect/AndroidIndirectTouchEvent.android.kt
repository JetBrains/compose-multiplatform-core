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

package androidx.compose.ui.input.indirect

import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.geometry.Offset

internal class AndroidIndirectTouchEvent
@OptIn(ExperimentalIndirectTouchTypeApi::class)
constructor(
    override val position: Offset,
    override val uptimeMillis: Long,
    override val type: IndirectTouchEventType,
    internal val nativeEvent: MotionEvent,
) : PlatformIndirectTouchEvent

@ExperimentalIndirectTouchTypeApi
val IndirectTouchEvent.nativeEvent: MotionEvent
    get() = (this as AndroidIndirectTouchEvent).nativeEvent

/** Allows creation of a [IndirectTouchEvent] from a [MotionEvent] for cross module testing. */
@ExperimentalIndirectTouchTypeApi
fun IndirectTouchEvent(motionEvent: MotionEvent): IndirectTouchEvent =
    AndroidIndirectTouchEvent(
        position = Offset(motionEvent.x, motionEvent.y),
        uptimeMillis = motionEvent.eventTime,
        type = convertActionToIndirectTouchEventType(motionEvent.actionMasked),
        nativeEvent = motionEvent,
    )

@OptIn(ExperimentalIndirectTouchTypeApi::class)
internal fun convertActionToIndirectTouchEventType(actionMasked: Int): IndirectTouchEventType {
    return when (actionMasked) {
        ACTION_UP -> IndirectTouchEventType.Release
        ACTION_DOWN -> IndirectTouchEventType.Press
        ACTION_MOVE -> IndirectTouchEventType.Move
        else -> IndirectTouchEventType.Unknown
    }
}
