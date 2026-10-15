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

package androidx.compose.ui.input.pointer.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Velocity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Adapted from `Lsq2VelocityTrackerTest` (androidHostTest) to verify the iOS-specific
 * [PlatformVelocityTracker] implementation.
 */
class IosVelocityTrackerTest {

    private val Tolerance = 0.2f

    private val MaximumVelocity = Velocity(Float.MAX_VALUE, Float.MAX_VALUE)

    @Test
    fun calculateVelocity_noData_returnsZero() {
        val tracker = PlatformVelocityTracker()
        assertEquals(Velocity.Zero, tracker.calculateVelocity(MaximumVelocity))
    }

    @Test
    fun calculateVelocity_onePosition_returnsZero() {
        val tracker = PlatformVelocityTracker()
        tracker.addPosition(216690896L, Offset(270f, 538.2857f))
        assertEquals(Velocity.Zero, tracker.calculateVelocity(MaximumVelocity))
    }

    @Test
    fun calculateVelocity_twoPoints_sameTimeStamp_returnsZero() {
        val tracker = PlatformVelocityTracker()
        tracker.addPosition(1000L, Offset(100f, 0f))
        tracker.addPosition(1000L, Offset(200f, 0f))
        tracker.addPosition(1000L, Offset(300f, 0f))
        assertEquals(Velocity.Zero, tracker.calculateVelocity(MaximumVelocity))
    }

    @Test
    fun calculateVelocity_whenOlderSamplesDiscardedDueToTimeGap_computesVelocityWithRemainingTwoSamples() {
        val tracker = PlatformVelocityTracker()

        // Add older samples which will will end up being discarded due to the time gap.
        tracker.addPosition(0L, Offset(0f, 0f))
        tracker.addPosition(20L, Offset(100f, 0f))

        // Introduce a time gap (which exceeds the 40ms threshold).
        // This leaves exactly 2 effective samples (the ones at 120ms and 130ms) for the
        // calculation.
        tracker.addPosition(120L, Offset(200f, 0f))
        tracker.addPosition(130L, Offset(300f, 0f))

        checkVelocity(
            actual = tracker.calculateVelocity(MaximumVelocity),
            expectedDx = 10000f,
            expectedDy = 0f,
        )
    }

    @Test
    fun calculateVelocity_exactlyTwoSamples_returnsLinearVelocity() {
        val tracker = PlatformVelocityTracker()

        // 10 pixels over 10 milliseconds = 1000 pixels per second
        tracker.addPosition(0L, Offset(0f, 0f))
        tracker.addPosition(10L, Offset(10f, 0f))

        checkVelocity(
            actual = tracker.calculateVelocity(MaximumVelocity),
            expectedDx = 1000f,
            expectedDy = 0f,
        )
    }

    @Test
    fun resetTracking_resetsTracking() {
        val tracker = PlatformVelocityTracker()
        tracker.addPosition(216690896L, Offset(270f, 538.2857f))

        tracker.resetTracking()

        assertEquals(Velocity.Zero, tracker.calculateVelocity(MaximumVelocity))
    }

    @Test
    fun calculateVelocityWithMaxValue_valueShouldBeGreaterThanZero() {
        val tracker = PlatformVelocityTracker()
        assertFailsWith<IllegalStateException> {
            tracker.calculateVelocity(Velocity(-1f, 1f))
        }

        assertFailsWith<IllegalStateException> {
            tracker.calculateVelocity(Velocity(1f, -1f))
        }
    }

    @Test
    fun addPointerInputChange_usesOriginalEventPosition() {
        val tracker = PlatformVelocityTracker()

        // The local position stays the same (the node moves together with the pointer), while the
        // original event position moves 10 px every 10 ms (1000 px/s).
        tracker.addPointerInputChange(
            createDownEvent(0L, position = Offset.Zero, originalPosition = Offset.Zero),
            Offset.Zero,
        )
        for (i in 1..5) {
            tracker.addPointerInputChange(
                createMoveEvent(
                    timeMillis = i * 10L,
                    position = Offset.Zero,
                    originalPosition = Offset(0f, i * 10f),
                ),
                Offset.Zero,
            )
        }

        checkVelocity(
            actual = tracker.calculateVelocity(MaximumVelocity),
            expectedDx = 0f,
            expectedDy = 1000f,
        )
    }

    @Test
    fun addPointerInputChange_ignoresLocalPosition() {
        val tracker = PlatformVelocityTracker()

        // The local position moves, while the original event position stays the same.
        tracker.addPointerInputChange(
            createDownEvent(0L, position = Offset.Zero, originalPosition = Offset.Zero),
            Offset.Zero,
        )
        for (i in 1..5) {
            tracker.addPointerInputChange(
                createMoveEvent(
                    timeMillis = i * 10L,
                    position = Offset(0f, i * 10f),
                    originalPosition = Offset.Zero,
                ),
                Offset.Zero,
            )
        }

        assertEquals(Velocity.Zero, tracker.calculateVelocity(MaximumVelocity))
    }

    @Test
    fun addPointerInputChange_historicalChanges_useOriginalEventPosition() {
        val tracker = PlatformVelocityTracker()

        tracker.addPointerInputChange(
            createDownEvent(0L, position = Offset.Zero, originalPosition = Offset.Zero),
            Offset.Zero,
        )
        // A single move event with historical changes; all local positions are the same.
        tracker.addPointerInputChange(
            createMoveEvent(
                timeMillis = 50L,
                position = Offset.Zero,
                originalPosition = Offset(50f, 0f),
                history = (1..4).map {
                    HistoricalChange(
                        uptimeMillis = it * 10L,
                        position = Offset.Zero,
                        scaleFactor = 1f,
                        panOffset = Offset.Zero,
                        originalEventPosition = Offset(it * 10f, 0f),
                    )
                },
            ),
            Offset.Zero,
        )

        checkVelocity(
            actual = tracker.calculateVelocity(MaximumVelocity),
            expectedDx = 1000f,
            expectedDy = 0f,
        )
    }

    @Test
    fun addPointerInputChange_appliesOffsetToOriginalEventPosition() {
        val tracker = PlatformVelocityTracker()

        // The offset is constant, so it should not affect the velocity.
        val offset = Offset(100f, 200f)
        tracker.addPointerInputChange(
            createDownEvent(0L, position = Offset.Zero, originalPosition = Offset.Zero),
            offset,
        )
        for (i in 1..5) {
            tracker.addPointerInputChange(
                createMoveEvent(
                    timeMillis = i * 10L,
                    position = Offset.Zero,
                    originalPosition = Offset(i * 10f, i * 10f),
                ),
                offset,
            )
        }

        checkVelocity(
            actual = tracker.calculateVelocity(MaximumVelocity),
            expectedDx = 1000f,
            expectedDy = 1000f,
        )
    }

    private fun checkVelocity(actual: Velocity, expectedDx: Float, expectedDy: Float) {
        assertEquals(expectedDx, actual.x, Tolerance)
        assertEquals(expectedDy, actual.y, Tolerance)
    }
}

private fun createDownEvent(timeMillis: Long, position: Offset, originalPosition: Offset) =
    PointerInputChange(
        id = PointerId(0),
        uptimeMillis = timeMillis,
        position = position,
        pressed = true,
        pressure = 1.0f,
        previousUptimeMillis = 0,
        previousPosition = Offset.Zero,
        previousPressed = false,
        isInitiallyConsumed = false,
        type = PointerType.Touch,
        historical = emptyList(),
        scrollDelta = Offset.Zero,
        scaleFactor = 1.0f,
        panOffset = Offset.Zero,
        originalEventPosition = originalPosition,
    )

private fun createMoveEvent(
    timeMillis: Long,
    position: Offset,
    originalPosition: Offset,
    history: List<HistoricalChange> = emptyList(),
) =
    PointerInputChange(
        id = PointerId(0),
        uptimeMillis = timeMillis,
        position = position,
        pressed = true,
        pressure = 1.0f,
        previousUptimeMillis = 0,
        previousPosition = Offset.Zero,
        previousPressed = true,
        isInitiallyConsumed = false,
        type = PointerType.Touch,
        historical = history,
        scrollDelta = Offset.Zero,
        scaleFactor = 1.0f,
        panOffset = Offset.Zero,
        originalEventPosition = originalPosition,
    )
