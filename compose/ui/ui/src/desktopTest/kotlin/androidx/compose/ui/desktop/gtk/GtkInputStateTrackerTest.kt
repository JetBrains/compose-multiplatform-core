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

@file:OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * KDT gtk `Event` constructors are internal, so these tests exercise the tracker's
 * event-independent surface: the extracted scroll math and the generation-guarded post-relayout
 * synthetic refresh (ported to GTK in this workstream; the linux tracker carried it already).
 */
@Category(HeadlessTest::class)
class GtkInputStateTrackerTest {
    private val sent = mutableListOf<PointerInputEvent>()

    private fun tracker(initialMode: InputMode = InputMode.Touch) = InputStateTracker(
        inputModeManager = InputModeManagerImpl(initialMode) { true },
        sendPointerInputEvent = { event ->
            sent += event
            PointerEventResult()
        },
        sendKeyEvent = { false },
    )

    @Test
    fun smoothScrollScalesByFifteenAndDiscreteByOneHundred() {
        assertEquals(
            DpOffset(30.dp, (-15).dp),
            computeGtkScrollDelta(2f, -1f, isSmoothScroll = true, shiftPressed = false),
        )
        assertEquals(
            DpOffset(100.dp, (-200).dp),
            computeGtkScrollDelta(1f, -2f, isSmoothScroll = false, shiftPressed = false),
        )
    }

    @Test
    fun shiftSwapsTheAxes() {
        assertEquals(
            DpOffset((-15).dp, 30.dp),
            computeGtkScrollDelta(2f, -1f, isSmoothScroll = true, shiftPressed = true),
        )
    }

    @Test
    fun syntheticRefreshRequiresAKnownPointerPosition() {
        val t = tracker()
        assertNull(t.prepareSyntheticPointerEventAfterRelayoutIfNecessary())
    }

    @Test
    fun syntheticRefreshSendsAMoveInTouchModeAndAnExitInKeyboardMode() {
        val touch = tracker(initialMode = InputMode.Touch)
        touch.overridePointerStateForTest(pointerInWindow = true, pointerPosition = Offset(5f, 5f))
        assertEquals(
            PointerEventType.Move,
            touch.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!.type,
        )

        val keyboard = tracker(initialMode = InputMode.Keyboard)
        keyboard.overridePointerStateForTest(pointerInWindow = true, pointerPosition = Offset(5f, 5f))
        assertEquals(
            PointerEventType.Exit,
            keyboard.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!.type,
        )
    }

    @Test
    fun aStaleGenerationTokenIsANoOpAndACurrentOneSends() {
        val t = tracker()
        t.overridePointerStateForTest(pointerInWindow = true, pointerPosition = Offset(5f, 5f))

        val older = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!
        val newer = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!

        sent.clear()
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(older)
        assertTrue(sent.isEmpty(), "stale token must be voided by the newer prepare")

        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(newer)
        assertEquals(1, sent.size, "current token sends exactly one refresh event")
        assertEquals(PointerEventType.Move, sent.single().eventType)
    }
}
