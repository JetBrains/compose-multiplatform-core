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

package androidx.compose.ui.text.platform

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed

/**
 * Maps offsets between the Compose text and the Skia paragraph built from it.
 *
 * [ParagraphBuilder] does not add the characters covered by a placeholder to the Skia paragraph;
 * it calls `addPlaceholder` instead, and Skia represents a placeholder as exactly one UTF-16 unit
 * (U+FFFC). Therefore, when a placeholder covers a Compose range whose length is not 1 (e.g.
 * `appendInlineContent(id, "[icon]")`), every Skia offset after that placeholder differs from the
 * corresponding Compose offset by the accumulated `length - 1` of the preceding placeholders.
 * Passing Compose offsets to Skia unconverted made hit testing, cursor placement, selection and
 * link ranges wrong after an inline item (https://youtrack.jetbrains.com/issue/CMP-9009).
 *
 * Semantics for Compose offsets inside a placeholder range `[start, end)`:
 * - [toSkia] maps `start` to the Skia offset of the placeholder. Offsets strictly inside the range
 *   map to the same Skia offset by default (cursor semantics: the caret is placed before the
 *   placeholder, like Android does for a `ReplacementSpan`). When the offset is the exclusive end
 *   of a range (`inclusiveEnd = true`) they map to the Skia offset right after the placeholder
 *   instead, so that a range covering only a part of the placeholder still includes it.
 * - [fromSkia] maps the placeholder's Skia offset to `start` and the Skia offset right after it to
 *   `end`. There are no other Skia offsets inside a placeholder, so a hit inside the placeholder
 *   resolves to its start or its end, depending on which half was hit (Android parity).
 *
 * Only placeholders whose range length differs from 1 are stored. When there are none, [create]
 * returns `null` so that the common case pays nothing but a null check.
 */
internal class PlaceholderOffsetMapping private constructor(
    /** Compose start offsets of the stored placeholders, sorted ascending. */
    private val composeStarts: IntArray,
    /** Compose end offsets (exclusive) of the stored placeholders. */
    private val composeEnds: IntArray,
    /** Skia offsets of the stored placeholders; each occupies `[skiaStart, skiaStart + 1)`. */
    private val skiaStarts: IntArray,
    /**
     * Skia offsets of every placeholder of the paragraph, including those covering a single UTF-16
     * unit, sorted ascending. Skia holds a U+FFFC at each of them whatever the Compose text says.
     */
    private val allPlaceholderSkiaStarts: IntArray,
    /** Length of the Skia paragraph text in UTF-16 units. */
    val skiaLength: Int,
) {
    /**
     * Converts a Compose [offset] to a Skia offset.
     *
     * @param inclusiveEnd `true` when [offset] is the exclusive end of a range, in which case an
     *   offset strictly inside a placeholder range maps to the Skia offset right after the
     *   placeholder so that the range includes it. Otherwise it maps to the placeholder's Skia
     *   offset. Consequently, whenever an end of a Compose range lands strictly inside a
     *   placeholder, that end snaps outwards so that the Skia range includes the whole placeholder
     *   (e.g. `[3, 5)` inside `[2, 8)` maps to the placeholder itself, and `[4, 10)` maps to the
     *   placeholder plus the text after it). A range ending exactly at a placeholder's start
     *   excludes the placeholder.
     */
    fun toSkia(offset: Int, inclusiveEnd: Boolean = false): Int {
        val i = lastIndexAtMost(composeStarts, offset)
        if (i < 0) return offset
        val skiaStart = skiaStarts[i]
        return when {
            // After the placeholder: shift by the delta accumulated up to and including it.
            offset >= composeEnds[i] -> offset - deltaAfter(i)
            offset == composeStarts[i] -> skiaStart
            inclusiveEnd -> skiaStart + 1
            else -> skiaStart
        }
    }

    /** Converts a Skia [skiaOffset] to a Compose offset. */
    fun fromSkia(skiaOffset: Int): Int {
        val i = lastIndexAtMost(skiaStarts, skiaOffset)
        if (i < 0) return skiaOffset
        return if (skiaOffset == skiaStarts[i]) composeStarts[i] else skiaOffset + deltaAfter(i)
    }

    /**
     * Returns `true` if the Skia paragraph holds a placeholder (a U+FFFC) at [skiaOffset], whatever
     * the length of the Compose range it stands for.
     */
    fun isPlaceholderStart(skiaOffset: Int): Boolean {
        val i = lastIndexAtMost(allPlaceholderSkiaStarts, skiaOffset)
        return i >= 0 && allPlaceholderSkiaStarts[i] == skiaOffset
    }

    /** `composeOffset - skiaOffset` for the offsets right after the placeholder at index [i]. */
    private fun deltaAfter(i: Int): Int = composeEnds[i] - (skiaStarts[i] + 1)

    companion object {
        /**
         * Builds a mapping for the given [placeholders] (in Compose offsets of a text of length
         * [textLength]), or returns `null` when Skia offsets coincide with Compose offsets, i.e.
         * every placeholder covers exactly one UTF-16 unit.
         *
         * Placeholder ranges are expected not to overlap; they don't need to be sorted.
         */
        fun create(
            placeholders: List<AnnotatedString.Range<Placeholder>>,
            textLength: Int,
        ): PlaceholderOffsetMapping? {
            if (!placeholders.fastAny { it.end - it.start != 1 }) return null

            val sorted = placeholders.sortedBy { it.start }
            val relevantCount = sorted.count { it.end - it.start != 1 }
            val composeStarts = IntArray(relevantCount)
            val composeEnds = IntArray(relevantCount)
            val skiaStarts = IntArray(relevantCount)
            val allPlaceholderSkiaStarts = IntArray(sorted.size)
            var relevantIndex = 0
            var delta = 0 // composeOffset - skiaOffset for offsets after the previous placeholder
            sorted.fastForEachIndexed { i, range ->
                val skiaStart = range.start - delta
                allPlaceholderSkiaStarts[i] = skiaStart
                if (range.end - range.start != 1) {
                    composeStarts[relevantIndex] = range.start
                    composeEnds[relevantIndex] = range.end
                    skiaStarts[relevantIndex] = skiaStart
                    relevantIndex++
                    delta += range.end - range.start - 1
                }
            }
            return PlaceholderOffsetMapping(
                composeStarts = composeStarts,
                composeEnds = composeEnds,
                skiaStarts = skiaStarts,
                allPlaceholderSkiaStarts = allPlaceholderSkiaStarts,
                skiaLength = textLength - delta,
            )
        }

        /** Returns the largest index `i` with `array[i] <= value`, or `-1` if there is none. */
        private fun lastIndexAtMost(array: IntArray, value: Int): Int {
            var low = 0
            var high = array.size - 1
            var result = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (array[mid] <= value) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }
    }
}
