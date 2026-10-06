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

package androidx.compose.foundation.text.selection

import androidx.compose.ui.text.TextRange
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.NaturalLanguage.NLTokenUnit
import platform.NaturalLanguage.NLTokenizer

internal val IosWordSelectionAdjustment = SelectionAdjustment { layout ->
    // Keep slot and crossed-handle handling in sync with adjustToBoundaries.
    val crossed = layout.crossStatus == CrossStatus.CROSSED
    Selection(
        start =
            layout.startInfo.anchorOnIosWordBoundary(
                crossed = crossed,
                isStart = true,
                slot = layout.startSlot,
            ),
        end =
            layout.endInfo.anchorOnIosWordBoundary(
                crossed = crossed,
                isStart = false,
                slot = layout.endSlot,
            ),
        handlesCrossed = crossed,
    )
}

private fun SelectableInfo.anchorOnIosWordBoundary(
    crossed: Boolean,
    isStart: Boolean,
    slot: Int,
): Selection.AnchorInfo {
    val offset = if (isStart) rawStartHandleOffset else rawEndHandleOffset

    if (slot != this.slot) {
        return anchorForOffset(offset)
    }

    val range = iosWordBoundary(inputText, offset) ?: textLayoutResult.getWordBoundary(offset)

    return anchorForOffset(if (isStart xor crossed) range.start else range.end)
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosWordBoundary(text: String, offset: Int): TextRange? {
    if (text.isEmpty()) return null

    val tokenizer = NLTokenizer(NLTokenUnit.NLTokenUnitWord)
    tokenizer.string = text
    val index = offset.coerceIn(0, text.lastIndex).toULong()

    return tokenizer.tokenRangeAtIndex(index).useContents {
        if (length == 0uL || location >= text.length.toULong()) {
            null
        } else {
            val start = location.toInt()
            val end = (location + length).coerceAtMost(text.length.toULong()).toInt()
            TextRange(start, end)
        }
    }
}
