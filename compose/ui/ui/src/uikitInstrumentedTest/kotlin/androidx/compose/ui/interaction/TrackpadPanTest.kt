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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.endScroll
import androidx.compose.ui.test.utils.scrollBy
import androidx.compose.ui.test.utils.scrollEventAt
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

internal class TrackpadPanTest {

    @Test
    fun testPointerInputReceivesTrackpadPan() = runUIKitInstrumentedTest {
        val panStartCount = mutableStateOf(0)
        val panMoveCount = mutableStateOf(0)
        val panEndCount = mutableStateOf(0)
        var totalPan = Offset.Zero

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                println(
                                    ">>> pointerInput got ${event.type}" +
                                        " changes=${event.changes.size}" +
                                        " | ${event.changes.map { it.panOffset }}"
                                )
                                when (event.type) {
                                    PointerEventType.PanStart -> panStartCount.value++
                                    PointerEventType.PanMove -> {
                                        panMoveCount.value++
                                        totalPan = event.changes.fold(totalPan) { acc, c ->
                                            acc + c.panOffset
                                        }
                                    }
                                    PointerEventType.PanEnd -> panEndCount.value++
                                }
                            }
                        }
                    }
            )
        }

        val panDx = 120.dp
        val panDy = 0.dp
        val steps = 8
        val stepInterval = 16.milliseconds
        val perStepDelta = DpOffset(panDx / steps.toFloat(), panDy / steps.toFloat())
        val center = DpOffset(screenSize.width / 2, screenSize.height / 2)
        val window = appDelegate.window()
        assertNotNull(window, "Host window must exist")

        val scrollEvent = window.scrollEventAt(location = center, delta = perStepDelta)

        // 2. Emit UIScrollPhaseChanged events for each subsequent step.
        repeat(steps - 1) {
            UIKitInstrumentedTest.delay(stepInterval.inWholeMilliseconds)
            scrollEvent.scrollBy(delta = perStepDelta, window = window)
        }

        // 3. Close the session — UIScrollPhaseEnded.
        UIKitInstrumentedTest.delay(stepInterval.inWholeMilliseconds)
        scrollEvent.endScroll(window = window)

        waitForIdle()

        assertTrue(
            panStartCount.value >= 1,
            "Expected at least one PanStart, received ${panStartCount.value}"
        )
        assertTrue(
            panMoveCount.value >= 1,
            "Expected at least one PanMove, received ${panMoveCount.value}"
        )
        assertTrue(
            panEndCount.value >= 1,
            "Expected at least one PanEnd, received ${panEndCount.value}"
        )

        val expectedTotalPxAbs = panDx.value * density.density
        assertTrue(
            totalPan.value.getDistance() > expectedTotalPxAbs / 2,
            "Accumulated pan offset (${totalPan.value}) should be in the ballpark of " +
                "the simulated delta (~${expectedTotalPxAbs}px along X)."
        )
    }
}
