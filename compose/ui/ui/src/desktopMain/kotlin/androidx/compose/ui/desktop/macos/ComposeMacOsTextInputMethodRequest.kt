/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.substring
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/**
 * Adapts Compose's generic [PlatformTextInputMethodRequest] (produced by `BasicTextField`) to the
 * macOS-specific [PlatformTextInputMethodRequestMacOs] consumed by [MacOsTextInputSessionOwner].
 *
 * This bridges the foundation text field, which produces the generic request, to the macOS
 * NSTextInputClient-shaped protocol. Coordinates are produced relative to the window content
 * (root); [MacOsTextInputSessionOwner.toTextInputClient] converts them to screen coordinates by
 * adding the window's content origin.
 *
 * Thread confinement: all callbacks are invoked from [MacOsTextInputSessionOwner.toTextInputClient]
 * inside `scene.withPreparedMainThread { }`, so this adapter must not wrap them again.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun PlatformTextInputMethodRequest.toMacOsRequest(
    density: () -> Density,
): PlatformTextInputMethodRequestMacOs {
    val request = this
    return object : PlatformTextInputMethodRequestMacOs {
        override fun hasMarkedText(): Boolean = request.state.composition != null

        override fun markedRange(): TextRange? = request.state.composition

        override fun selectedRange(): TextRange = request.state.selection

        override fun insertText(text: String, replacementRange: TextRange?) {
            request.editText {
                if (replacementRange != null) {
                    setSelection(replacementRange)
                }
                commitText(text, 1)
            }
        }

        override fun doCommand(command: String): Boolean = false

        override fun unmarkText() {
            request.editText { finishComposingText() }
        }

        override fun setMarkedText(
            text: String,
            selectedRange: TextRange?,
            replacementRange: TextRange?,
        ) {
            request.editText {
                val potentialReplacementRange =
                    request.state.let { it.composition ?: it.selection }
                val effectiveStart = replacementRange?.start ?: potentialReplacementRange.start
                val effectiveEnd = replacementRange?.end ?: potentialReplacementRange.end
                setComposition(TextRange(effectiveStart, effectiveEnd))
                setComposingText(text, 1)
                selectedRange?.let {
                    val start = effectiveStart + it.start
                    setSelection(TextRange(start, start + it.length))
                }
            }
        }

        override fun attributedStringForRange(
            range: TextRange,
        ): PlatformTextInputMethodRequestMacOs.StringAndRange {
            val text = request.state
            if (range.start >= text.length) {
                return PlatformTextInputMethodRequestMacOs.StringAndRange(null, null)
            }
            val adjustedEnd = range.end.coerceAtMost(text.length)
            val adjustedRange = TextRange(range.start, adjustedEnd)
            return PlatformTextInputMethodRequestMacOs.StringAndRange(
                text.substring(adjustedRange),
                adjustedRange.takeIf { adjustedEnd != range.end },
            )
        }

        override fun firstRectForCharacterRange(
            range: TextRange,
        ): PlatformTextInputMethodRequestMacOs.RectAndRange {
            val (firstTextRange, firstRect) = request.firstTextRangeAndRectInRoot(range)
            val d = density()
            val rect = with(d) {
                DpRect(
                    left = firstRect.left.toDp(),
                    top = firstRect.top.toDp(),
                    right = firstRect.right.toDp(),
                    bottom = firstRect.bottom.toDp(),
                )
            }
            return PlatformTextInputMethodRequestMacOs.RectAndRange(
                rect,
                firstTextRange.takeIf { it != range },
            )
        }

        override fun characterIndexForPoint(point: DpOffset): Long? {
            val d = density()
            val offset = with(d) { Offset(point.x.toPx(), point.y.toPx()) }
            return request.characterIndexAtOffsetInRoot(offset).toLong()
        }
    }
}
