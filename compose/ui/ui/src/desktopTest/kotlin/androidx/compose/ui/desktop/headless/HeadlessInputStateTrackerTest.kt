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

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessInputStateTrackerTest {
    private val windowId = LightweightWindowId(1)
    private val density = Density(1f)
    private val sent = mutableListOf<Pair<PointerEventType, Offset>>()

    private fun tracker(sendKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false }) =
        InputStateTracker(
            inputModeManager = InputModeManagerImpl(InputMode.Keyboard) { true },
            sendPointerEvent = { eventType, position, _, _, _, _, _, _, _ ->
                sent += eventType to position
                PointerEventResult(anyChangeConsumed = false)
            },
            sendKeyEvent = sendKey,
        )

    @Test
    fun pressAtANewPositionSynthesizesAMoveFirst() {
        val t = tracker()
        t.updateStateAndSendEvents(Event.MouseEntered(windowId, DpOffset(1.dp, 1.dp), 0.0), density)
        sent.clear()
        t.updateStateAndSendEvents(
            Event.MouseDown(windowId, PointerButton.Primary, DpOffset(10.dp, 20.dp), 1.0), density,
        )
        assertEquals(PointerEventType.Move, sent[0].first)
        assertEquals(Offset(10f, 20f), sent[0].second)
        assertEquals(PointerEventType.Press, sent[1].first)
    }

    @Test
    fun mouseEventsForceTouchInputMode() {
        // Deviation from the task brief (see task-5-report.md "concerns"): this fork's
        // InputModeManagerImpl.requestInputMode only delegates to the requester and never assigns
        // `inputMode` (InputModeManager.kt), and InputModeManager.inputMode is read-only. So the
        // tracker — matching macos/Noria — can only *request* Touch; it cannot make
        // `modeManager.inputMode` become Touch from a `{ true }` requester. We therefore observe
        // the requested mode through the requester, preserving the test's intent.
        var requestedMode: InputMode? = null
        val modeManager = InputModeManagerImpl(InputMode.Keyboard) {
            requestedMode = it
            true
        }
        val t = InputStateTracker(
            inputModeManager = modeManager,
            sendPointerEvent = { _, _, _, _, _, _, _, _, _ -> PointerEventResult(anyChangeConsumed = false) },
            sendKeyEvent = { false },
        )
        t.updateStateAndSendEvents(Event.MouseEntered(windowId, DpOffset.Zero, 0.0), density)
        assertEquals(InputMode.Touch, requestedMode)
    }

    @Test
    fun syntheticRelayoutRefreshIsSkippedWhenARealEventArrivesInBetween() {
        val t = tracker()
        t.updateStateAndSendEvents(Event.MouseEntered(windowId, DpOffset(5.dp, 5.dp), 0.0), density)
        val generation = t.prepareSyntheticPointerEventAfterRelayoutIfNecessary()
        // A real event bumps the generation…
        t.updateStateAndSendEvents(Event.MouseMoved(windowId, DpOffset(6.dp, 6.dp), 1.0), density)
        sent.clear()
        // …so the stale synthetic refresh must be dropped.
        t.sendSyntheticPointerEventAfterRelayoutIfCurrent(generation!!)
        assertTrue(sent.isEmpty())
    }
}
