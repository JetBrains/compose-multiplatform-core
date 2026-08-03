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

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class MacOsInputStateTrackerTest {
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

    private fun moved(x: Double = 5.0, y: Double = 5.0, t: Double = 1.0) =
        Event.MouseMoved(windowId = 1L, locationInWindow = LogicalPoint(x, y), timestamp = Timestamp(t))
    private fun entered(t: Double = 0.0) =
        Event.MouseEntered(windowId = 1L, locationInWindow = LogicalPoint(1.0, 1.0), timestamp = Timestamp(t))
    private fun swipe(deltaX: Double, t: Double = 2.0) = Event.Swipe(
        windowId = 1L, deltaX = deltaX, deltaY = 0.0,
        phase = org.jetbrains.desktop.macos.EventPhase.Ended,
        locationInWindow = LogicalPoint(3.0, 3.0), timestamp = Timestamp(t),
    )

    @Test
    fun mouseMovedWithoutEnterIsDroppedAndDoesNotSynthesizePresence() {
        val t = tracker()
        assertEquals(EventHandlerResult.Continue, t.updateStateAndSendEvents(moved(), density))
        assertTrue(sentPointer.isEmpty(), "occluded-window MouseMoved must not reach the scene")
        t.updateStateAndSendEvents(entered(), density)
        sentPointer.clear()
        assertEquals(EventHandlerResult.Continue, t.updateStateAndSendEvents(moved(), density))
        assertEquals(listOf(PointerEventType.Move), sentPointer)
    }

    @Test
    fun swipeWithPositiveDeltaXSynthesizesBackButtonPressReleaseWithKeyFallback() {
        val t = tracker()
        t.updateStateAndSendEvents(entered(), density)
        sentPointer.clear()
        t.updateStateAndSendEvents(swipe(deltaX = 1.0), density)
        // Move refresh, then Press, then Release (deltaX>0 == swipe left == BACK)
        assertEquals(listOf(PointerEventType.Move, PointerEventType.Press, PointerEventType.Release), sentPointer)
        assertEquals(2, sentKeys.size, "unconsumed press+release each fall back to a KeyEvent")
    }

    @Test
    fun swipeWithZeroDeltaXOnlyRefreshesPosition() {
        val t = tracker()
        t.updateStateAndSendEvents(entered(), density)
        sentPointer.clear()
        assertEquals(EventHandlerResult.Continue, t.updateStateAndSendEvents(swipe(deltaX = 0.0), density))
        assertEquals(listOf(PointerEventType.Move), sentPointer)
        assertTrue(sentKeys.isEmpty())
    }

    @Test
    fun syntheticRelayoutRefreshIsVoidedByAnyRealEventInBetween() {
        val t = tracker()
        t.updateStateAndSendEvents(entered(), density)
        val request = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()
        t.updateStateAndSendEvents(moved(), density) // bumps the generation
        sentPointer.clear()
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(request!!)
        assertTrue(sentPointer.isEmpty(), "stale generation must be a no-op")
    }

    @Test
    fun syntheticRelayoutRefreshFiresWhenNothingIntervened() {
        val t = tracker()
        t.updateStateAndSendEvents(entered(), density)
        sentPointer.clear()
        val request = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(request!!)
        assertEquals(1, sentPointer.size)
    }
}
