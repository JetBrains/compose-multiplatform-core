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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextInputRangeTest {
    @Test
    fun forwardRangeKeepsItsOrder() {
        val range = TextRange(3, 10).toUITextRange()

        assertEquals(3, (range.start() as TextInputPosition).position)
        assertEquals(10, (range.end() as TextInputPosition).position)
        assertFalse(range.isEmpty())
    }

    @Test
    fun reversedRangeStartsAtItsLowerOffset() {
        // A backwards selection, such as one dragged from right to left.
        val range = TextRange(10, 3).toUITextRange()

        assertEquals(3, (range.start() as TextInputPosition).position)
        assertEquals(10, (range.end() as TextInputPosition).position)
        assertFalse(range.isEmpty())
    }

    @Test
    fun collapsedRangeIsEmpty() {
        val range = TextRange(5).toUITextRange()

        assertEquals(5, (range.start() as TextInputPosition).position)
        assertEquals(5, (range.end() as TextInputPosition).position)
        assertTrue(range.isEmpty())
    }
}
