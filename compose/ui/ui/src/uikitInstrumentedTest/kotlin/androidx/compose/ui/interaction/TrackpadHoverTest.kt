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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.endHover
import androidx.compose.ui.test.utils.hoverEventAt
import androidx.compose.ui.test.utils.hoverTo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TrackpadHoverTest {

    @Test
    fun testBoxReceivesHoverEnterMoveExitForMultipleGestures() = runUIKitInstrumentedTest {
        var enterCount = 0
        var moveCount = 0
        var exitCount = 0

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Enter -> enterCount++
                                    PointerEventType.Move -> moveCount++
                                    PointerEventType.Exit -> exitCount++
                                }
                            }
                        }
                    }
            )
        }

        val window = appDelegate.window()!!
        val center = screenSize.center

        run {
            val hover = window.hoverEventAt(center)
            hover.hoverTo(DpOffset(center.x + 30.dp, center.y), window)
            hover.hoverTo(DpOffset(center.x + 60.dp, center.y + 20.dp), window)
            hover.endHover(window)
        }
        waitForIdle()

        run {
            val hover = window.hoverEventAt(DpOffset(center.x - 50.dp, center.y - 50.dp))
            hover.hoverTo(DpOffset(center.x - 20.dp, center.y - 20.dp), window)
            hover.endHover(window)
        }
        waitForIdle()

        assertEquals(2, enterCount, "Expected 2 Enter events for 2 gestures, got $enterCount")
        assertTrue(moveCount >= 3, "Expected at least 3 Move events, got $moveCount")
        assertEquals(2, exitCount, "Expected 2 Exit events for 2 gestures, got $exitCount")
    }

    @Test
    fun testButtonHover_outside_in_out() = runUIKitInstrumentedTest {
        val (buttonCenter, isHovered) = setUpHoverableBoxAtScreenCenter()
        val window = appDelegate.window()!!
        val outside = outsideButton(buttonCenter)

        val hover = window.hoverEventAt(outside)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after begin outside")

        hover.hoverTo(buttonCenter, window)
        waitForIdle()
        assertTrue(isHovered(), "Button should be hovered after move over it")

        hover.hoverTo(outside, window)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after move out")

        hover.endHover(window)
    }

    @Test
    fun testButtonHover_outside_in_end() = runUIKitInstrumentedTest {
        val (buttonCenter, isHovered) = setUpHoverableBoxAtScreenCenter()
        val window = appDelegate.window()!!
        val outside = outsideButton(buttonCenter)

        val hover = window.hoverEventAt(outside)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after begin outside")

        hover.hoverTo(buttonCenter, window)
        waitForIdle()
        assertTrue(isHovered(), "Button should be hovered after move over it")

        hover.endHover(window)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after end hover")
    }

    @Test
    fun testButtonHover_overButton_movesOut() = runUIKitInstrumentedTest {
        val (buttonCenter, isHovered) = setUpHoverableBoxAtScreenCenter()
        val window = appDelegate.window()!!
        val outside = outsideButton(buttonCenter)

        val hover = window.hoverEventAt(buttonCenter)
        waitForIdle()
        assertTrue(isHovered(), "Button should be hovered after begin over it")

        hover.hoverTo(outside, window)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after move out")

        hover.endHover(window)
    }

    @Test
    fun testButtonHover_overButton_endHover() = runUIKitInstrumentedTest {
        val (buttonCenter, isHovered) = setUpHoverableBoxAtScreenCenter()
        val window = appDelegate.window()!!

        val hover = window.hoverEventAt(buttonCenter)
        waitForIdle()
        assertTrue(isHovered(), "Button should be hovered after begin over it")

        hover.endHover(window)
        waitForIdle()
        assertFalse(isHovered(), "Button should not be hovered after end hover")
    }
}

private val ButtonSize = DpSize(120.dp, 60.dp)

private fun outsideButton(buttonCenter: DpOffset): DpOffset =
    DpOffset(buttonCenter.x + ButtonSize.width, buttonCenter.y)

private fun UIKitInstrumentedTest.setUpHoverableBoxAtScreenCenter(): Pair<DpOffset, () -> Boolean> {
    val boxCenter = screenSize.center
    val topLeft = DpOffset(
        boxCenter.x - ButtonSize.width / 2,
        boxCenter.y - ButtonSize.height / 2,
    )
    var hovered = false
    setContent {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        LaunchedEffect(isHovered) { hovered = isHovered }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(topLeft.x, topLeft.y)
                    .size(ButtonSize)
                    .background(Color.Cyan)
                    .hoverable(interactionSource)
            )
        }
    }
    return boxCenter to { hovered }
}
