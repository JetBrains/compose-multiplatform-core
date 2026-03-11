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

package androidx.compose.ui.input.pointer.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastForEach

internal actual fun PlatformVelocityTracker(): PlatformVelocityTracker = UIKitVelocityTracker()

private class UIKitVelocityTracker: PlatformVelocityTracker {
    private val xVelocityTracker = PointerVelocityTracker1D(preventReversedPointerMovements = true)
    private val yVelocityTracker = PointerVelocityTracker1D(preventReversedPointerMovements = true)

    override fun addPointerInputChange(event: PointerInputChange, offset: Offset) {
        // If this is ACTION_DOWN: Reset the tracking.
        if (event.changedToDownIgnoreConsumed()) {
            resetTracking()
        }
        event.historical.fastForEach {
            addPosition(it.uptimeMillis, it.position + offset)
        }
        addPosition(event.uptimeMillis, event.position + offset)
    }

    override fun calculateVelocity(maximumVelocity: Velocity): Velocity {
        val velocityX = xVelocityTracker.calculateVelocity(maximumVelocity.x)
        val velocityY = yVelocityTracker.calculateVelocity(maximumVelocity.y)
        return Velocity(velocityX, velocityY)
    }

    override fun resetTracking() {
        xVelocityTracker.resetTracking()
        yVelocityTracker.resetTracking()
    }

    override fun addPosition(timeMillis: Long, position: Offset) {
        xVelocityTracker.addDataPoint(timeMillis, position.x)
        yVelocityTracker.addDataPoint(timeMillis, position.y)
    }
}
