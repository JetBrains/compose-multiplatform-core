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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TrackpadPanTest {

    @Test
    fun testPointerInputReceivesTrackpadPan() = runUIKitInstrumentedTest {
        var panStartCount = 0
        var panMoveCount = 0
        var panEndCount = 0
        var totalPan = Offset.Zero

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                                when (event.type) {
                                    PointerEventType.PanStart -> panStartCount++
                                    PointerEventType.PanMove -> {
                                        panMoveCount++
                                        totalPan = event.changes.fold(totalPan) { acc, c ->
                                            acc + c.panOffset
                                        }
                                    }

                                    PointerEventType.PanEnd -> panEndCount++
                                }
                            }
                        }
                    }
            )
        }

        trackpadPan(screenSize.center, 120.dp, dy = 75.dp)

        assertEquals(1, panStartCount)
        assertTrue(
            panMoveCount >= 1,
            "Expected at least one PanMove, received ${panMoveCount}"
        )
        assertEquals(1, panEndCount)

        val totalPanDp = totalPan.toDpOffset(density)
        assertEquals(120.dp.value, totalPanDp.x.value, absoluteTolerance = 0.5f)
        assertEquals(75.dp.value, totalPanDp.y.value, absoluteTolerance = 0.5f)
    }

    @Test
    fun testVerticalScroll() = runUIKitInstrumentedTest {
        val state = ScrollState(0)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(state)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Red)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenSize.height)
                        .background(Color.Blue)
                )
            }
        }

        trackpadPan(screenSize.center, 0.dp, dy = 75.dp)
        waitForIdle()
        assertEquals(with(density) { 75.dp.roundToPx() }, state.value)

        trackpadPan(screenSize.center, 0.dp, dy = (-75).dp)
        waitForIdle()
        assertEquals(0, state.value)
    }

    @Test
    fun testHorizontalScroll() = runUIKitInstrumentedTest {
        val state = ScrollState(0)

        setContent {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(state)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .background(Color.Red)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(screenSize.width)
                        .background(Color.Blue)
                )
            }
        }

        trackpadPan(screenSize.center, dx = 75.dp)
        waitForIdle()
        assertEquals(with(density) { 75.dp.roundToPx() }, state.value)

        trackpadPan(screenSize.center, dx = (-75).dp)
        waitForIdle()
        assertEquals(0, state.value)
    }

    @Test
    fun testPointerInputReceivesMultipleTrackpadPans() = runUIKitInstrumentedTest {
        var panStartCount = 0
        var panMoveCount = 0
        var panEndCount = 0
        var totalPan = Offset.Zero

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                                when (event.type) {
                                    PointerEventType.PanStart -> panStartCount++
                                    PointerEventType.PanMove -> {
                                        panMoveCount++
                                        totalPan = event.changes.fold(totalPan) { acc, c ->
                                            acc + c.panOffset
                                        }
                                    }

                                    PointerEventType.PanEnd -> panEndCount++
                                }
                            }
                        }
                    }
            )
        }

        val deltas = listOf(
            120.dp to 75.dp,
            (-50).dp to 30.dp,
            0.dp to (-90).dp,
        )
        for ((dx, dy) in deltas) {
            trackpadPan(screenSize.center, dx = dx, dy = dy)
        }
        waitForIdle()

        assertEquals(deltas.size, panStartCount)
        assertEquals(deltas.size, panEndCount)
        assertTrue(
            panMoveCount >= deltas.size,
            "Expected at least ${deltas.size} PanMove events, received $panMoveCount"
        )

        val expectedDx = deltas.sumOf { it.first.value.toDouble() }.toFloat()
        val expectedDy = deltas.sumOf { it.second.value.toDouble() }.toFloat()
        val totalPanDp = totalPan.toDpOffset(density)
        assertEquals(expectedDx, totalPanDp.x.value, absoluteTolerance = 0.5f)
        assertEquals(expectedDy, totalPanDp.y.value, absoluteTolerance = 0.5f)
    }
}