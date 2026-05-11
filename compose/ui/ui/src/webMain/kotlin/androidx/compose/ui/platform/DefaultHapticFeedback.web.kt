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

@OptIn(ExperimentalWasmJsInterop::class)
internal class WebHapticFeedback : HapticFeedback {
    // Check if API is supported before doing anything
    private val isVibrationSupported = isVibrationSupported()

    // Declare these hardcoded patterns to avoid js-interop on every call
    private val ConfirmVibrationPattern: JsArray<JsNumber> = vibrationPatternOf(18, 32, 36)
    private val RejectVibrationPattern: JsArray<JsNumber> = vibrationPatternOf(18, 28, 18, 28, 18)
    private val SinglePulseVibrationPattern: JsArray<JsNumber> = vibrationPatternOf(12)
    private val SoftTickVibrationPattern: JsArray<JsNumber> = vibrationPatternOf(6)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!isVibrationSupported) return
        val pattern = vibrationPatternFor(hapticFeedbackType) ?: return
        vibrate(pattern)
    }

    // We don't have a high-level browser API right now. So we hardcode the patterns here.
    // TODO: to eventually avoid the hardcoded values, follow the new browser API proposal https://github.com/WICG/web-haptics
    // and rely on it once it's implemented
    @OptIn(ExperimentalWasmJsInterop::class)
    internal fun vibrationPatternFor(hapticFeedbackType: HapticFeedbackType): JsArray<JsNumber>? {
        return when (hapticFeedbackType) {
            HapticFeedbackType.Confirm -> ConfirmVibrationPattern
            HapticFeedbackType.Reject -> RejectVibrationPattern
            HapticFeedbackType.ContextClick,
            HapticFeedbackType.GestureEnd,
            HapticFeedbackType.GestureThresholdActivate,
            HapticFeedbackType.LongPress,
            HapticFeedbackType.ToggleOff,
            HapticFeedbackType.ToggleOn,
            HapticFeedbackType.VirtualKey -> SinglePulseVibrationPattern
            HapticFeedbackType.KeyboardTap,
            HapticFeedbackType.SegmentFrequentTick,
            HapticFeedbackType.SegmentTick -> SoftTickVibrationPattern
            HapticFeedbackType.TextHandleMove -> null
            else -> null
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrationPatternOf(vararg durations: Int): JsArray<JsNumber> =
    durations.map { it.toDouble().toJsNumber() }.toJsArray()

@OptIn(ExperimentalWasmJsInterop::class)
private fun isVibrationSupported(): Boolean = js(
    //language=javascript
    """
        typeof window !== 'undefined' &&
        window.navigator != null &&
        typeof window.navigator.vibrate === 'function'
    """
)

//language=javascript
@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrate(pattern: JsArray<JsNumber>) {
    // Assuming the API support has been checked in advance, we can safely call it
    js("window.navigator.vibrate(pattern)")
}