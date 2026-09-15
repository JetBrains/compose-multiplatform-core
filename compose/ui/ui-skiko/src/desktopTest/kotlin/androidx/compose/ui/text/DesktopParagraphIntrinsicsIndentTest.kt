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

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Intrinsic widths of an indented paragraph, measured with a fixed advance test font so that the
 * expected numbers can be derived from the text length instead of from another measurement.
 */
@RunWith(JUnit4::class)
class DesktopParagraphIntrinsicsIndentTest : SkikoComposeTestBase() {

    private val fontFamilyResolver = createFontFamilyResolver()
    private val fontFamilyMeasureFont =
        FontFamily(
            Font(
                "font_desktop/sample_font.ttf",
                weight = FontWeight.Normal,
                style = FontStyle.Normal
            )
        )
    private val defaultDensity = Density(density = 1f)
    private val fontSize = 10.sp
    private val fontSizeInPx = with(defaultDensity) { fontSize.toPx() }

    /** Every glyph of the measure font advances by exactly the font size. */
    private val textWidthInPx = TEXT.length * fontSizeInPx

    @Test
    fun withoutIndent_maxIntrinsicWidth_isTheTextWidth() {
        val intrinsics = intrinsics(textIndent = null)

        assertThat(intrinsics.maxIntrinsicWidth).isWithin(TOLERANCE).of(textWidthInPx)
    }

    @Test
    fun spIndent_isAddedToMaxIntrinsicWidth() {
        val intrinsics = intrinsics(textIndent = TextIndent(firstLine = 30.sp))

        assertThat(intrinsics.maxIntrinsicWidth).isWithin(TOLERANCE).of(textWidthInPx + 30f)
    }

    @Test
    fun emIndent_isAddedToMaxIntrinsicWidth() {
        // The unit a bullet list uses, see Bullet.DefaultIndentation.
        val intrinsics = intrinsics(textIndent = TextIndent(firstLine = 1.em))

        assertThat(intrinsics.maxIntrinsicWidth)
            .isWithin(TOLERANCE)
            .of(textWidthInPx + fontSizeInPx)
    }

    @Test
    fun indentedText_needsTheIndentedWidth_toFitIntoASingleLine() {
        val textIndent = TextIndent(firstLine = 30.sp)

        // The width skia reports on its own, without the indentation, is one line short ...
        assertThat(lineCount(textIndent, maxWidth = textWidthInPx.toInt())).isEqualTo(2)

        // ... while the width the paragraph reports as intrinsic fits the text.
        assertThat(lineCount(textIndent, maxWidth = (textWidthInPx + 30f).toInt())).isEqualTo(1)
    }

    private fun lineCount(textIndent: TextIndent, maxWidth: Int) = Paragraph(
        paragraphIntrinsics = intrinsics(textIndent),
        constraints = Constraints(maxWidth = maxWidth),
        overflow = TextOverflow.Clip
    ).lineCount

    private fun intrinsics(textIndent: TextIndent?) = ParagraphIntrinsics(
        text = TEXT,
        style = TextStyle(
            fontSize = fontSize,
            fontFamily = fontFamilyMeasureFont,
            textIndent = textIndent
        ),
        annotations = emptyList(),
        density = defaultDensity,
        fontFamilyResolver = fontFamilyResolver
    )

    private companion object {
        // Skia drops the indentation when a single unbreakable run does not fit into what is left
        // of the width, so the text needs several words.
        const val TEXT = "abc abc"
        const val TOLERANCE = 0.001f
    }
}
