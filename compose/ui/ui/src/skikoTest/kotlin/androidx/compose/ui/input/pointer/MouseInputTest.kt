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

package androidx.compose.ui.input.pointer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class MouseInputTest {
    private fun Modifier.dragWithNoMoveWorkaround(
        onDrag: (PointerInputChange) -> Unit
    ) = this
        .pointerInput(onDrag) {
            awaitPointerEventScope {
                val press = awaitFirstDown()
                val pointerId = press.id
                drag(pointerId) { change ->
                    onDrag(change)
                    change.consume()
                }
            }
        }

    // Verify that only move events are consumed during drag
    @Test
    fun scrollDuringDragIsNotConsumed() = androidx.compose.ui.test.v2.runComposeUiTest {
        var scrollEventReceived = false
        setContent {
            Box(
                modifier = Modifier
                    .testTag("box")
                    .size(100.dp)
                    .onPointerEvent(PointerEventType.Scroll) {
                        scrollEventReceived = true
                    }
                    .dragWithNoMoveWorkaround { }
            )
        }

        onNodeWithTag("box").performMouseInput {
            press()
            moveBy(Offset(0f, 20f))
            scroll(Offset(0f, 20f))
            release()
        }

        assertTrue(scrollEventReceived)
    }

    // Verify that moving the underlying element during a drag gesture causes a drag event to be
    // detected.
    @Test
    fun dragCalledWhenElementIsMoved() = androidx.compose.ui.test.v2.runComposeUiTest {
        var pointerPosition = Offset.Unspecified
        val scrollState = ScrollState(0)
        setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .testTag("box")
                        .fillMaxWidth()
                        .height(200.dp)
                        .dragWithNoMoveWorkaround { change ->
                            pointerPosition = change.position
                        }
                )
            }
        }

        onNodeWithTag("box").performMouseInput {
            moveTo(Offset(0f, 0f))
            press()
        }

        scrollState.scrollTo(100)
        awaitIdle()

        onNodeWithTag("box").performMouseInput {
            release()
        }

        assertEquals(Offset(0f, 100f), pointerPosition)
    }

    // Verify that a click (press and release without moving) does not trigger a drag event.
    // See https://youtrack.jetbrains.com/issue/CMP-10994
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun clickDoesNotTriggerDrag() = androidx.compose.ui.test.v2.runComposeUiTest {
        var dragDetected = false
        var state by mutableIntStateOf(0)
        setContent {
            Box(
                modifier = Modifier
                    .testTag("box")
                    .layout { measurable, constraints ->
                        state
                        val placeable = measurable.measure(constraints)
                        layout(100, 100) {
                            placeable.place(0, 0)
                        }
                    }
                    .size(100.dp)
                    .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                        state += 1  // Cause measureAndLayout -> synthetic move after the press
                    }
                    .dragWithNoMoveWorkaround {
                        dragDetected = true
                    }
            )
        }

        onNodeWithTag("box").performMouseInput {
            // Without moveTo first, the extra synthetic enter event causes the test to pass even
            // without the fix
            moveTo(center)
            click(center)
        }

        assertFalse(dragDetected, "Drag (incorrectly) triggered for click")
    }
}