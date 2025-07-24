/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.layout

import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetToFocusedRectTests {
    @Test
    fun calculateVerticalAdjustedToFocusedRectOffset() = with(Density(density = 2f)) {
        val size = DpSize(60.dp, 100.dp).toSize().toIntSize()
        val rect = DpRect(top = 20.dp, bottom = 80.dp, left = 5.dp, right = 55.dp).toRect()

        // No insets
        assertEquals(
            IntOffset.Zero,
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 0.dp, bottom = 0.dp)
            )
        )

        // Small insets
        assertEquals(
            IntOffset.Zero,
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 20.dp, bottom = 20.dp)
            )
        )

        // Moderate insets on top
        assertEquals(
            IntOffset(0, 15.dp.roundToPx()),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 35.dp, bottom = 0.dp)
            )
        )

        // Moderate insets on bottom
        assertEquals(
            IntOffset(0, -15.dp.roundToPx()),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 0.dp, bottom = 35.dp)
            )
        )

        // Large insets on top
        assertEquals(
            IntOffset(0, 20.dp.roundToPx()),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 50.dp, bottom = 0.dp)
            )
        )

        // Moderate insets on bottom
        assertEquals(
            IntOffset(0, -20.dp.roundToPx()),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 0.dp, bottom = 50.dp)
            )
        )

        // Large insets on both sides
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(top = 50.dp, bottom = 50.dp)
            )
        )

        // Focused rect is above the canvas
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = DpRect(
                    top = (-20).dp, bottom = (-10).dp, left = 5.dp, right = 55.dp
                ).toRect(),
                size = size,
                insets = PlatformInsets.Zero
            )
        )

        // Focused rect is below the canvas
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = DpRect(
                    top = (100).dp, bottom = (110).dp, left = 5.dp, right = 55.dp
                ).toRect(),
                size = size,
                insets = PlatformInsets.Zero
            )
        )
    }

    @Test
    fun calculateHorizontalAdjustedToFocusedRectOffset() = with(Density(density = 2f)) {
        val size = DpSize(100.dp, 60.dp).toSize().toIntSize()
        val rect = DpRect(top = 5.dp, bottom = 55.dp, left = 20.dp, right = 80.dp).toRect()

        // No insets
        assertEquals(
            IntOffset.Zero,
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 0.dp, right = 0.dp)
            )
        )

        // Small insets
        assertEquals(
            IntOffset.Zero,
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 20.dp, right = 20.dp)
            )
        )

        // Moderate insets on left
        assertEquals(
            IntOffset(15.dp.roundToPx(), 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 35.dp, right = 0.dp)
            )
        )

        // Moderate insets on right
        assertEquals(
            IntOffset(-15.dp.roundToPx(), 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 0.dp, right = 35.dp)
            )
        )

        // Large insets on left
        assertEquals(
            IntOffset(20.dp.roundToPx(), 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 50.dp, right = 0.dp)
            )
        )

        // Moderate insets on right
        assertEquals(
            IntOffset(-20.dp.roundToPx(), 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 0.dp, right = 50.dp)
            )
        )

        // Large insets on both sides
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = rect,
                size = size,
                insets = PlatformInsets(left = 50.dp, right = 50.dp)
            )
        )

        // Focused rect is left outside canvas
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = DpRect(
                    top = 5.dp, bottom = 55.dp, left = (-20).dp, right = (-10).dp
                ).toRect(),
                size = size,
                insets = PlatformInsets.Zero
            )
        )

        // Focused rect is right outside canvas
        assertEquals(
            IntOffset(0, 0),
            adjustedToFocusedRectOffset(
                focusedRect = DpRect(
                    top = 5.dp, bottom = 55.dp, left = 110.dp, right = 120.dp
                ).toRect(),
                size = size,
                insets = PlatformInsets.Zero
            )
        )
    }
}