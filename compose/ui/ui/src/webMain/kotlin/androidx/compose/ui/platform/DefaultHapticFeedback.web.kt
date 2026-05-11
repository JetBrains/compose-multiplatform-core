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

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.js
import kotlin.js.toJsArray
import kotlin.js.toJsNumber

internal class WebHapticFeedback : HapticFeedback {
    @OptIn(ExperimentalWasmJsInterop::class)
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        vibrate(vibrationPatternFor(hapticFeedbackType))
    }
}

// TODO: to eventually avoid the hardcoded values, follow the new browser API proposal https://github.com/WICG/web-haptics
// and rely on it once it's implemented
@OptIn(ExperimentalWasmJsInterop::class)
internal fun vibrationPatternFor(hapticFeedbackType: HapticFeedbackType): JsArray<JsNumber> =
    when (hapticFeedbackType) {
        HapticFeedbackType.Confirm -> vibrationPatternOf(18, 32, 36)
        HapticFeedbackType.Reject -> vibrationPatternOf(18, 28, 18, 28, 18)
        HapticFeedbackType.ContextClick,
        HapticFeedbackType.GestureEnd,
        HapticFeedbackType.GestureThresholdActivate,
        HapticFeedbackType.LongPress,
        HapticFeedbackType.ToggleOff,
        HapticFeedbackType.ToggleOn,
        HapticFeedbackType.VirtualKey -> vibrationPatternOf(12)
        HapticFeedbackType.KeyboardTap,
        HapticFeedbackType.SegmentFrequentTick,
        HapticFeedbackType.SegmentTick,
        HapticFeedbackType.TextHandleMove -> vibrationPatternOf(6)
        else -> vibrationPatternOf(12)
    }

@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrationPatternOf(vararg durations: Int): JsArray<JsNumber> =
    durations.map { it.toDouble().toJsNumber() }.toJsArray()

//language=javascript
@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrate(pattern: JsArray<JsNumber>) {
    js(
        """
        if (typeof window !== 'undefined' &&
            window.navigator != null &&
            typeof window.navigator.vibrate === 'function') {
            window.navigator.vibrate(pattern)
        }
        """
    )
}