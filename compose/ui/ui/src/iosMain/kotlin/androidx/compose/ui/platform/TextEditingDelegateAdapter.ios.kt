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

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect

/**
 * Adapts the [TextInputContainer.Delegate] of a text field to the interface the
 * [NativeTextEditingDelegate] conforming views talk to.
 */
internal class InactiveTextInputAdapter(
    private val delegate: TextInputContainer.Delegate,
) : NativeTextEditingDelegate {
    override val isInteractive: Boolean = false

    private val text: String get() = delegate.text

    override fun onResignFocus() = Unit

    override fun beginFloatingCursor(offset: DpOffset) = Unit

    override fun updateFloatingCursor(offset: DpOffset) = Unit

    override fun endFloatingCursor() = Unit

    override fun hasText(): Boolean = text.isNotEmpty()

    override fun insertText(text: String) = delegate.insertText(text)

    override fun deleteBackward() = delegate.deleteBackward()

    override fun endOfDocument(): Int = text.length

    override fun getSelectedTextRange(): TextRange = delegate.selectionTextRange

    override fun setSelectedTextRange(range: TextRange?) = delegate.setSelectedText(range)

    override fun selectAll() = delegate.setSelectedText(TextRange(0, text.length))

    override fun textInRange(range: TextRange): String? =
        text.takeIf { range.isValidIn(it.length) }?.substring(range.start, range.end)

    override fun replaceRange(range: TextRange, text: String) = delegate.replaceRange(range, text)

    override fun setMarkedText(markedText: String?, selectedRange: TextRange) =
        delegate.setMarkedText(markedText, selectedRange)

    override fun markedTextRange(): TextRange? = delegate.markedTextRange

    override fun unmarkText() = delegate.unmarkText()

    override fun positionFromPosition(position: Int, offset: Int): Int? =
        text.movePositionByGraphemes(position, offset)

    override fun verticalPositionFromPosition(position: Int, verticalOffset: Int): Int? = null

    override fun caretDpRectForPosition(position: Int): DpRect? = null

    override fun selectionDpRectsForRange(range: TextRange): List<TextInputSelectionRect> =
        emptyList()

    override fun firstSelectionRectForRange(range: TextRange): DpRect? = null

    override fun closestPositionToPoint(point: DpOffset): Int? = null

    override fun closestPositionToPoint(point: DpOffset, withinRange: TextRange): Int? = null

    override fun characterRangeAtPoint(point: DpOffset): TextRange? = null

    override val inputTraits: SkikoUITextInputTraits get() = getUITextInputTraits(delegate.imeOptions)

    override fun positionWithinRange(
        range: TextRange,
        farthestInDirection: TextLayoutDirection
    ): Int? = null
}
