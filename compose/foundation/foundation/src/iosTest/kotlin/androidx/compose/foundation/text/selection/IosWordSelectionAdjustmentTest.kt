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

import androidx.compose.foundation.SkikoComposeTestBase
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosWordSelectionAdjustmentTest : SkikoComposeTestBase() {
    @Test
    fun chineseUsesLinguisticWordBoundary() {
        assertEquals(TextRange(4, 6), iosWordBoundary("我在学习中文", 5))
    }

    @Test
    fun japaneseUsesLinguisticWordBoundary() {
        assertEquals(TextRange(3, 5), iosWordBoundary("これは日本語です", 4))
    }

    @Test
    fun cjkAfterEmojiUsesUtf16Offsets() {
        assertEquals(TextRange(6, 8), iosWordBoundary("😀我在学习中文", 7))
    }

    @Test
    fun latinUsesLinguisticWordBoundary() {
        assertEquals(TextRange(10, 18), iosWordBoundary("this is a language", 12))
    }

    @Test
    fun whitespaceHasNoLinguisticWordBoundary() {
        assertNull(iosWordBoundary("word word", 4))
    }

    @Test
    fun adjustmentExpandsCollapsedSelectionToNativeWord() {
        val selection = IosWordSelectionAdjustment.adjust(selectionLayout("我在学习中文", 5, 5))

        assertEquals(TextRange(4, 6), selection.toTextRange())
        assertFalse(selection.handlesCrossed)
    }

    @Test
    fun adjustmentExpandsBothSelectionEnds() {
        val selection = IosWordSelectionAdjustment.adjust(selectionLayout("中文 日本", 1, 4))

        assertEquals(TextRange(0, 5), selection.toTextRange())
        assertFalse(selection.handlesCrossed)
    }

    @Test
    fun adjustmentPreservesReversedSelection() {
        val selection = IosWordSelectionAdjustment.adjust(selectionLayout("中文 日本", 4, 1))

        assertEquals(TextRange(5, 0), selection.toTextRange())
        assertTrue(selection.handlesCrossed)
    }

    @Test
    fun adjustmentFallsBackToLayoutBoundaryForWhitespace() {
        val layout = selectionLayout("word word", 4, 4)

        assertEquals(
            SelectionAdjustment.Word.adjust(layout),
            IosWordSelectionAdjustment.adjust(layout),
        )
    }

    @Test
    fun adjustmentFallsBackForEachAnchorIndependently() {
        val layout = selectionLayout("中文 ", 1, 2)
        val selection = IosWordSelectionAdjustment.adjust(layout)

        assertEquals(0, selection.start.offset)
        assertEquals(SelectionAdjustment.Word.adjust(layout).end, selection.end)
        assertFalse(selection.handlesCrossed)
    }

    @Test
    fun adjustmentPreservesStartAnchorOutsideSelectable() {
        val textFieldLayout = selectionLayout("我在学习中文", 5, 5)
        val layout =
            object : SelectionLayout by textFieldLayout {
                override val startSlot = 0
                override val crossStatus = CrossStatus.NOT_CROSSED
            }

        val selection = IosWordSelectionAdjustment.adjust(layout)

        assertEquals(TextRange(5, 6), selection.toTextRange())
        assertFalse(selection.handlesCrossed)
    }

    @Test
    fun adjustmentPreservesEndAnchorOutsideSelectable() {
        val textFieldLayout = selectionLayout("我在学习中文", 5, 5)
        val layout =
            object : SelectionLayout by textFieldLayout {
                override val endSlot = 2
                override val crossStatus = CrossStatus.NOT_CROSSED
            }

        val selection = IosWordSelectionAdjustment.adjust(layout)

        assertEquals(TextRange(4, 5), selection.toTextRange())
        assertFalse(selection.handlesCrossed)
    }

    @Test
    fun adjustmentPreservesRtlAnchorDirectionAndSelectableId() {
        val layout = selectionLayout("שלום", 1, 1)
        val selection = IosWordSelectionAdjustment.adjust(layout)

        assertEquals(TextRange(0, 4), selection.toTextRange())
        assertEquals(ResolvedTextDirection.Rtl, selection.start.direction)
        // The text-end anchor keeps the direction and identity supplied by the layout.
        assertEquals(SelectionAdjustment.Word.adjust(layout), selection)
    }

    private fun selectionLayout(text: String, start: Int, end: Int): SelectionLayout {
        val textMeasurer =
            TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        return getTextFieldSelectionLayout(
            layoutResult = textMeasurer.measure(text),
            rawStartHandleOffset = start,
            rawEndHandleOffset = end,
            rawPreviousHandleOffset = -1,
            previousSelectionRange = TextRange.Zero,
            isStartOfSelection = true,
            isStartHandle = false,
        )
    }
}
