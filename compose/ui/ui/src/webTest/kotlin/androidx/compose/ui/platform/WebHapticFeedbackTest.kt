@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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

package androidx.compose.ui.platform

import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class WebHapticFeedbackTest : OnCanvasTests {

    @Test
    fun composeWindowProvidesWebHapticFeedback() = runApplicationTest {
        var hapticFeedback: Any? = null

        createComposeWindow {
            hapticFeedback = LocalHapticFeedback.current
        }

        assertIs<WebHapticFeedback>(hapticFeedback)
    }

    @Test
    fun mapsConfirmToMultiPulsePattern() {
        assertPatternEquals(
            expected = listOf(18, 32, 36),
            actual = vibrationPatternFor(HapticFeedbackType.Confirm)
        )
    }

    @Test
    fun mapsRejectToErrorPattern() {
        assertPatternEquals(
            expected = listOf(18, 28, 18, 28, 18),
            actual = vibrationPatternFor(HapticFeedbackType.Reject)
        )
    }

    @Test
    fun mapsSelectionAndTextHandleTypesToShortPulse() {
        assertPatternEquals(
            expected = listOf(6),
            actual = vibrationPatternFor(HapticFeedbackType.SegmentTick)
        )
        assertPatternEquals(
            expected = listOf(6),
            actual = vibrationPatternFor(HapticFeedbackType.TextHandleMove)
        )
    }

    private fun assertPatternEquals(expected: List<Int>, actual: dynamic) {
        val actualValues = js("Array.from(actual)").unsafeCast<Array<Double>>()
        assertContentEquals(
            expected.toTypedArray(),
            actualValues.map(Double::toInt).toTypedArray()
        )
    }
}