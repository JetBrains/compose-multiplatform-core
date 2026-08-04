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

@file:OptIn(androidx.compose.ui.node.InternalCoreApi::class)

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.desktop.win32.EventHandlerResult
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Every KDT win32 pointer/key/scroll/activation `Event` constructor is Kotlin-`internal` (verified:
 * even `Event.WindowActivated`, which `javap` reports as a public JVM constructor, is `internal` in
 * the Kotlin metadata), so no tracked event can be built from test code. These tests therefore
 * exercise the tracker's event-independent surface, mirroring how the linux tracker is tested: the
 * extracted scroll math, the synthetic exit-nudge geometry, the window-activation state method
 * (extracted like linux's `updateStateForKeyboardLeave`), and the relayout-generation guard driven
 * through [InputStateTracker.overridePointerStateForTest]. Everything reachable only through
 * `updateStateAndSendEvents` needs native `Keyboard.getState()` and is compile-only until the VM pass.
 */
@Category(HeadlessTest::class)
class WindowsInputStateTrackerTest {
    private val density = Density(1f)
    private val sentPointer = mutableListOf<PointerEventType>()
    private val sentKeys = mutableListOf<KeyEvent>()

    private fun tracker(consumePointer: Boolean = false, consumeKey: Boolean = false) =
        InputStateTracker(
            inputModeManager = InputModeManagerImpl(InputMode.Keyboard) { true },
            sendPointerEvent = { eventType, _, _, _, _, _, _, _, _ ->
                sentPointer += eventType
                PointerEventResult(anyChangeConsumed = consumePointer)
            },
            sendKeyEvent = { keyEvent -> sentKeys += keyEvent; consumeKey },
        )

    // --- pure scroll math (mirrors linux computeLinuxScrollDelta extraction) ---

    @Test
    fun verticalWheelScrollsVerticallyAndIsInverted() {
        assertEquals(DpOffset(0.dp, (-120).dp), computeWindowsVerticalScrollDelta(120, shiftPressed = false))
        assertEquals(DpOffset(0.dp, 120.dp), computeWindowsVerticalScrollDelta(-120, shiftPressed = false))
    }

    @Test
    fun shiftRedirectsVerticalWheelToHorizontal() {
        assertEquals(DpOffset((-120).dp, 0.dp), computeWindowsVerticalScrollDelta(120, shiftPressed = true))
    }

    @Test
    fun horizontalWheelScrollsHorizontallyWithoutInversion() {
        assertEquals(DpOffset(120.dp, 0.dp), computeWindowsHorizontalScrollDelta(120))
        assertEquals(DpOffset((-120).dp, 0.dp), computeWindowsHorizontalScrollDelta(-120))
    }

    // --- synthetic "moved outside window" nudge geometry (win32-only, no macos/linux analogue) ---

    @Test
    fun syntheticExitContinuesTravelAtLeastTheMinimumNudge() {
        // travel of 5px is shorter than the 32px nudge, so it is extended to the full 32px.
        assertEquals(
            Offset(42f, 5f),
            computeSyntheticExitPosition(
                exitPosition = Offset(10f, 5f),
                lastPositionInWindow = Offset(5f, 5f),
                minNudgePx = 32f,
            ),
        )
    }

    @Test
    fun syntheticExitUsesFullTravelWhenItExceedsTheNudge() {
        assertEquals(
            Offset(200f, 5f),
            computeSyntheticExitPosition(
                exitPosition = Offset(100f, 5f),
                lastPositionInWindow = Offset(0f, 5f),
                minNudgePx = 32f,
            ),
        )
    }

    @Test
    fun syntheticExitWithoutTravelVectorPushesAboveTheTopEdge() {
        assertEquals(
            Offset(10f, -32f),
            computeSyntheticExitPosition(
                exitPosition = Offset(10f, 10f),
                lastPositionInWindow = Offset(10f, 10f),
                minNudgePx = 32f,
            ),
        )
        assertEquals(
            Offset(10f, -32f),
            computeSyntheticExitPosition(
                exitPosition = Offset(10f, 10f),
                lastPositionInWindow = null,
                minNudgePx = 32f,
            ),
        )
    }

    // --- window-activation state (extracted like linux's updateStateForKeyboardLeave) ---

    @Test
    fun windowDeactivationClearsStuckModifiers() {
        val t = tracker()
        t.keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = true)
        assertTrue(t.keyboardModifiers.isShiftPressed)

        val result = t.updateStateForWindowActivated(active = false)

        assertEquals(EventHandlerResult.Continue, result)
        assertFalse(t.keyboardModifiers.isShiftPressed, "losing activation must release stuck modifiers")
        assertEquals(PointerKeyboardModifiers(), t.keyboardModifiers)
        assertTrue(sentPointer.isEmpty())
        assertTrue(sentKeys.isEmpty())
    }

    // --- relayout-generation guard (driven via the test seam) ---

    @Test
    fun prepareReturnsNullWithoutAPointerPosition() {
        assertNull(tracker().prepareSyntheticPointerEventAfterRelayoutIfNecessary())
    }

    @Test
    fun syntheticRelayoutRefreshFiresWhenNothingIntervened() {
        val t = tracker()
        t.overridePointerStateForTest(pointerInWindow = true, pointerPosition = Offset(5f, 5f))
        val request = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()
        assertEquals(PointerEventType.Exit, request!!.type)
        sentPointer.clear()
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(request)
        assertEquals(listOf(PointerEventType.Exit), sentPointer)
    }

    @Test
    fun syntheticRelayoutRefreshIsVoidedWhenANewerRequestSupersedesIt() {
        val t = tracker()
        t.overridePointerStateForTest(pointerInWindow = true, pointerPosition = Offset(5f, 5f))
        val stale = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!
        // A newer prepare bumps the generation, staling the earlier request.
        val fresh = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()!!
        sentPointer.clear()
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(stale)
        assertTrue(sentPointer.isEmpty(), "stale generation must be a no-op")
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(fresh)
        assertEquals(listOf(PointerEventType.Exit), sentPointer, "the current request still fires")
    }
}
