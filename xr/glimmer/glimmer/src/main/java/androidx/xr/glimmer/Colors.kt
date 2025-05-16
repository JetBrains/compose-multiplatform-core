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

package androidx.xr.glimmer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A set of named color parameters for a [GlimmerTheme].
 *
 * @property primary The primary color is typically used as an accent color for important actions
 *   and information.
 * @property onPrimary Color used for text and icons displayed on top of the primary color.
 * @property secondary The secondary color provides more ways to accent and distinguish your
 *   product.
 * @property onSecondary Color used for text and icons displayed on top of the secondary color.
 * @property positive The positive color is used to indicate positive or affirmative actions, such
 *   as a confirmation button.
 * @property onPositive Color used for text and icons displayed on top of the positive color.
 * @property negative The negative color is used to indicate negative actions, such as a cancel
 *   button.
 * @property onNegative Color used for text and icons displayed on top of the negative color.
 * @property surface The surface color that affect surfaces of components, such as buttons, cards,
 *   and list items.
 * @property onSurface Color used for text and icons displayed on top of the surface color.
 * @property surfaceLow Another option for a color with similar uses of [surface].
 * @property outline Subtle color used for boundaries. This color helps to add contrast around
 *   components for accessibility purposes.
 * @property outlineVariant Utility color used for boundaries for decorative elements when strong
 *   contrast is not required.
 */
@Immutable
public class Colors(
    public val primary: Color = Color(0xFFA8C7FA),
    public val onPrimary: Color = Color.Black,
    public val secondary: Color = Color(0xFF4C88E9),
    public val onSecondary: Color = Color.Black,
    public val positive: Color = Color(0xFF4CE995),
    public val onPositive: Color = Color.Black,
    public val negative: Color = Color(0xFFF57084),
    public val onNegative: Color = Color.Black,
    public val surface: Color = Color.Black,
    public val onSurface: Color = Color.White,
    public val surfaceLow: Color = Color(0xFF4F4F4F),
    public val outline: Color = Color(0xFF606164),
    public val outlineVariant: Color = Color(0xFF42434A),
) {

    /** Returns a copy of this Colors, optionally overriding some of the values. */
    public fun copy(
        primary: Color = this.primary,
        onPrimary: Color = this.onPrimary,
        secondary: Color = this.secondary,
        onSecondary: Color = this.onSecondary,
        positive: Color = this.positive,
        onPositive: Color = this.onPositive,
        negative: Color = this.negative,
        onNegative: Color = this.onNegative,
        surface: Color = this.surface,
        onSurface: Color = this.onSurface,
        surfaceLow: Color = this.surfaceLow,
        outline: Color = this.outline,
        outlineVariant: Color = this.outlineVariant,
    ): Colors =
        Colors(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            positive = positive,
            onPositive = onPositive,
            negative = negative,
            onNegative = onNegative,
            surface = surface,
            onSurface = onSurface,
            surfaceLow = surfaceLow,
            outline = outline,
            outlineVariant = outlineVariant,
        )

    override fun toString(): String {
        return "Colors(primary=$primary, onPrimary=$onPrimary, secondary=$secondary, onSecondary=$onSecondary, positive=$positive, onPositive=$onPositive, negative=$negative, onNegative=$onNegative, surface=$surface, onSurface=$onSurface, surfaceLow=$surfaceLow, outline=$outline, outlineVariant=$outlineVariant)"
    }
}
