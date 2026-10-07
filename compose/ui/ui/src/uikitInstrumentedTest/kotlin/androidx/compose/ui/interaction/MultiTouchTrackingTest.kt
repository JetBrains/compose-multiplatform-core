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

package androidx.compose.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private val DragDistance = 20.dp
private val DragDuration = 10.milliseconds

class MultiTouchTrackingTest {
    @Test
    fun testStagedMultiTouchTracking() = runUIKitInstrumentedTest {
        var pressedPointers = 0

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                pressedPointers = event.changes.count { it.pressed }
                            }
                        }
                    }
            )
        }

        val choreographer = requireNotNull(frameChoreographer) {
            "FrameChoreographer is unavailable for the test window."
        }

        fun assertTouchesDown(expected: Int, step: String) {
            assertEquals(expected, pressedPointers, "$step: touches tracked by the Compose view")
            assertEquals(
                expected,
                choreographer.ongoingActivitiesCount,
                "$step: ongoing activities"
            )
        }

        assertTouchesDown(0, "before any touch is put on the screen")

        // The first group: three touches go down one by one.
        val touch1 = touchDown(DpOffset(50.dp, 200.dp))
        val touch2 = touchDown(DpOffset(150.dp, 200.dp))
        val touch3 = touchDown(DpOffset(250.dp, 200.dp))

        waitForIdle()
        assertTouchesDown(3, "the first group of touches is down")

        // The second group: two more touches join while the first group keeps pressing the screen.
        val touch4 = touchDown(DpOffset(100.dp, 350.dp))
        val touch5 = touchDown(DpOffset(200.dp, 350.dp))

        waitForIdle()
        assertTouchesDown(5, "both groups of touches are down")

        // Drag all the touches.
        touch1.dragBy(dy = -DragDistance, duration = DragDuration)
        touch2.dragBy(dy = -DragDistance, duration = DragDuration)
        touch3.dragBy(dy = -DragDistance, duration = DragDuration)
        touch4.dragBy(dy = -DragDistance, duration = DragDuration)
        touch5.dragBy(dy = -DragDistance, duration = DragDuration)

        waitForIdle()
        assertTouchesDown(5, "all the touches are dragged")

        touch1.up()
        touch3.up()
        touch4.up()

        waitForIdle()
        assertTouchesDown(2, "touch1, touch3 and touch4 are released")

        // The touches that are still down keep being tracked and can still be dragged.
        touch2.dragBy(dy = DragDistance, duration = DragDuration)
        touch5.dragBy(dy = DragDistance, duration = DragDuration)

        waitForIdle()
        assertTouchesDown(2, "the touches left after the release are dragged")

        // Release the rest of the touches.
        touch2.up()
        touch5.up()

        waitForIdle()
        assertTouchesDown(0, "all the touches are released")
    }
}
