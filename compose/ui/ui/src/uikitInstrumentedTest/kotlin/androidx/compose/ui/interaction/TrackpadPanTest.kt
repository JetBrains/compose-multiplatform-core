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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.endScroll
import androidx.compose.ui.test.utils.scrollBy
import androidx.compose.ui.test.utils.scrollEventAt
import androidx.compose.ui.touch
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
        println(">>> totalPan=$totalPanDp")
        assertEquals(120.dp.value, totalPanDp.x.value)
        assertEquals(75.dp.value, totalPanDp.y.value)
    }
}