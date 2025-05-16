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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalOf

/**
 * Calculates the preferred content color for [backgroundColor]. This will return either
 * [Color.White] or [Color.Black], depending on the luminance of the background color.
 *
 * @see ModifierLocalContentColor
 * @see surface
 */
public fun calculateContentColor(backgroundColor: Color): Color =
    if (backgroundColor.luminance() < LuminanceContrastRatioBreakpoint) Color.White else Color.Black

/**
 * ModifierLocal containing the preferred content color for text and iconography within a surface.
 * Most surfaces should be [Color.Black], so content color is typically [Color.White]. In a few
 * cases where surfaces are filled with a different color, the content color may be [Color.Black] to
 * improve contrast. For cases where higher emphasis is required, content color may be a different
 * color from the theme, such as [Colors.primary].
 *
 * Content color is automatically provided by [surface], and calculated from the provided background
 * color by default. To manually calculate the default content color for a provided background
 * color, use [calculateContentColor].
 */
public val ModifierLocalContentColor: ProvidableModifierLocal<Color> = modifierLocalOf {
    Color.White
}

/**
 * Contrast ratio is defined as (L1 + 0.05) / (L2 + 0.05) where L1 is the relative luminance of the
 * lighter color and L2 is the relative luminance of the darker color. ([WCAG
 * 2.0](https://www.w3.org/TR/UNDERSTANDING-WCAG20/visual-audio-contrast-contrast.html#contrast-ratiodef))
 *
 * The luminance of white is 1, and the luminance of black is 0 - so essentially we need to compare
 * whether (1.05) / (L + 0.05) > (L + 0.05) / (0.05) - this can be simplified down to L < 0.179129.
 */
private const val LuminanceContrastRatioBreakpoint = 0.179129f
