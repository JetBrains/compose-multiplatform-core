/*
 * Copyright 2023 The Android Open Source Project
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.uikit.utils.CMPTextInputStringTokenizer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.asCGRect
import kotlinx.cinterop.CValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.BreakIterator
import platform.CoreGraphics.CGRect
import platform.UIKit.NSWritingDirection
import platform.UIKit.NSWritingDirectionNatural
import platform.UIKit.UIResponder
import platform.UIKit.UITextDirection
import platform.UIKit.UITextGranularity
import platform.UIKit.UITextLayoutDirection
import platform.UIKit.UITextLayoutDirectionDown
import platform.UIKit.UITextLayoutDirectionLeft
import platform.UIKit.UITextLayoutDirectionRight
import platform.UIKit.UITextLayoutDirectionUp
import platform.UIKit.UITextPosition
import platform.UIKit.UITextRange
import platform.UIKit.UITextSelectionRect
import platform.UIKit.UITextStorageDirectionForward
import platform.UIKit.UITextWritingDirection

internal interface TextInputDelegate {

    fun onResignFocus()

    fun beginFloatingCursor(offset: DpOffset)

    fun updateFloatingCursor(offset: DpOffset)

    fun endFloatingCursor()

    /**
     * Delays all edit commands until [endEditBatch] is being called.
     */
    fun beginEditBatch()

    /**
     * Performs all editing commands, starting from the [beginEditBatch] call.
     */
    fun endEditBatch()

    /**
     * A Boolean value that indicates whether the text-entry object has any text.
     * https://developer.apple.com/documentation/uikit/uikeyinput/1614457-hastext
     */
    fun hasText(): Boolean

    /**
     * Inserts a character into the displayed text.
     * Add the character text to your class’s backing store at the index corresponding to the cursor and redisplay the text.
     * https://developer.apple.com/documentation/uikit/uikeyinput/1614543-inserttext
     * @param text A string object representing the character typed on the system keyboard.
     */
    fun insertText(text: String)

    /**
     * Deletes a character from the displayed text.
     * Remove the character just before the cursor from your class’s backing store and redisplay the text.
     * https://developer.apple.com/documentation/uikit/uikeyinput/1614572-deletebackward
     */
    fun deleteBackward()

    /**
     * The text position for the end of a document.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614555-endofdocument
     */
    fun endOfDocument(): Int

    /**
     * The range of selected text in a document.
     * If the text range has a length, it indicates the currently selected text.
     * If it has zero length, it indicates the caret (insertion point).
     * If the text-range object is nil, it indicates that there is no current selection.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614541-selectedtextrange
     */
    fun getSelectedTextRange(): TextRange?

    fun setSelectedTextRange(range: TextRange?)

    fun selectAll()

    /**
     * Returns the text in the specified range.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614527-text
     * @param range A range of text in a document.
     * @return A substring of a document that falls within the specified range.
     */
    fun textInRange(range: TextRange): String?

    /**
     * Replaces the text in a document that is in the specified range.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614558-replace
     * @param range A range of text in a document.
     * @param text A string to replace the text in range.
     */
    fun replaceRange(range: TextRange, text: String)

    /**
     * Inserts the provided text and marks it to indicate that it is part of an active input session.
     * Setting marked text either replaces the existing marked text or,
     * if none is present, inserts it in place of the current selection.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614465-setmarkedtext
     * @param markedText The text to be marked.
     * @param selectedRange A range within markedText that indicates the current selection.
     * This range is always relative to markedText.
     */
    fun setMarkedText(markedText: String?, selectedRange: TextRange)

    /**
     * The range of currently marked text in a document.
     * If there is no marked text, the value of the property is nil.
     * Marked text is provisionally inserted text that requires user confirmation;
     * it occurs in multistage text input.
     * The current selection, which can be a caret or an extended range, always occurs within the marked text.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614489-markedtextrange
     */
    fun markedTextRange(): TextRange?

    /**
     * Unmarks the currently marked text.
     * After this method is called, the value of markedTextRange is nil.
     * https://developer.apple.com/documentation/uikit/uitextinput/1614512-unmarktext
     */
    fun unmarkText()

    /**
     * Returns the text position at a specified offset from another text position.
     * Returned value must be in range between 0 and length of text (inclusive).
     */
    fun positionFromPosition(position: Int, offset: Int): Int?

    /**
     * Returns the text position at a specified offset from another text position.
     * Returned value must be in range between 0 and length of the text (inclusive).
     */
    fun verticalPositionFromPosition(position: Int, verticalOffset: Int): Int?

}

/**
 * Extension of [TextInputDelegate] for the Native iOS Text Input path.
 */
internal interface NativeTextInputDelegate : TextInputDelegate {

    /**
     * Returns the caret rectangle for a given text position.
     * https://developer.apple.com/documentation/uikit/uitextinput/caretrect(for:)
     * @param position A text position within the document.
     * @return A rectangle, in dp, that encloses the caret at the specified position, or `null`
     * if the position is invalid.
     */
    fun caretDpRectForPosition(position: Int): DpRect?

    /**
     * Returns the selection rectangles that enclose a range of text.
     * https://developer.apple.com/documentation/uikit/uitextinput/selectionrects(for:)
     * @param range A range of text in the document.
     * @return A list of rectangles, in dp, that tightly bound the visual selection for the range.
     */
    fun selectionDpRectsForRange(range: TextRange): List<TextSelectionRect>

    /**
     * Returns the first rectangle that encloses a range of text.
     * https://developer.apple.com/documentation/uikit/uitextinput/firstrect(for:)
     * @param range A range of text in the document.
     * @return The first selection rectangle, in dp, or `null` if the range is invalid or empty.
     */
    fun firstSelectionRectForRange(range: TextRange): DpRect?

    /**
     * Returns the text position that is closest to the specified point.
     * https://developer.apple.com/documentation/uikit/uitextinput/closestposition(to:)
     * @param point A point, in dp, in the coordinate space of the text input.
     * @return The position closest to the point, or `null` if none can be determined.
     */
    fun closestPositionToPoint(point: DpOffset): Int?

    /**
     * Returns the text position that is closest to the specified point within range.
     * https://developer.apple.com/documentation/uikit/uitextinput/closestposition(to:within:)
     * @param point A point, in dp, in the coordinate space of the text input.
     * @param withinRange A range that limits the returned position.
     * @return The closest position within the given range, or `null` if none exists.
     */
    fun closestPositionToPoint(point: DpOffset, withinRange: TextRange): Int?

    /**
     * Returns the character range at the specified dp point.
     * https://developer.apple.com/documentation/uikit/uitextinput/characterrange(at:)
     * @param point A point, in dp, in the coordinate space of the text input.
     * @return The range of the character at the point, or `null` if none.
     */
    fun characterRangeAtPoint(point: DpOffset): TextRange?

    /**
     * Returns the position in a specified direction that is farthest within a given range.
     * https://developer.apple.com/documentation/uikit/uitextinput/position(within:farthestin:)
     * @param range The limiting range.
     * @param farthestInDirection A direction constant (left, right, up, or down).
     * @return The farthest position within the range in the given direction, or `null` if none.
     */
    fun positionWithinRange(range: TextRange, farthestInDirection: PlatformTextLayoutDirection): Int?
}

internal fun TextInputDelegate.withDeferredEditBatch(
    withScope: CoroutineScope,
    update: TextInputDelegate.() -> Unit
) {
    beginEditBatch()
    update()
    withScope.launch {
        endEditBatch()
    }
}

internal class IntermediateTextPosition(val position: Int = 0) : UITextPosition() {
    override fun description(): String {
        return "IntermediateTextPosition($position)"
    }

    init {
        assert(position >= 0) { "position should be >= 0" }
    }
}

internal fun IntermediateTextRange(start: Int, end: Int) =
    IntermediateTextRange(
        _start = IntermediateTextPosition(start),
        _end = IntermediateTextPosition(end)
    )

internal class IntermediateTextRange(
    val _start: IntermediateTextPosition,
    val _end: IntermediateTextPosition
) : UITextRange() {
    override fun isEmpty() = (_end.position - _start.position) <= 0
    override fun start(): UITextPosition = _start
    override fun end(): UITextPosition = _end

    override fun description(): String {
        return "IntermediateTextRange(start=$_start, end=$_end)"
    }
}

// Despite UITextRange being declared as non-null, iOS can still pass null to methods that take a UITextRange parameter.
internal fun UITextRange.toTextRange(): TextRange? {
    val start = (start() as? IntermediateTextPosition)?.position ?: return null
    val end = (end() as? IntermediateTextPosition)?.position ?: return null
    return TextRange(start, end)
}

internal fun TextRange.toUITextRange(): UITextRange =
    IntermediateTextRange(start = start, end = end)

internal class IntermediateTextSelectionRect(
    private var _rect: CValue<CGRect>,
    private val _writingDirection: UITextWritingDirection,
    private val _containsStart: Boolean,
    private val _containsEnd: Boolean,
    private val _isVertical: Boolean

) : UITextSelectionRect() {
    constructor(textSelectionRect: TextSelectionRect) : this(
        textSelectionRect.dpRect.asCGRect(),
        NSWritingDirectionNatural,
        textSelectionRect.containsStart,
        textSelectionRect.containsEnd,
        textSelectionRect.isVertical
    )

    override fun rect(): CValue<CGRect> = _rect
    override fun writingDirection(): NSWritingDirection = _writingDirection
    override fun containsStart(): Boolean = _containsStart
    override fun containsEnd(): Boolean = _containsEnd
    override fun isVertical(): Boolean = _isVertical
}

internal class IntermediateTextTokenizer(
    textInput: UIResponder,
    val getString: () -> String?
): CMPTextInputStringTokenizer(textInput) {
    private val newLineCharacters = setOf('\n', '\r', '\u2029')

    override fun positionFromPosition(
        position: UITextPosition,
        toBoundary: UITextGranularity,
        inDirection: UITextDirection
    ): UITextPosition? {
        val textPosition = position as? IntermediateTextPosition ?: return null
        val isForward = inDirection == UITextStorageDirectionForward ||
            inDirection == UITextLayoutDirectionRight ||
            inDirection == UITextLayoutDirectionDown

        val iterator = when (toBoundary) {
            UITextGranularity.UITextGranularityCharacter -> BreakIterator.makeCharacterInstance()
            UITextGranularity.UITextGranularityWord -> BreakIterator.makeWordInstance()
            UITextGranularity.UITextGranularitySentence -> BreakIterator.makeSentenceInstance()
            UITextGranularity.UITextGranularityLine -> BreakIterator.makeLineInstance()
            UITextGranularity.UITextGranularityParagraph ->
                return positionFromPositionToParagraphBoundary(position, isForward)

            else -> return super.positionFromPosition(position, toBoundary, inDirection)
        }

        val string = getString() ?: ""
        iterator.setText(string)

        val iteratorResult = if (isForward) {
            if (textPosition.position >= string.length - 1) {
                string.length
            } else {
                iterator.following(textPosition.position)
            }
        } else {
            if (textPosition.position <= 0) {
                0
            } else {
                iterator.preceding(textPosition.position)
            }
        }

        return IntermediateTextPosition(iteratorResult)
    }

    override fun isPositionAtBoundary(
        position: UITextPosition,
        atBoundary: UITextGranularity,
        inDirection: UITextDirection
    ): Boolean {
        val textPosition = position as? IntermediateTextPosition ?: return false

        val iterator = when (atBoundary) {
            UITextGranularity.UITextGranularityCharacter -> BreakIterator.makeCharacterInstance()
            UITextGranularity.UITextGranularityWord -> BreakIterator.makeWordInstance()
            UITextGranularity.UITextGranularitySentence -> BreakIterator.makeSentenceInstance()
            UITextGranularity.UITextGranularityLine -> BreakIterator.makeLineInstance()
            UITextGranularity.UITextGranularityParagraph -> {
                return isAtParagraphBoundary(getString() ?: "", textPosition.position)
            }
            else -> return super.isPositionAtBoundary(position, atBoundary, inDirection)
        }

        iterator.setText(getString() ?: "")
        return iterator.isBoundary(textPosition.position)
    }

    private fun positionFromPositionToParagraphBoundary(
        position: UITextPosition,
        isForward: Boolean
    ): UITextPosition? {
        val textPosition = position as? IntermediateTextPosition ?: return null

        val string = getString() ?: ""
        var location = textPosition.position
        while (isForward && location < string.length || !isForward && location > 0) {
            if (isForward) {
                if (string[location] in newLineCharacters) {
                    break
                }
                location++
            } else {
                if (string[location] in newLineCharacters) {
                    location++
                    break
                }
                location--
            }
        }
        return IntermediateTextPosition(location)
    }

    private fun isAtParagraphBoundary(text: String, position: Int): Boolean {
        if (position == 0 || position == text.length) return true
        return text[position] in newLineCharacters || text[position - 1] in newLineCharacters
    }
}

internal data class TextSelectionRect(
    val dpRect: DpRect,
    val writingDirection: TextDirection,
    val containsStart: Boolean,
    val containsEnd: Boolean,
    val isVertical: Boolean
)

// Kotlin wrapper for UITextLayoutDirection
internal enum class PlatformTextLayoutDirection(val platform: UITextLayoutDirection) {
    Left(UITextLayoutDirectionLeft),
    Right(UITextLayoutDirectionRight),
    Up(UITextLayoutDirectionUp),
    Down(UITextLayoutDirectionDown);

    companion object {
        operator fun invoke(platform: UITextLayoutDirection): PlatformTextLayoutDirection? {
            return entries.find { it.platform == platform }
        }
    }
}

// Insets in DP
internal data class DpInsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)
