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

package androidx.compose.ui.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.google.common.truth.FloatSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Regression tests for https://youtrack.jetbrains.com/issue/CMP-9009: a placeholder whose range
 * covers more than one character (e.g. `appendInlineContent(id, "[icon]")`) is a single U+FFFC in
 * the Skia paragraph, so every offset-based [Paragraph] API must translate between Compose and
 * Skia offsets.
 *
 * The test font has a fixed advance: every glyph is `fontSize` px wide.
 */
@RunWith(JUnit4::class)
class DesktopParagraphPlaceholderOffsetTest : SkikoComposeTestBase() {
    private val fontFamilyResolver = createFontFamilyResolver()
    private val defaultDensity = Density(density = 1f)
    private val fontFamilyMeasureFont =
        FontFamily(
            Font(
                "font_desktop/sample_font.ttf",
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
            )
        )

    private val fontSize = 10.sp
    private val glyph = 10f
    private val placeholderWidth = 30f

    // "ab[icon]cd": placeholder over "[icon]" (offsets 2..8), 'c' at 8, 'd' at 9.
    private val text = "ab[icon]cd"
    private val placeholderStart = text.indexOf('[')
    private val placeholderEnd = text.indexOf(']') + 1
    private val cIndex = placeholderEnd // 'c' right after the placeholder (not the one in "icon")

    // Layout: [a: 0..10][b: 10..20][placeholder: 20..50][c: 50..60][d: 60..70]
    private val placeholderLeft = 2 * glyph
    private val placeholderRight = placeholderLeft + placeholderWidth

    @Test
    fun getOffsetForPosition_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()

        // Before the placeholder.
        assertThat(paragraph.getOffsetForPosition(Offset(2f, 5f))).isEqualTo(0)
        assertThat(paragraph.getOffsetForPosition(Offset(8f, 5f))).isEqualTo(1)
        assertThat(paragraph.getOffsetForPosition(Offset(18f, 5f))).isEqualTo(2)

        // Inside the placeholder: start when the left half is hit, end for the right half
        // (Android ReplacementSpan parity).
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderLeft + 5f, 5f)))
            .isEqualTo(placeholderStart)
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRight - 5f, 5f)))
            .isEqualTo(placeholderEnd)

        // Right after the placeholder: 'c' (Compose offset 8, not Skia offset 3).
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRight + 2f, 5f)))
            .isEqualTo(cIndex)
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRight + glyph + 2f, 5f)))
            .isEqualTo(cIndex + 1)
        // Past the end of the line.
        assertThat(paragraph.getOffsetForPosition(Offset(200f, 5f))).isEqualTo(text.length)
    }

    @Test
    fun getCursorRect_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()

        assertThat(paragraph.getCursorRect(0).left).isEqualToWithTolerance(0f)
        assertThat(paragraph.getCursorRect(placeholderStart).left)
            .isEqualToWithTolerance(placeholderLeft)
        // Offsets strictly inside the placeholder range put the caret before the placeholder.
        assertThat(paragraph.getCursorRect(placeholderStart + 2).left)
            .isEqualToWithTolerance(placeholderLeft)
        assertThat(paragraph.getCursorRect(placeholderEnd).left)
            .isEqualToWithTolerance(placeholderRight)
        assertThat(paragraph.getCursorRect(text.length).left)
            .isEqualToWithTolerance(placeholderRight + 2 * glyph)
    }

    @Test
    fun getBoundingBox_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()
        val placeholderRect = paragraph.placeholderRects.single()!!

        assertThat(placeholderRect.left).isEqualToWithTolerance(placeholderLeft)
        assertThat(placeholderRect.right).isEqualToWithTolerance(placeholderRight)

        assertThat(paragraph.getBoundingBox(placeholderStart)).isEqualTo(placeholderRect)
        assertThat(paragraph.getBoundingBox(placeholderStart + 2)).isEqualTo(placeholderRect)

        val cBox = paragraph.getBoundingBox(cIndex)
        assertThat(cBox.left).isEqualToWithTolerance(placeholderRight)
        assertThat(cBox.right).isEqualToWithTolerance(placeholderRight + glyph)

        val dBox = paragraph.getBoundingBox(cIndex + 1)
        assertThat(dBox.left).isEqualToWithTolerance(placeholderRight + glyph)
        assertThat(dBox.right).isEqualToWithTolerance(placeholderRight + 2 * glyph)
    }

    @Test
    fun getHorizontalPosition_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()

        assertThat(paragraph.getHorizontalPosition(placeholderStart, true))
            .isEqualToWithTolerance(placeholderLeft)
        assertThat(paragraph.getHorizontalPosition(placeholderEnd, true))
            .isEqualToWithTolerance(placeholderRight)
        assertThat(paragraph.getHorizontalPosition(cIndex + 1, true))
            .isEqualToWithTolerance(placeholderRight + glyph)
        assertThat(paragraph.getHorizontalPosition(text.length, false))
            .isEqualToWithTolerance(placeholderRight + 2 * glyph)
    }

    @Test
    fun lineApis_areInComposeOffsets() {
        val paragraph = paragraphWithPlaceholder()

        assertThat(paragraph.lineCount).isEqualTo(1)
        assertThat(paragraph.getLineStart(0)).isEqualTo(0)
        assertThat(paragraph.getLineEnd(0, visibleEnd = false)).isEqualTo(text.length)
        assertThat(paragraph.getLineEnd(0, visibleEnd = true)).isEqualTo(text.length)
        assertThat(paragraph.getLineForOffset(cIndex + 1)).isEqualTo(0)
        assertThat(paragraph.getLineForOffset(text.length)).isEqualTo(0)
    }

    @Test
    fun getPathForRange_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()

        val all = paragraph.getPathForRange(0, text.length).getBounds()
        assertThat(all.left).isEqualToWithTolerance(0f)
        assertThat(all.width).isEqualToWithTolerance(placeholderRight + 2 * glyph)

        // Only "cd".
        val cd = paragraph.getPathForRange(cIndex, text.length).getBounds()
        assertThat(cd.left).isEqualToWithTolerance(placeholderRight)
        assertThat(cd.right).isEqualToWithTolerance(placeholderRight + 2 * glyph)

        // Exactly the placeholder.
        val placeholder = paragraph.getPathForRange(placeholderStart, placeholderEnd).getBounds()
        assertThat(placeholder.left).isEqualToWithTolerance(placeholderLeft)
        assertThat(placeholder.right).isEqualToWithTolerance(placeholderRight)

        // A range ending inside the placeholder still includes it.
        val partial = paragraph.getPathForRange(0, placeholderStart + 3).getBounds()
        assertThat(partial.left).isEqualToWithTolerance(0f)
        assertThat(partial.right).isEqualToWithTolerance(placeholderRight)

        // A range starting inside the placeholder includes it as well.
        val fromInside = paragraph.getPathForRange(placeholderStart + 3, text.length).getBounds()
        assertThat(fromInside.left).isEqualToWithTolerance(placeholderLeft)
        assertThat(fromInside.right).isEqualToWithTolerance(placeholderRight + 2 * glyph)

        // An empty range stays empty.
        assertThat(paragraph.getPathForRange(placeholderStart + 3, placeholderStart + 3).isEmpty)
            .isTrue()
    }

    @Test
    fun getWordBoundary_aroundMultiCharPlaceholder() {
        val paragraph = paragraphWithPlaceholder()

        // "cd" is a word of its own: Skia's word iterator treats the placeholder (U+FFFC) as a
        // boundary, and the result is reported in Compose offsets.
        assertThat(paragraph.getWordBoundary(cIndex)).isEqualTo(TextRange(cIndex, text.length))
        assertThat(paragraph.getWordBoundary(cIndex + 1))
            .isEqualTo(TextRange(cIndex, text.length))
        assertThat(paragraph.getWordBoundary(text.length))
            .isEqualTo(TextRange(cIndex, text.length))
        // Inside the placeholder the "word" is the whole placeholder range.
        assertThat(paragraph.getWordBoundary(placeholderStart + 1))
            .isEqualTo(TextRange(placeholderStart, placeholderEnd))
    }

    @Test
    fun twoPlaceholders_accumulateDeltas() {
        // "a[x1]b[long2]c": placeholder 1 over "[x1]" (1..5), placeholder 2 over "[long2]" (6..13).
        val text = "a[x1]b[long2]c"
        val p1 = 1..5
        val p2 = 6..13
        val paragraph =
            paragraphWithPlaceholders(
                text,
                listOf(
                    placeholderRange(p1.first, p1.last, width = 30.sp),
                    placeholderRange(p2.first, p2.last, width = 40.sp),
                ),
            )
        // Layout: [a: 0..10][p1: 10..40][b: 40..50][p2: 50..90][c: 90..100]
        val cIndex = text.lastIndex

        assertThat(paragraph.getCursorRect(p1.last).left).isEqualToWithTolerance(40f)
        assertThat(paragraph.getCursorRect(p2.first).left).isEqualToWithTolerance(50f)
        assertThat(paragraph.getCursorRect(p2.last).left).isEqualToWithTolerance(90f)
        assertThat(paragraph.getCursorRect(text.length).left).isEqualToWithTolerance(100f)

        assertThat(paragraph.getOffsetForPosition(Offset(45f, 5f))).isEqualTo(p1.last)
        assertThat(paragraph.getOffsetForPosition(Offset(92f, 5f))).isEqualTo(cIndex)
        assertThat(paragraph.getOffsetForPosition(Offset(98f, 5f))).isEqualTo(text.length)

        val cBox = paragraph.getBoundingBox(cIndex)
        assertThat(cBox.left).isEqualToWithTolerance(90f)
        assertThat(cBox.right).isEqualToWithTolerance(100f)

        val rects = paragraph.placeholderRects
        assertThat(paragraph.getBoundingBox(p1.first)).isEqualTo(rects[0])
        assertThat(paragraph.getBoundingBox(p2.first + 3)).isEqualTo(rects[1])

        assertThat(paragraph.getLineEnd(0, visibleEnd = true)).isEqualTo(text.length)
        assertThat(paragraph.getPathForRange(0, text.length).getBounds().width)
            .isEqualToWithTolerance(100f)
    }

    @Test
    fun mixedSingleAndMultiCharPlaceholders_singleCharPlaceholderIsNotText() {
        // "ab[icon] cd": a multi-character placeholder over "[icon]" and a single-character one
        // over the space. Skia holds a U+FFFC for both, so the space must not be treated as
        // whitespace when Skia-space characters are inspected.
        val text = "ab[icon] cd"
        val spaceIndex = text.indexOf(' ')
        val paragraph =
            paragraphWithPlaceholders(
                text,
                listOf(
                    placeholderRange(placeholderStart, placeholderEnd, width = 30.sp),
                    placeholderRange(spaceIndex, spaceIndex + 1, width = 20.sp),
                ),
            )
        // Layout: [a: 0..10][b: 10..20][icon: 20..50][space: 50..70][c: 70..80][d: 80..90]

        // The single-character placeholder is its own word, not a separator that makes the
        // lookup fall back to the word before it.
        assertThat(paragraph.getWordBoundary(spaceIndex))
            .isEqualTo(TextRange(spaceIndex, spaceIndex + 1))
        assertThat(paragraph.getWordBoundary(spaceIndex + 1))
            .isEqualTo(TextRange(spaceIndex + 1, text.length))

        assertThat(paragraph.getCursorRect(spaceIndex).left).isEqualToWithTolerance(50f)
        assertThat(paragraph.getCursorRect(spaceIndex + 1).left).isEqualToWithTolerance(70f)
        assertThat(paragraph.getCursorRect(text.length).left).isEqualToWithTolerance(90f)
        assertThat(paragraph.getOffsetForPosition(Offset(75f, 5f))).isEqualTo(spaceIndex + 1)
        assertThat(paragraph.getBoundingBox(spaceIndex)).isEqualTo(paragraph.placeholderRects[1])
        assertThat(paragraph.getLineEnd(0, visibleEnd = true)).isEqualTo(text.length)
    }

    @Test
    fun singleCharPlaceholder_identityMapping() {
        // Default alternate text of appendInlineContent: one character.
        val text = "ab\uFFFDcd"
        val paragraph =
            paragraphWithPlaceholders(text, listOf(placeholderRange(2, 3, width = 30.sp)))
        // Layout: [a: 0..10][b: 10..20][placeholder: 20..50][c: 50..60][d: 60..70]

        assertThat(paragraph.getOffsetForPosition(Offset(52f, 5f))).isEqualTo(3)
        assertThat(paragraph.getCursorRect(3).left).isEqualToWithTolerance(50f)
        assertThat(paragraph.getCursorRect(text.length).left).isEqualToWithTolerance(70f)
        assertThat(paragraph.getBoundingBox(2)).isEqualTo(paragraph.placeholderRects.single())
        assertThat(paragraph.getBoundingBox(3).left).isEqualToWithTolerance(50f)
        assertThat(paragraph.getLineEnd(0, visibleEnd = true)).isEqualTo(text.length)
    }

    @Test
    fun multiLine_placeholderOnSecondLine() {
        // Width 55: "ab " (30px) fits on the first line but not the placeholder (30px) too;
        // "[icon]" + "cd" (50px) go to the second line.
        val text = "ab [icon]cd"
        val start = text.indexOf('[')
        val end = text.indexOf(']') + 1
        val paragraph =
            paragraphWithPlaceholders(
                text,
                listOf(placeholderRange(start, end, width = 30.sp)),
                width = 55f,
            )

        assertThat(paragraph.lineCount).isEqualTo(2)
        assertThat(paragraph.getLineStart(0)).isEqualTo(0)
        assertThat(paragraph.getLineEnd(0, visibleEnd = true)).isEqualTo(2)
        assertThat(paragraph.getLineEnd(0, visibleEnd = false)).isEqualTo(start)
        assertThat(paragraph.getLineStart(1)).isEqualTo(start)
        assertThat(paragraph.getLineEnd(1, visibleEnd = false)).isEqualTo(text.length)
        assertThat(paragraph.getLineEnd(1, visibleEnd = true)).isEqualTo(text.length)

        assertThat(paragraph.getLineForOffset(1)).isEqualTo(0)
        assertThat(paragraph.getLineForOffset(start)).isEqualTo(1)
        assertThat(paragraph.getLineForOffset(start + 3)).isEqualTo(1)
        assertThat(paragraph.getLineForOffset(end)).isEqualTo(1)
        assertThat(paragraph.getLineForOffset(text.length)).isEqualTo(1)

        // 'c' is right after the placeholder on the second line.
        val lineTop = paragraph.getLineTop(1)
        val cBox = paragraph.getBoundingBox(end)
        assertThat(cBox.left).isEqualToWithTolerance(30f)
        assertThat(cBox.top).isAtLeast(lineTop)
        assertThat(paragraph.getOffsetForPosition(Offset(32f, lineTop + 5f))).isEqualTo(end)
        assertThat(paragraph.getCursorRect(text.length).left).isEqualToWithTolerance(50f)
    }

    @Test
    fun rtl_multiCharPlaceholderInTheMiddle() {
        // Hebrew "alef bet [icon] gimel dalet"; the placeholder covers "[icon]" (offsets 2..8).
        val text = "\u05D0\u05D1[icon]\u05D2\u05D3"
        val start = text.indexOf('[')
        val end = text.indexOf(']') + 1
        val width = 200f
        val paragraph =
            paragraphWithPlaceholders(
                text,
                listOf(placeholderRange(start, end, width = 30.sp)),
                width = width,
            )
        assertThat(paragraph.getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Rtl)

        // Visual layout, right to left: [alef: 190..200][bet: 180..190][placeholder: 150..180]
        // [gimel: 140..150][dalet: 130..140].
        val placeholderRect = paragraph.placeholderRects.single()!!
        assertThat(placeholderRect.right).isEqualToWithTolerance(width - 2 * glyph)
        assertThat(placeholderRect.left)
            .isEqualToWithTolerance(width - 2 * glyph - placeholderWidth)

        for (offset in 0 until text.length) {
            assertThat(paragraph.getBidiRunDirection(offset)).isEqualTo(ResolvedTextDirection.Rtl)
        }

        // In RTL the caret before the placeholder (logical start) is at its right edge and the
        // caret after it (logical end) is at its left edge.
        assertThat(paragraph.getCursorRect(0).left).isEqualToWithTolerance(width)
        assertThat(paragraph.getCursorRect(start).left)
            .isEqualToWithTolerance(placeholderRect.right)
        assertThat(paragraph.getCursorRect(start + 3).left)
            .isEqualToWithTolerance(placeholderRect.right)
        assertThat(paragraph.getCursorRect(end).left).isEqualToWithTolerance(placeholderRect.left)
        assertThat(paragraph.getCursorRect(end + 1).left)
            .isEqualToWithTolerance(placeholderRect.left - glyph)
        assertThat(paragraph.getCursorRect(text.length).left)
            .isEqualToWithTolerance(placeholderRect.left - 2 * glyph)

        assertThat(paragraph.getBoundingBox(start)).isEqualTo(placeholderRect)
        assertThat(paragraph.getBoundingBox(start + 3)).isEqualTo(placeholderRect)
        val gimelBox = paragraph.getBoundingBox(end)
        assertThat(gimelBox.right).isEqualToWithTolerance(placeholderRect.left)
        assertThat(gimelBox.left).isEqualToWithTolerance(placeholderRect.left - glyph)

        val y = placeholderRect.center.y
        // Just right of the placeholder is the second half of bet -> the offset after it (2).
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRect.right + 2f, y)))
            .isEqualTo(start)
        // Just left of the placeholder is the first half of gimel -> gimel's offset (8).
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRect.left - 2f, y)))
            .isEqualTo(end)
        // Inside the placeholder: logical start when its right half is hit, end for the left half.
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRect.right - 2f, y)))
            .isEqualTo(start)
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRect.left + 2f, y)))
            .isEqualTo(end)
        // The rest of the letters.
        assertThat(paragraph.getOffsetForPosition(Offset(width - 8f, y))).isEqualTo(1)
        assertThat(paragraph.getOffsetForPosition(Offset(placeholderRect.left - 12f, y)))
            .isEqualTo(end + 1)

        assertThat(paragraph.getWordBoundary(end + 1)).isEqualTo(TextRange(end, text.length))
        val gimelDalet = paragraph.getPathForRange(end, text.length).getBounds()
        assertThat(gimelDalet.right).isEqualToWithTolerance(placeholderRect.left)
        assertThat(gimelDalet.left).isEqualToWithTolerance(placeholderRect.left - 2 * glyph)
    }

    private fun paragraphWithPlaceholder(): Paragraph =
        paragraphWithPlaceholders(
            text,
            listOf(placeholderRange(placeholderStart, placeholderEnd, width = 30.sp)),
        )

    private fun placeholderRange(
        start: Int,
        end: Int,
        width: TextUnit,
        height: TextUnit = fontSize,
    ) =
        AnnotatedString.Range(
            item = Placeholder(width, height, PlaceholderVerticalAlign.TextCenter),
            start = start,
            end = end,
        )

    private fun paragraphWithPlaceholders(
        text: String,
        placeholders: List<AnnotatedString.Range<Placeholder>>,
        width: Float = 2000f,
    ): Paragraph {
        val intrinsics =
            ParagraphIntrinsics(
                text = text,
                style = TextStyle(fontSize = fontSize, fontFamily = fontFamilyMeasureFont),
                annotations = emptyList(),
                density = defaultDensity,
                fontFamilyResolver = fontFamilyResolver,
                placeholders = placeholders,
            )
        return Paragraph(
            paragraphIntrinsics = intrinsics,
            constraints = Constraints(maxWidth = width.toInt()),
            maxLines = Int.MAX_VALUE,
        )
    }
}

private fun FloatSubject.isEqualToWithTolerance(expected: Float, tolerance: Float = 0.001f) =
    isWithin(tolerance).of(expected)
