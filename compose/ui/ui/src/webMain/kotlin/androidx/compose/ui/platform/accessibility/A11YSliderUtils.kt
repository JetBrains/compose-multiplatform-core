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

package androidx.compose.ui.platform.accessibility

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

internal object A11YSliderUtils {

    fun isSliderNode(semanticsNode: SemanticsNode): Boolean {
        val config = semanticsNode.config

        if (!config.contains(SemanticsActions.SetProgress) ||
            !config.contains(SemanticsProperties.ProgressBarRangeInfo)
        ) return false

        val rangeInfo = config[SemanticsProperties.ProgressBarRangeInfo]
        if (rangeInfo == ProgressBarRangeInfo.Indeterminate) {
            return false
        }

        return true
    }

    fun handleSliderKeyEvents(keyboardEvent: KeyboardEvent, semanticsNode: SemanticsNode) {
        val config = semanticsNode.config
        val rangeInfo = config[SemanticsProperties.ProgressBarRangeInfo]
        val actualSteps = if (rangeInfo.steps > 0) rangeInfo.steps + 1 else 100
        val delta = (rangeInfo.range.endInclusive - rangeInfo.range.start) / actualSteps
        val pageDelta = delta * (actualSteps / 10).coerceIn(1, 10)
        val newValue = when (keyboardEvent.key) {
            "ArrowUp", "ArrowRight" -> rangeInfo.current + delta
            "ArrowDown", "ArrowLeft" -> rangeInfo.current - delta
            "Home" -> rangeInfo.range.start
            "End" -> rangeInfo.range.endInclusive
            "PageUp" -> rangeInfo.current + pageDelta
            "PageDown" -> rangeInfo.current - pageDelta
            else -> return
        }.coerceIn(rangeInfo.range)

        if (config[SemanticsActions.SetProgress].action?.invoke(newValue) == true) {
            keyboardEvent.preventDefault()
        }
    }


    internal fun setA11YProgressBarRangeInfo(
        element: HTMLElement,
        semanticsConfiguration: SemanticsConfiguration,
    ) {
        val info = semanticsConfiguration[SemanticsProperties.ProgressBarRangeInfo]
        if (info == ProgressBarRangeInfo.Indeterminate) {
            removeA11YProgressBarRangeInfo(element)
            return
        }
        val stateDescription = semanticsConfiguration.getOrNull(SemanticsProperties.StateDescription)

        element.setAttribute("aria-valuemin", info.range.start.toString())
        element.setAttribute("aria-valuemax", info.range.endInclusive.toString())
        element.setAttribute("aria-valuenow", info.current.toString())
        if (stateDescription != null) {
            element.setAttribute("aria-valuetext", stateDescription)
        } else {
            element.removeAttribute("aria-valuetext")
        }
    }

    internal fun removeA11YProgressBarRangeInfo(element: HTMLElement) {
        element.removeAttribute("aria-valuemin")
        element.removeAttribute("aria-valuemax")
        element.removeAttribute("aria-valuenow")
        element.removeAttribute("aria-valuetext")
    }
}
