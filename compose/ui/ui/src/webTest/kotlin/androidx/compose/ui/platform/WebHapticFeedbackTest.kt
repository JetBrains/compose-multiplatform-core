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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebHapticFeedbackTest : OnCanvasTests {

    @Test
    fun composeWindowProvidesWebHapticFeedback() = runApplicationTest {
        var hapticFeedback: WebHapticFeedback? = null

        createComposeWindow {
            hapticFeedback = LocalHapticFeedback.current as? WebHapticFeedback
        }

        assertNotNull(hapticFeedback, "LocalHapticFeedback should provide WebHapticFeedback")
        val pattern = hapticFeedback!!.vibrationPatternFor(HapticFeedbackType.Confirm)

        assertNotNull(pattern, "pattern should not be null")

        // We can't verify the vibration has been performed,
        // so just call performHapticFeedback to check that it doesn't fail
        hapticFeedback!!.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}