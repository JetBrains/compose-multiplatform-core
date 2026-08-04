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

package androidx.compose.ui.desktop.linux

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * KDT linux `Event` constructors are internal, so these tests exercise the tracker's
 * event-independent surface: the extracted scroll math and the keyboard-focus modifier lifecycle
 * (AIR-5571 / cc557a8bf5daa semantics ported from Noria).
 */
@Category(HeadlessTest::class)
class LinuxInputStateTrackerTest {
    private fun tracker() = InputStateTracker(
        inputModeManager = InputModeManagerImpl(InputMode.Keyboard) { true },
        sendPointerInputEvent = { PointerEventResult() },
        sendKeyEvent = { false },
    )

    @Test
    fun discreteDetentsScrollOneHundredDpPerNotch() {
        val delta = computeLinuxScrollDelta(
            horizontalDelta = 0.0, horizontalWheelValue120 = 0,
            verticalDelta = 10.0, verticalWheelValue120 = 120,
            shiftPressed = false,
        )
        assertEquals(DpOffset(0.dp, 100.dp), delta)

        val doubleNotchBack = computeLinuxScrollDelta(
            horizontalDelta = 0.0, horizontalWheelValue120 = 0,
            verticalDelta = -20.0, verticalWheelValue120 = -240,
            shiftPressed = false,
        )
        assertEquals(DpOffset(0.dp, (-200).dp), doubleNotchBack)
    }

    @Test
    fun smoothScrollScalesByFifteenDp() {
        val delta = computeLinuxScrollDelta(
            horizontalDelta = 2.0, horizontalWheelValue120 = 0,
            verticalDelta = -1.0, verticalWheelValue120 = 0,
            shiftPressed = false,
        )
        assertEquals(DpOffset(30.dp, (-15).dp), delta)
    }

    @Test
    fun anyNonZeroWheelValueSelectsDetentMathForBothAxes() {
        // A detent on one axis routes BOTH axes through the wheelValue120 math (Noria shape).
        val delta = computeLinuxScrollDelta(
            horizontalDelta = 5.0, horizontalWheelValue120 = 0,
            verticalDelta = 10.0, verticalWheelValue120 = 120,
            shiftPressed = false,
        )
        assertEquals(DpOffset(0.dp, 100.dp), delta)
    }

    @Test
    fun shiftSwapsTheAxes() {
        val delta = computeLinuxScrollDelta(
            horizontalDelta = 0.0, horizontalWheelValue120 = 0,
            verticalDelta = 10.0, verticalWheelValue120 = 120,
            shiftPressed = true,
        )
        assertEquals(DpOffset(100.dp, 0.dp), delta)
    }

    @Test
    fun keyboardLeaveClearsModifiersOnlyWhenThePointerIsOutside() {
        val shift = PointerKeyboardModifiers(isShiftPressed = true)

        val outside = tracker()
        outside.keyboardModifiers = shift
        outside.overridePointerStateForTest(pointerInWindow = false)
        outside.updateStateForKeyboardLeave()
        assertFalse(outside.keyboardModifiers.isShiftPressed, "pointer outside: modifiers cleared")

        val inside = tracker()
        inside.keyboardModifiers = shift
        inside.overridePointerStateForTest(pointerInWindow = true)
        inside.updateStateForKeyboardLeave()
        assertTrue(inside.keyboardModifiers.isShiftPressed, "pointer inside: modifiers retained")
    }
}
