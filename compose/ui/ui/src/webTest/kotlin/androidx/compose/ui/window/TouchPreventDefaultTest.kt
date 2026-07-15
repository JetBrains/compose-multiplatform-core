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

package androidx.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.events.touchEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.w3c.dom.pointerevents.PointerEvent as WebPointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/**
 * Verifies the preventDefault() decisions ComposeWindow makes on the touch event stream
 * (see the touchstart/touchmove/touchend handlers in ComposeWindowInternal.web.kt).
 *
 * The browser's dispatch ordering contract is emulated manually: for each touch, the pointer
 * event is dispatched before its touch counterpart (pointerdown -> touchstart,
 * pointermove -> touchmove, pointerup -> touchend), matching de facto browser behavior.
 *
 * Limitations: synthetic events are untrusted, so the browser never performs real default
 * actions for them. These tests pin down that we make the right preventDefault() calls for a
 * given event stream; whether the browser then honors them (click suppression, focus
 * retention, gesture handover) can only be verified end-to-end on real devices.
 */
class TouchPreventDefaultTest : OnCanvasTests {

    private fun touch(id: Int, x: Int, y: Int) = PointerEventInit(
        pointerId = id,
        clientX = x,
        clientY = y,
        pointerType = "touch",
        // Trusted touch-derived pointer events are cancelable; the EventInit default is false,
        // and ComposeWindow's consume logic checks event.cancelable before preventDefault().
        cancelable = true,
    )

    @Test
    fun touchstartIsNotPrevented() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray).clickable { })
        }

        // Even though Compose consumes the pointerdown of an interactive area, touchstart must
        // never be canceled - it would block system gestures (scroll, back, pull-to-refresh)
        // before we know whether Compose handles the sequence.
        dispatchEvents(WebPointerEvent("pointerdown", touch(0, 50, 50)))
        val touchstart = touchEvent("touchstart")
        dispatchEvents(touchstart)

        assertFalse(touchstart.defaultPrevented, "touchstart should not be prevented")
    }

    @Test
    fun touchendPreventedWhenReleaseConsumed() = runApplicationTest {
        var clicksCount = 0
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray).clickable { clicksCount++ })
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 50)),
            WebPointerEvent("pointerup", touch(0, 50, 50)),
        )
        val touchend = touchEvent("touchend")
        dispatchEvents(touchend)

        // The tap was consumed, so the synthetic mouse events (incl. click) must be suppressed.
        assertTrue(touchend.defaultPrevented, "touchend should be prevented when release is consumed")

        awaitIdle()
        assertEquals(1, clicksCount, "click should have been registered")

        // The flag is single-use: a touchend with no fresh pointerup must stay untouched.
        val strayTouchend = touchEvent("touchend")
        dispatchEvents(strayTouchend)
        assertFalse(strayTouchend.defaultPrevented, "stray touchend without fresh pointerup should not be prevented")
    }

    @Test
    fun touchendNotPreventedWhenReleaseNotConsumed() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray)) // nothing consumes the tap
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 50)),
            WebPointerEvent("pointerup", touch(0, 50, 50)),
        )
        val touchend = touchEvent("touchend")
        dispatchEvents(touchend)

        // The browser keeps its default actions (compatibility mouse events, click).
        assertFalse(touchend.defaultPrevented, "touchend should not be prevented when nothing consumes the tap")
    }

    @Test
    fun nonCancelableTouchendStillResetsTheFlag() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray).clickable { })
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 50)),
            WebPointerEvent("pointerup", touch(0, 50, 50)),
        )

        // The browser dispatches touchend with cancelable = false when it is already
        // performing a default action; preventDefault() must not be attempted then.
        val nonCancelable = touchEvent("touchend", cancelable = false)
        dispatchEvents(nonCancelable)
        assertFalse(nonCancelable.defaultPrevented, "non-cancelable touchend should not be prevented")

        // The consumed-release flag must be reset even on that path: a later touchend
        // must not be canceled based on stale state.
        val nextTouchend = touchEvent("touchend")
        dispatchEvents(nextTouchend)
        assertFalse(nextTouchend.defaultPrevented, "subsequent touchend should not be prevented after flag reset")
    }

    @Test
    fun pointercancelResetsTheReleaseFlag() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray).clickable { })
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 50)),
            WebPointerEvent("pointerup", touch(0, 50, 50)),
            WebPointerEvent("pointercancel", touch(1, 50, 50)),
        )
        val touchend = touchEvent("touchend")
        dispatchEvents(touchend)

        assertFalse(touchend.defaultPrevented, "touchend should not be prevented after pointercancel resets the flag")
    }

    @Test
    fun touchmovePreventedWhenComposeScrolls() = runTest {
        createComposeWindow {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                repeat(50) {
                    Box(Modifier.fillMaxWidth().height(100.dp).background(Color.LightGray))
                }
            }
        }

        // Drag upwards: the scrollable consumes the scroll (content scrolls forward).
        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 80)),
            // first move exceeds the touch slop
            WebPointerEvent("pointermove", touch(0, 50, 60)),
            WebPointerEvent("pointermove", touch(0, 50, 30)),
        )
        val touchmove = touchEvent("touchmove")
        dispatchEvents(touchmove)

        // Scrolling happened in Compose - the browser must not take over the gesture.
        assertTrue(touchmove.defaultPrevented, "touchmove should be prevented when Compose scrolls")
    }

    @Test
    fun touchmovePreventedWhenDragConsumesMovesWithoutScroll() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray).pointerInput(Unit) {
                detectTransformGestures { _, _, _, _ -> }
            })
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 80)),
            // first move exceeds the touch slop
            WebPointerEvent("pointermove", touch(0, 50, 60)),
            WebPointerEvent("pointermove", touch(0, 50, 30)),
        )
        val touchmove = touchEvent("touchmove")
        dispatchEvents(touchmove)

        // No scroll happened, but a component (drag) consumed the moves -
        // the browser must not take over the gesture.
        assertTrue(touchmove.defaultPrevented, "touchmove should be prevented when drag consumes moves")
    }

    @Test
    fun touchmoveNotPreventedWhenNothingConsumesMoves() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().background(Color.LightGray)) // nothing consumes the moves
        }

        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 80)),
            WebPointerEvent("pointermove", touch(0, 50, 60)),
            WebPointerEvent("pointermove", touch(0, 50, 30)),
        )
        val touchmove = touchEvent("touchmove")
        dispatchEvents(touchmove)

        // The browser is free to handle the gesture.
        assertFalse(touchmove.defaultPrevented, "touchmove should not be prevented when nothing consumes moves")
    }

    @Test
    fun touchmoveNotPreventedWhenScrollingAtTheEdge() = runTest {
        createComposeWindow {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                repeat(50) {
                    Box(Modifier.fillMaxWidth().height(100.dp).background(Color.LightGray))
                }
            }
        }

        // Drag downwards while already scrolled to the top: the drag is tracked by the
        // scrollable, but no scroll distance can be consumed (nowhere to scroll).
        dispatchEvents(
            WebPointerEvent("pointerdown", touch(0, 50, 30)),
            // first move exceeds the touch slop
            WebPointerEvent("pointermove", touch(0, 50, 60)),
            WebPointerEvent("pointermove", touch(0, 50, 90)),
        )
        val touchmove = touchEvent("touchmove")
        dispatchEvents(touchmove)

        // The gesture hit the scroll edge - the browser should take it over
        // (e.g. scroll of an outer html container, pull-to-refresh).
        assertFalse(touchmove.defaultPrevented, "touchmove should not be prevented when scrolling at the edge")
    }
}
