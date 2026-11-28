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

package androidx.compose.ui.text

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.platform.FontCache
import androidx.compose.ui.text.platform.SkikoParagraphIntrinsics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.test.IgnoreJsTarget
import kotlinx.test.IgnoreWasmTarget
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle as SkParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle as SkTextStyle

// Adopted tests from text/text/src/androidTest/java/androidx/compose/ui/text/android/selection/WordBoundaryTest.kt
@OptIn(InternalComposeUiApi::class)
class SkikoParagraphTest {
    @BeforeTest
    fun setup() {
        registerSkikoComposeImplementation()
    }

    @AfterTest
    fun tearDown() {
        clearSkikoComposeImplementation()
    }

    // Lazy so the registry is populated by [setup] before the resolver is created (the property
    // initializer would otherwise run at construction time, before @BeforeTest).
    private val fontFamilyResolver by lazy { createFontFamilyResolver() }
    private val defaultDensity = Density(density = 1f)
    private val maxWidthConstraint = 1000

    @Test
    fun getWordBoundary_out_of_boundary_too_small() {
        val text = "text"
        val paragraph = simpleParagraph(text)

        assertFailsWith<IllegalArgumentException> {
            paragraph.getWordBoundary(-1)
        }
    }

    @Test
    fun getWordBoundary_out_of_boundary_too_big() {
        val text = "text"
        val paragraph = simpleParagraph(text)

        assertFailsWith<IllegalArgumentException> {
            paragraph.getWordBoundary(text.length + 1)
        }
    }

    @Test
    fun getWordBoundary_length() {
        val text = "text"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(0, text.length),
            paragraph.getWordBoundary(text.length)
        )
    }

    @Test
    fun getWordBoundary_empty_string() {
        val paragraph = simpleParagraph("")

        assertEquals(
            TextRange(0, 0),
            paragraph.getWordBoundary(0)
        )
    }

    @Test
    fun getWordBoundary() {
        val text = "abc def-ghi. jkl"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf('a'))
        )
        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf('c'))
        )
        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf(' '))
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.indexOf('-')),
            paragraph.getWordBoundary(text.indexOf('d'))
        )
        assertEquals(
            TextRange(text.indexOf('g'), text.indexOf('.')),
            paragraph.getWordBoundary(text.indexOf('i'))
        )
        assertEquals(
            TextRange(text.indexOf('j'), text.indexOf('l') + 1),
            paragraph.getWordBoundary(text.indexOf('k'))
        )
    }

    @Test
    fun getWordBoundary_spaces() {
        val text = "ab cd  e"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf('b') + 1),
            paragraph.getWordBoundary(text.indexOf('b') + 1)
        )
        assertEquals(
            TextRange(text.indexOf('c'), text.indexOf('d') + 1),
            paragraph.getWordBoundary(text.indexOf('c'))
        )
        assertEquals(
            TextRange(text.indexOf('d') + 2, text.indexOf('d') + 2),
            paragraph.getWordBoundary(text.indexOf('d') + 2)
        )
    }

    @Test
    fun getWordBoundary_no_break_space() {
        val text = "abc\u00A0def\u202Fghi"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf('c') + 1),
            paragraph.getWordBoundary(text.indexOf('b'))
        )
        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf('c') + 1),
            paragraph.getWordBoundary(text.indexOf('\u00A0'))
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.length),
            paragraph.getWordBoundary(text.indexOf('d'))
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.length),
            paragraph.getWordBoundary(text.indexOf('\u202F'))
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.length),
            paragraph.getWordBoundary(text.length)
        )
    }

    @Test
    fun getWordBoundary_RTL() { // Hebrew -- "אבג דה-וז. חט"
        val text = "\u05d0\u05d1\u05d2 \u05d3\u05d4-\u05d5\u05d6. \u05d7\u05d8"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('\u05d0'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf('\u05d0'))
        )
        assertEquals(
            TextRange(text.indexOf('\u05d0'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf('\u05d2'))
        )
        assertEquals(
            TextRange(text.indexOf('\u05d0'), text.indexOf(' ')),
            paragraph.getWordBoundary(text.indexOf(' '))
        )
        assertEquals(
            TextRange(text.indexOf('\u05d3'), text.indexOf('-')),
            paragraph.getWordBoundary(text.indexOf('\u05d4'))
        )
        /*
        TODO: Port punctuation handling from Android.
        assertEquals(
            TextRange(text.indexOf('\u05d3'), text.indexOf('-') + 1),
            paragraph.getWordBoundary(text.indexOf('-'))
        )
        assertEquals(
            TextRange(text.indexOf('-'), text.indexOf('.')),
            paragraph.getWordBoundary(text.indexOf('\u05d5'))
        )
         */
        assertEquals(
            TextRange(text.indexOf('\u05d5'), text.indexOf('.')),
            paragraph.getWordBoundary(text.indexOf('\u05d6'))
        )
        assertEquals(
            TextRange(text.indexOf('\u05d7'), text.length),
            paragraph.getWordBoundary(text.indexOf('\u05d7'))
        )
    }

    @Test
    fun getWordBoundary_CJK() { // Japanese HIRAGANA letter + KATAKANA letters
        val text = "\u3042\u30A2\u30A3\u30A4"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('\u3042'), text.indexOf('\u3042') + 1),
            paragraph.getWordBoundary(text.indexOf('\u3042'))
        )
        /*
        TODO: figure out why skia's ICU split words this way
        assertEquals(
            TextRange(text.indexOf('\u3042'), text.indexOf('\u30A4') + 1),
            paragraph.getWordBoundary(text.indexOf('\u30A2'))
        )
         */
        assertEquals(
            TextRange(text.indexOf('\u30A2'), text.indexOf('\u30A4') + 1),
            paragraph.getWordBoundary(text.indexOf('\u30A4'))
        )
        /*
        TODO: figure out why skia's ICU split words this way
        assertEquals(
            TextRange(text.indexOf('\u30A2'), text.indexOf('\u30A4') + 1),
            paragraph.getWordBoundary(text.length)
        )
         */
    }

    @Test
    fun getWordBoundary_apostropheMiddleOfWord() {
        // These tests confirm that the word "isn't" is treated like one word.
        val text = "isn't he"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('i'), text.indexOf('t') + 1),
            paragraph.getWordBoundary(text.indexOf('i'))
        )
        assertEquals(
            TextRange(text.indexOf('i'), text.indexOf('t') + 1),
            paragraph.getWordBoundary(text.indexOf('n'))
        )
        assertEquals(
            TextRange(text.indexOf('i'), text.indexOf('t') + 1),
            paragraph.getWordBoundary(text.indexOf('\''))
        )
        assertEquals(
            TextRange(text.indexOf('i'), text.indexOf('t') + 1),
            paragraph.getWordBoundary(text.indexOf('t'))
        )
        assertEquals(
            TextRange(text.indexOf('i'), text.indexOf('t') + 1),
            paragraph.getWordBoundary(text.indexOf('t') + 1)
        )
        assertEquals(
            TextRange(text.indexOf('h'), text.indexOf('e') + 1),
            paragraph.getWordBoundary(text.indexOf('h'))
        )
    }

    @Test
    @Ignore // TODO: Port punctuation handling from Android.
    fun getWordBoundary_isOnPunctuation() {
        val text = "abc!? (^^;) def"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf('!')),
            paragraph.getWordBoundary(text.indexOf('a'))
        )
        assertEquals(
            TextRange(text.indexOf('a'), text.indexOf('?') + 1),
            paragraph.getWordBoundary(text.indexOf('!'))
        )
        assertEquals(
            TextRange(text.indexOf('!'), text.indexOf('?') + 1),
            paragraph.getWordBoundary(text.indexOf('?') + 1)
        )
        assertEquals(
            TextRange(text.indexOf('('), text.indexOf('(') + 1),
            paragraph.getWordBoundary(text.indexOf('('))
        )
        assertEquals(
            TextRange(text.indexOf('(') + 2, text.indexOf('(') + 2),
            paragraph.getWordBoundary(text.indexOf('(') + 2)
        )
        assertEquals(
            TextRange(text.indexOf(';'), text.indexOf(')') + 1),
            paragraph.getWordBoundary(text.indexOf(';'))
        )
        assertEquals(
            TextRange(text.indexOf(';'), text.indexOf(')') + 1),
            paragraph.getWordBoundary(text.indexOf(')'))
        )
        assertEquals(
            TextRange(text.indexOf(';'), text.indexOf(')') + 1),
            paragraph.getWordBoundary(text.indexOf(')') + 1)
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.length),
            paragraph.getWordBoundary(text.indexOf('d'))
        )
        assertEquals(
            TextRange(text.indexOf('d'), text.length),
            paragraph.getWordBoundary(text.length)
        )
    }

    @Test
    fun getWordBoundary_emoji() {
        // "ab 🧑🏿‍🦰 cd" - example of complex emoji
        //             | (offset=3)      | (offset=6)
        val text = "ab \uD83E\uDDD1\uD83C\uDFFF\u200D\uD83E\uDDB0 cd"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(3, 10),
            paragraph.getWordBoundary(6)
        )
    }

    @Test
    fun getWordBoundary_multichar() {
        // "ab 𐐔𐐯𐑅𐐨𐑉𐐯𐐻 cd" - example of multi-char code units
        //             | (offset=3)      | (offset=6)
        val text =
            "ab \uD801\uDC14\uD801\uDC2F\uD801\uDC45\uD801\uDC28\uD801\uDC49\uD801\uDC2F\uD801\uDC3B cd"
        val paragraph = simpleParagraph(text)

        assertEquals(
            TextRange(3, 17),
            paragraph.getWordBoundary(6)
        )
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_ltr_start_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.Start, textDirection = TextDirection.Ltr))
        val cursorHorizontalPosition: Float = paragraph.getHorizontalPosition(0, false)
        assertEquals(0f, cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_ltr_end_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.End, textDirection = TextDirection.Ltr))
        val cursorHorizontalPosition = paragraph.getHorizontalPosition(0, false)
        assertEquals(maxWidthConstraint.toFloat(), cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_center_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.Center))
        val cursorHorizontalPosition = paragraph.getHorizontalPosition(0, false)
        assertEquals((maxWidthConstraint / 2).toFloat(), cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_rtl_start_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.Start, textDirection = TextDirection.Rtl))
        val cursorHorizontalPosition: Float = paragraph.getHorizontalPosition(0, false)
        assertEquals(maxWidthConstraint.toFloat(), cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_rtl_end_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.End, textDirection = TextDirection.Rtl))
        val cursorHorizontalPosition = paragraph.getHorizontalPosition(0, false)
        assertEquals(0f, cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_left_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.Start))
        val cursorHorizontalPosition: Float = paragraph.getHorizontalPosition(0, false)
        assertEquals(0f, cursorHorizontalPosition)
    }

    @Test
    fun getHorizontalPosition_cursor_empty_textfield_right_alignment() {
        val paragraph = simpleParagraph("", TextStyle(textAlign = TextAlign.End))
        val cursorHorizontalPosition = paragraph.getHorizontalPosition(0, false)
        assertEquals(maxWidthConstraint.toFloat(), cursorHorizontalPosition)
    }

    @Test
    // Ignored on web: skiko shapes the cluster with an inflated advance there (see note below),
    // which moves the box midpoint and breaks this check. A stable web run needs a bundled font.
    // TODO: CMP-10342
    @IgnoreJsTarget
    @IgnoreWasmTarget
    fun getOffsetForPosition_midpointOfComplexCharacter_snapsToClusterStart() {
        val text = "abca\u030B abc" // "abc" + 'a' + U+030B (combining double acute) + " abc"
        val paragraph = simpleParagraph(text)

        val complexCharStart = 3 // Index of the base 'a'; the 'a' + U+030B cluster spans indices 3..4
        val complexCharBox = paragraph.getBoundingBox(complexCharStart)

        // On web the default font shapes 'a' + U+030B with an inflated cluster advance (the
        // combining acute consumes width), so getBoundingBox returns a much wider box than on
        // desktop and this midpoint check would resolve to the cluster end. A deterministic web
        // run would require an explicitly bundled font.

        // Click exactly in the middle of the complex character's box.
        val insideOffset =
            Offset(complexCharBox.left + complexCharBox.width / 2, complexCharBox.center.y)
        val position = paragraph.getOffsetForPosition(insideOffset)

        // A midpoint click is a tie between the two valid caret positions (before the
        // cluster = 3, after it = 5). Matching Android's Layout.getOffsetForHorizontal,
        // the exact-midpoint tie resolves to the before-char side, i.e. the cluster start.
        // The caret must never land on the internal code-unit index (4).
        assertEquals(
            3, // start of the 'a' + U+030B cluster (before-char), Android-compatible midpoint tie-break
            position,
            message = "A midpoint click on a complex cluster should snap to the cluster start (before-char)"
        )
    }

    /**
     * Test that the caret can be placed at the end of a line when a complex character
     * is the last character in the line.
     */
    @Test
    fun getOffsetForPosition_endOfLineWithComplexCharacter_shouldPositionCorrectly() {
        val text = "abc\u0915\u094D" // "abcक्"
        val paragraph = simpleParagraph(text)

        val complexCharStart = 3
        val complexCharBox = paragraph.getBoundingBox(complexCharStart)

        val afterOffset = Offset(complexCharBox.right + 5, complexCharBox.center.y)
        val position = paragraph.getOffsetForPosition(afterOffset)


        assertEquals(
            5,
            position,
            message = "The position should be at the end of the text"
        )
    }


    @Test
    fun bullet_withEmUnits_shouldNotCrash() {
        // Regression test for bug where Bullet with Em units caused crash:
        // "Only Sp can convert to Px" when textIndent used Em units
        val text = "First item"

        // Create a simple paragraph with textIndent using Em units (like Bullet.DefaultIndentation)
        val paragraph = Paragraph(
            text = text,
            style = TextStyle(
                textIndent = TextIndent(1.em, 1.em)
            ),
            constraints = Constraints(maxWidth = maxWidthConstraint),
            density = defaultDensity,
            fontFamilyResolver = fontFamilyResolver
        )

        // Verify paragraph was created successfully without crashing
        assertEquals(1, paragraph.lineCount)
    }

    @Test
    fun maxIntrinsicWidth_includesFirstLineIndent() {
        val text = "Bullet list item"
        val textStyle = TextStyle(fontSize = 20.sp)

        val plain = simpleIntrinsics(text, textStyle)
        val indented = simpleIntrinsics(
            text = text,
            textStyle = textStyle.copy(textIndent = TextIndent(firstLine = 20.sp))
        )

        assertEquals(plain.maxIntrinsicWidth + 20f, indented.maxIntrinsicWidth)
    }

    @Test
    fun paragraphAtItsMaxIntrinsicWidth_doesNotWrap_spIndent() {
        assertSingleLineAtMaxIntrinsicWidth(TextIndent(firstLine = 20.sp), expectedIndentPx = 20f)
    }

    @Test
    fun paragraphAtItsMaxIntrinsicWidth_doesNotWrap_emIndent() {
        // The unit a bullet list uses, see Bullet.DefaultIndentation.
        assertSingleLineAtMaxIntrinsicWidth(TextIndent(firstLine = 1.em), expectedIndentPx = 20f)
    }

    /** Expects the text laid out at its own intrinsic width to fit into a single line. */
    private fun assertSingleLineAtMaxIntrinsicWidth(
        textIndent: TextIndent,
        expectedIndentPx: Float
    ) {
        val intrinsics = simpleIntrinsics(
            // Skia drops the indentation when a single unbreakable run does not fit into what is
            // left of the width, so the text needs several words.
            text = "Bullet list item",
            textStyle = TextStyle(fontSize = 20.sp, textIndent = textIndent)
        )

        val paragraph = Paragraph(
            paragraphIntrinsics = intrinsics,
            constraints = Constraints(maxWidth = ceil(intrinsics.maxIntrinsicWidth).toInt()),
            overflow = TextOverflow.Clip
        )

        assertEquals(1, paragraph.lineCount)
        assertEquals(expectedIndentPx, paragraph.getLineLeft(0))
    }

    // Regression test for https://youtrack.jetbrains.com/issue/CMP-10034
    @Test
    fun getCursorRect_doesNotCrash_inRtlParagraphsWithNewlinesAndSpecialCharacters() {
        val problemTexts = listOf(
            // Arabic with diacritics across a newline: "اّ\nبَ"
            "\u0627\u0651\n\u0628\u064E",
            // Hebrew followed by newline and emoji: "אב\n😀"
            "\u05D0\u05D1\n\uD83D\uDE00",
            // RTL with multiple newlines and combining marks: "א\nבְ\nג"
            "\u05D0\n\u05D1\u05B0\n\u05D2",
            // Newline at start: "\nאב"
            "\n\u05D0\u05D1",
            // Newline followed by combining mark: "א\nְב"
            "\u05D0\n\u05B0\u05D1",
        )

        for (text in problemTexts) {
            val paragraph = simpleParagraph(
                text = text,
                textStyle = TextStyle(textDirection = TextDirection.Rtl, fontSize = 20.sp)
            )
            for (offset in 0..text.length) {
                paragraph.getCursorRect(offset)
            }
        }
    }

    @Test
    fun getCursorRect_hasSameHeightForEmptyAndNonEmptyText() {
        val style = TextStyle(
            fontSize = 64.sp,
            letterSpacing = 0.5.sp,
            lineHeight = 24.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
                mode = LineHeightStyle.Mode.Fixed,
            ),
        )
        val emptyParagraph = simpleParagraph(text = "", textStyle = style)
        val nonEmptyText = "q"
        val nonEmptyParagraph = simpleParagraph(text = nonEmptyText, textStyle = style)

        val emptyCursor = emptyParagraph.getCursorRect(0)
        val nonEmptyCursor = nonEmptyParagraph.getCursorRect(nonEmptyText.length)

        assertEquals(emptyCursor.height, nonEmptyCursor.height)
    }

    @Test
    fun lineTopsAndBottoms_tileTheParagraph() {
        for ((styleName, style) in lineHeightStyles) {
            for (text in listOf("", "Hg", "Hg\nHg\nHg", "Hg\n")) {
                val paragraph = simpleParagraph(text = text, textStyle = style)
                assertLinesTileTheParagraph(
                    paragraph,
                    "$styleName, text \"${text.replace("\n", "\\n")}\"",
                )
            }
        }
    }

    @Test
    fun lineTopsAndBottoms_tileTheParagraph_whenLinesDiffer() {
        val text = "Hg Hg Hg Hg Hg Hg Hg Hg Hg Hg Hg Hg"
        val largeSpan = AnnotatedString.Range(SpanStyle(fontSize = 40.sp), start = 9, end = 14)
        val placeholders = listOf(
            PlaceholderVerticalAlign.AboveBaseline,
            PlaceholderVerticalAlign.Bottom,
            PlaceholderVerticalAlign.Center,
            PlaceholderVerticalAlign.TextCenter,
        ).mapIndexed { index, verticalAlign ->
            AnnotatedString.Range(
                Placeholder(width = 20.sp, height = 50.sp, placeholderVerticalAlign = verticalAlign),
                start = index * 9,
                end = index * 9 + 1,
            )
        }
        for ((styleName, style) in lineHeightStyles) {
            fun paragraph(
                spanStyles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
                placeholders: List<AnnotatedString.Range<Placeholder>> = emptyList(),
                maxLines: Int = Int.MAX_VALUE,
            ) = Paragraph(
                text = text,
                style = style,
                // Narrow enough to wrap the text onto several lines
                constraints = Constraints(maxWidth = 150),
                density = defaultDensity,
                fontFamilyResolver = fontFamilyResolver,
                spanStyles = spanStyles,
                placeholders = placeholders,
                maxLines = maxLines,
            )

            assertLinesTileTheParagraph(paragraph(), "$styleName, wrapped")
            assertLinesTileTheParagraph(paragraph(maxLines = 2), "$styleName, two lines at most")
            assertLinesTileTheParagraph(
                paragraph(spanStyles = listOf(largeSpan)),
                "$styleName, a larger span",
            )
            assertLinesTileTheParagraph(
                paragraph(placeholders = placeholders),
                "$styleName, placeholders",
            )
            assertLinesTileTheParagraph(
                paragraph(spanStyles = listOf(largeSpan), placeholders = placeholders),
                "$styleName, a larger span and placeholders",
            )
        }
    }

    // Skia gives no line metrics for such a paragraph, so its line is made up.
    @Test
    fun lineTopsAndBottoms_tileTheParagraph_ofOnePlaceholder() {
        for ((styleName, style) in lineHeightStyles) {
            val paragraph = Paragraph(
                text = "\uFFFD",
                style = style,
                constraints = Constraints(maxWidth = maxWidthConstraint),
                density = defaultDensity,
                fontFamilyResolver = fontFamilyResolver,
                placeholders = listOf(
                    AnnotatedString.Range(
                        Placeholder(20.sp, 50.sp, PlaceholderVerticalAlign.AboveBaseline),
                        start = 0,
                        end = 1,
                    )
                ),
            )

            assertEquals(1, paragraph.lineCount, styleName)
            assertLinesTileTheParagraph(paragraph, styleName)
        }
    }

    private fun assertLinesTileTheParagraph(paragraph: Paragraph, message: String) {
        assertEquals(0f, paragraph.getLineTop(0), message)
        for (line in 0 until paragraph.lineCount) {
            assertEquals(
                paragraph.getLineTop(line) + paragraph.getLineHeight(line),
                paragraph.getLineBottom(line),
                absoluteTolerance = 0.01f,
                "$message, line $line",
            )
            if (line > 0) {
                assertEquals(
                    paragraph.getLineBottom(line - 1),
                    paragraph.getLineTop(line),
                    "$message, line $line",
                )
            }
        }
        assertEquals(
            paragraph.height,
            paragraph.getLineBottom(paragraph.lineCount - 1),
            absoluteTolerance = 0.01f,
            message,
        )
    }

    @Test
    fun getLineForVerticalPosition_agreesWithLineTopsAndBottoms() {
        for ((styleName, style) in lineHeightStyles) {
            for (text in listOf("", "Hg", "Hg\nHg\nHg", "Hg\n")) {
                val paragraph = simpleParagraph(text = text, textStyle = style)
                val message = "$styleName, text \"${text.replace("\n", "\\n")}\""

                for (line in 0 until paragraph.lineCount) {
                    val top = paragraph.getLineTop(line)
                    val bottom = paragraph.getLineBottom(line)
                    for (vertical in listOf(top, (top + bottom) / 2, bottom - 0.25f)) {
                        assertEquals(
                            line,
                            paragraph.getLineForVerticalPosition(vertical),
                            "$message, position $vertical",
                        )
                    }
                }
                assertEquals(0, paragraph.getLineForVerticalPosition(-1f), message)
                assertEquals(
                    paragraph.lineCount - 1,
                    paragraph.getLineForVerticalPosition(paragraph.height + 1f),
                    message,
                )
            }
        }
    }

    @Test
    fun ellipsisByHeight_keepsTheLinesThatFit() {
        for ((styleName, style) in lineHeightStyles) {
            val text = "Hg\nHg\nHg"
            val unconstrained = simpleParagraph(text, style)
            val twoLinesHeight =
                ceil(unconstrained.getLineHeight(0) + unconstrained.getLineHeight(1)).toInt()

            for ((maxHeight, expectedLineCount) in listOf(
                twoLinesHeight to 2,
                twoLinesHeight - 1 to 1,
            )) {
                val paragraph = Paragraph(
                    text = text,
                    style = style,
                    constraints = Constraints(maxWidth = maxWidthConstraint, maxHeight = maxHeight),
                    density = defaultDensity,
                    fontFamilyResolver = fontFamilyResolver,
                    overflow = TextOverflow.Ellipsis,
                )
                val message = "$styleName, max height $maxHeight"

                assertEquals(expectedLineCount, paragraph.lineCount, message)
            }
        }
    }

    // A single-line text field takes its height from the bottom of its first line, so a
    // difference here makes the field change its height when its first character is typed.
    @Test
    fun getLineBottom_isTheSameForEmptyAndNonEmptyText() {
        var comparedStyles = 0
        for ((styleName, style) in lineHeightStyles) {
            val emptyParagraph = simpleParagraph(text = "", textStyle = style)
            val nonEmptyParagraph = simpleParagraph(text = "Hg", textStyle = style)

            // Skia sizes an empty paragraph from the font's rounded height and a line of text
            // from its exact height, so for some styles the two paragraphs are a pixel apart.
            // Which styles depends on the font, and there the bottoms can't be the same.
            if (emptyParagraph.height != nonEmptyParagraph.height) continue
            comparedStyles++

            assertEquals(
                nonEmptyParagraph.getLineTop(0),
                emptyParagraph.getLineTop(0),
                styleName,
            )
            assertEquals(
                nonEmptyParagraph.getLineBottom(0),
                emptyParagraph.getLineBottom(0),
                styleName,
            )
        }
        assertTrue(comparedStyles > 0, "No style to compare")
    }

    // Many font sizes, so that the font's height rounds both up and down whatever the platform's
    // default font is, with and without a line height, with every alignment and trim skia treats
    // differently, with whole and fractional line heights, and a line height below the font size.
    private val lineHeightStyles: List<Pair<String, TextStyle>> = buildList {
        val fontSizes = (20..48).map { (it / 2f).sp }
        for (fontSize in fontSizes) {
            add("$fontSize" to TextStyle(fontSize = fontSize))
        }
        add("64sp, line height 24sp" to TextStyle(fontSize = 64.sp, lineHeight = 24.sp))

        val fontSizesAndLineHeights =
            // Material 3's bodyLarge, bodyMedium and titleLarge
            listOf(16.sp to 24.sp, 14.sp to 20.sp, 22.sp to 28.sp) +
                fontSizes.map { it to (it.value.toInt() + 6).sp } +
                fontSizes.map { it to it * 1.5f }
        for ((fontSize, lineHeight) in fontSizesAndLineHeights) {
            for (alignment in listOf(
                LineHeightStyle.Alignment.Center,
                LineHeightStyle.Alignment.Proportional,
            )) {
                for (trim in listOf(
                    LineHeightStyle.Trim.None,
                    LineHeightStyle.Trim.Both,
                    LineHeightStyle.Trim.FirstLineTop,
                    LineHeightStyle.Trim.LastLineBottom,
                )) {
                    add(
                        "$fontSize, line height $lineHeight, $alignment, $trim" to TextStyle(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            lineHeightStyle = LineHeightStyle(alignment, trim),
                        )
                    )
                }
            }
        }
    }

    // https://youtrack.jetbrains.com/issue/CMP-11086
    @Test
    fun setDrawStyle_fillOrNull_doesNotRequireRelayout() {
        val layouter = simpleLayouter()

        // The default draw style is null, while painters pass Fill; both are painted the same
        layouter.setDrawStyle(Fill)
        assertFalse(layouter.needsForegroundUpdate)
        layouter.setDrawStyle(null)
        assertFalse(layouter.needsForegroundUpdate)
    }

    @Test
    fun setDrawStyle_stroke_requiresRelayout() {
        val layouter = simpleLayouter()

        layouter.setDrawStyle(Stroke(width = 2f))
        assertTrue(layouter.needsForegroundUpdate)
    }

    @Test
    fun setBrush_sameBrushAndNaNAlpha_doesNotRequireRelayout() {
        val layouter = simpleLayouter()
        val brush = Brush.verticalGradient(listOf(Color.Blue, Color.Red))
        val size = Size(100f, 100f)

        // NaN is the default alpha, e.g. of TextStyle(brush = ...)
        layouter.setBrush(brush, size, alpha = Float.NaN)
        layouter.layoutParagraph(maxWidthConstraint.toFloat())
        layouter.setBrush(brush, size, alpha = Float.NaN)
        assertFalse(layouter.needsForegroundUpdate)
    }

    @Test
    fun setBrush_alphaChangedFromNaN_requiresRelayout() {
        val layouter = simpleLayouter()
        val brush = Brush.verticalGradient(listOf(Color.Blue, Color.Red))
        val size = Size(100f, 100f)

        layouter.setBrush(brush, size, alpha = Float.NaN)
        layouter.layoutParagraph(maxWidthConstraint.toFloat())
        layouter.setBrush(brush, size, alpha = 0.5f)
        assertTrue(layouter.needsForegroundUpdate)
    }

    // https://youtrack.jetbrains.com/issue/CMP-8959
    @Test
    fun paintWithNewColor_changesTextDecorationColor() {
        val paragraph = decoratedParagraph()

        assertTrue(
            paragraph.paintedImage(Color.Red).anyPixel { it.isPainted },
            "Nothing was painted"
        )
        val image = paragraph.paintedImage(Color.Blue)
        assertTrue(image.anyPixel { it.isPainted }, "Nothing was painted")
        assertFalse(
            image.anyPixel { it.hasRed },
            "Some pixels are still painted with the old color"
        )
    }

    // https://youtrack.jetbrains.com/issue/CMP-8959
    @Test
    fun paintWithNewSolidColorBrush_changesTextDecorationColor() {
        val paragraph = decoratedParagraph()

        assertTrue(
            paragraph.paintedImage(SolidColor(Color.Red)).anyPixel { it.isPainted },
            "Nothing was painted"
        )
        val image = paragraph.paintedImage(SolidColor(Color.Blue))
        assertTrue(image.anyPixel { it.isPainted }, "Nothing was painted")
        assertFalse(
            image.anyPixel { it.hasRed },
            "Some pixels are still painted with the old color"
        )
    }

    // https://youtrack.jetbrains.com/issue/CMP-8959
    @Test
    fun setShaderBrush_onDecoratedText_doesNotRequireRelayout() {
        val layouter =
            (simpleIntrinsics("Lorem ipsum", TextStyle(textDecoration = TextDecoration.Underline))
                as SkikoParagraphIntrinsics).layouter()
        val size = Size(100f, 100f)

        // Skia only paints decorations with a solid color, and with a ShaderBrush that color
        // doesn't depend on the brush, so changing the brush doesn't need to update them
        val gradient1 = Brush.verticalGradient(listOf(Color.Blue, Color.Red))
        val gradient2 = Brush.verticalGradient(listOf(Color.Green, Color.Red))
        layouter.setBrush(gradient1, size, alpha = 1f)
        layouter.layoutParagraph(maxWidthConstraint.toFloat())
        layouter.setBrush(gradient2, size, alpha = 0.5f)
        assertTrue(layouter.needsForegroundUpdate)
    }

    private fun decoratedParagraph() = simpleParagraph(
        text = "Hello",
        textStyle = TextStyle(
            fontSize = 20.sp,
            color = Color.Red,
            textDecoration = TextDecoration.Underline + TextDecoration.LineThrough
        )
    )

    private fun Paragraph.paintedImage(color: Color): ImageBitmap {
        val image = ImageBitmap(width = 200, height = 40)
        paint(
            canvas = Canvas(image),
            color = color,
            shadow = null,
            textDecoration = TextDecoration.Underline + TextDecoration.LineThrough,
            drawStyle = null,
            blendMode = DrawScope.DefaultBlendMode
        )
        return image
    }

    private fun Paragraph.paintedImage(brush: Brush): ImageBitmap {
        val image = ImageBitmap(width = 200, height = 40)
        paint(
            canvas = Canvas(image),
            brush = brush,
            alpha = Float.NaN,
            shadow = null,
            textDecoration = TextDecoration.Underline + TextDecoration.LineThrough,
            drawStyle = null,
            blendMode = DrawScope.DefaultBlendMode
        )
        return image
    }

    private fun ImageBitmap.anyPixel(predicate: (Color) -> Boolean): Boolean {
        val pixelMap = toPixelMap()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (predicate(pixelMap[x, y])) return true
            }
        }
        return false
    }

    // The canvas is fully transparent, and antialiasing only changes a pixel's alpha, so a pixel
    // painted pure blue has no red at all, and one painted pure red does
    private val Color.isPainted get() = alpha > 0f
    private val Color.hasRed get() = red > 0f

    // https://youtrack.jetbrains.com/issue/CMP-10337
    @Test
    fun textStyleLocaleList_selectsLocaleSpecificGlyphs() {
        if (!hasLocaleSpecificCjkGlyphs) return

        fun paintedPixels(localeList: LocaleList) =
            paintedPixels(HAN_TEXT, TextStyle(localeList = localeList))

        val japanese = paintedPixels(LocaleList("ja"))
        val simplifiedChinese = paintedPixels(LocaleList("zh-Hans"))

        assertTrue(
            japanese.contentEquals(paintedPixels(LocaleList("ja"))),
            "The same locale must render identically"
        )
        assertFalse(
            japanese.contentEquals(simplifiedChinese),
            "Japanese and Simplified Chinese glyphs must differ"
        )
    }

    // https://youtrack.jetbrains.com/issue/CMP-10337
    @Test
    fun spanStyleLocaleList_selectsLocaleSpecificGlyphs() {
        if (!hasLocaleSpecificCjkGlyphs) return

        fun text(localeList: LocaleList) = buildAnnotatedString {
            withStyle(SpanStyle(localeList = localeList)) {
                append(HAN_TEXT)
            }
        }

        val japanese = paintedPixels(text(LocaleList("ja")), TextStyle())
        val simplifiedChinese = paintedPixels(text(LocaleList("zh-Hans")), TextStyle())

        assertFalse(
            japanese.contentEquals(simplifiedChinese),
            "Japanese and Simplified Chinese glyphs must differ"
        )
    }

    private fun simpleLayouter() =
        (simpleIntrinsics("Lorem ipsum", TextStyle()) as SkikoParagraphIntrinsics).layouter()
            .apply { layoutParagraph(maxWidthConstraint.toFloat()) }

    private fun simpleIntrinsics(text: String, textStyle: TextStyle) = ParagraphIntrinsics(
        text = text,
        style = textStyle,
        annotations = emptyList(),
        density = defaultDensity,
        fontFamilyResolver = fontFamilyResolver
    )

    private fun simpleParagraph(text: String, textStyle: TextStyle = TextStyle()) = Paragraph(
        text = text,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthConstraint),
        density = defaultDensity,
        fontFamilyResolver = fontFamilyResolver
    )

    /** Paints [text] and returns its pixels, checking that some text has actually been painted. */
    private fun paintedPixels(text: AnnotatedString, textStyle: TextStyle): IntArray {
        val paragraph = Paragraph(
            text = text.text,
            style = textStyle.merge(fontSize = 32.sp, color = Color.Black),
            constraints = Constraints(maxWidth = CANVAS_WIDTH),
            density = defaultDensity,
            fontFamilyResolver = fontFamilyResolver,
            defaultLocaleList = LocaleList("en-US"),
            spanStyles = text.spanStyles,
        )
        val image = ImageBitmap(CANVAS_WIDTH, CANVAS_HEIGHT)
        paragraph.paint(
            canvas = Canvas(image),
            color = Color.Unspecified,
            shadow = null,
            textDecoration = null,
            drawStyle = null,
            blendMode = DrawScope.DefaultBlendMode
        )
        val pixels = image.toPixelMap().buffer
        assertTrue(pixels.any { it != 0 }, "No text was painted")
        return pixels
    }

    private fun paintedPixels(text: String, textStyle: TextStyle) =
        paintedPixels(AnnotatedString(text), textStyle)

    private companion object {
        // Ideographs whose glyph shapes differ between the Japanese and Chinese conventions
        const val HAN_TEXT = "直海角骨"

        // The size of the canvas the locale tests paint [HAN_TEXT] on
        const val CANVAS_WIDTH = 400
        const val CANVAS_HEIGHT = 60

        /**
         * Whether the host's fonts have locale-specific glyph variants for [HAN_TEXT].
         *
         * Checked by drawing the text with Skia directly, bypassing Compose's text styles, in
         * Japanese and in Simplified Chinese, using the same font fallback setup as Compose. Hosts
         * without such fonts (e.g. Linux machines without CJK fonts) draw both the same way, or not
         * at all. Computed once, as it can't change during a test run.
         */
        val hasLocaleSpecificCjkGlyphs: Boolean by lazy {
            val fonts = FontCache().fonts

            fun skiaPixels(locale: String): IntArray {
                val style = SkTextStyle()
                    .setFontSize(32f)
                    .setColor(Color.Black.toArgb())
                    .setLocale(locale)
                val paragraph = ParagraphBuilder(SkParagraphStyle(), fonts)
                    .pushStyle(style)
                    .addText(HAN_TEXT)
                    .build()
                    .layout(CANVAS_WIDTH.toFloat())
                val surface = Surface.makeRasterN32Premul(CANVAS_WIDTH, CANVAS_HEIGHT)
                try {
                    paragraph.paint(surface.canvas, 0f, 0f)
                    return surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap().buffer
                } finally {
                    surface.close()
                }
            }

            !skiaPixels("ja").contentEquals(skiaPixels("zh-Hans"))
        }
    }
}
