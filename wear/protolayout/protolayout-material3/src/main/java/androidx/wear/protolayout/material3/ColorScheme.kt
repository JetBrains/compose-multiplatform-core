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
package androidx.wear.protolayout.material3

import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.material3.tokens.ColorTokens

/**
 * A [ColorScheme] holds all the named color parameters for a [MaterialTheme].
 *
 * Color schemes are designed to be harmonious, ensure accessible text, and distinguish UI elements
 * and surfaces from one another.
 *
 * The Material color system and custom schemes provide default values for color as a starting point
 * for customization.
 *
 * To learn more about color schemes, see
 * [Material Design Color System](https://m3.material.io/styles/color/the-color-system/color-roles).
 *
 * @property primary is the color displayed most frequently across your app’s screens and components
 * @property primaryDim is less prominent than [primary] for component backgrounds
 * @property primaryContainer is a standout container color for key components
 * @property onPrimary color is used for text and icons displayed on top of the primary color
 * @property onPrimaryContainer color (and state variants) that should be used for content on top of
 *   [primaryContainer]
 * @property secondary color provides more ways to accent and distinguish your product
 * @property secondaryDim is less prominent than [secondary] for component backgrounds
 * @property secondaryContainer is a tonal color to be used in containers
 * @property onSecondary color is used for text and icons displayed on top of the secondary color
 * @property onSecondaryContainer color (and state variants) should be used for content on top of
 *   [secondaryContainer]
 * @property tertiary color that can be used to balance primary and secondary colors, or bring
 *   heightened attention to an element
 * @property tertiaryDim is a less prominent tertiary color that can be used to balance primary and
 *   secondary colors, or bring heightened attention to an element
 * @property tertiaryContainer is a tonal color to be used in containers
 * @property onTertiary color is used for text and icons displayed on top of the tertiary color
 * @property onTertiaryContainer color (and state variants) that should be used for content on top
 *   of [tertiaryContainer]
 * @property surfaceContainerLow is a surface color used for large containment components such as
 *   Card and Button with low prominence
 * @property surfaceContainer is the main surface color that affect surfaces of components with
 *   large containment areas, such as Card and Button
 * @property surfaceContainerHigh is a surface color used for large containment components such Card
 *   and Button with high prominence
 * @property onSurface color is used for text and icons displayed on top of the surface color
 * @property onSurfaceVariant is the color for secondary text and icons on top of [surfaceContainer]
 * @property outline is the main color for primary outline components. The outline color role adds
 *   contrast for accessibility purposes.
 * @property outlineVariant is the secondary color for secondary outline components
 * @property background color that appears behind other content
 * @property onBackground color is used for text and icons displayed on top of the background color
 * @property error color that indicates remove, delete, close or dismiss actions. Added as an
 *   errorContainer alternative that is slightly less alarming and urgent color.
 * @property onError color is used for text and icons displayed on top of the error color
 * @property errorContainer is color that indicates errors or emergency actions, such as safety
 *   alerts. This color is for use-cases that are more alarming and urgent than the error color.
 * @property onErrorContainer is color used for text and icons on the errorContainer color
 */
public class ColorScheme(
    public val primary: ColorProp = argb(ColorTokens.PRIMARY),
    public val primaryDim: ColorProp = argb(ColorTokens.PRIMARY_DIM),
    public val primaryContainer: ColorProp = argb(ColorTokens.PRIMARY_CONTAINER),
    public val onPrimary: ColorProp = argb(ColorTokens.ON_PRIMARY),
    public val onPrimaryContainer: ColorProp = argb(ColorTokens.ON_PRIMARY_CONTAINER),
    public val secondary: ColorProp = argb(ColorTokens.SECONDARY),
    public val secondaryDim: ColorProp = argb(ColorTokens.SECONDARY_DIM),
    public val secondaryContainer: ColorProp = argb(ColorTokens.SECONDARY_CONTAINER),
    public val onSecondary: ColorProp = argb(ColorTokens.ON_SECONDARY),
    public val onSecondaryContainer: ColorProp = argb(ColorTokens.ON_SECONDARY_CONTAINER),
    public val tertiary: ColorProp = argb(ColorTokens.TERTIARY),
    public val tertiaryDim: ColorProp = argb(ColorTokens.TERTIARY_DIM),
    public val tertiaryContainer: ColorProp = argb(ColorTokens.TERTIARY_CONTAINER),
    public val onTertiary: ColorProp = argb(ColorTokens.ON_TERTIARY),
    public val onTertiaryContainer: ColorProp = argb(ColorTokens.ON_TERTIARY_CONTAINER),
    public val surfaceContainerLow: ColorProp = argb(ColorTokens.SURFACE_CONTAINER_LOW),
    public val surfaceContainer: ColorProp = argb(ColorTokens.SURFACE_CONTAINER),
    public val surfaceContainerHigh: ColorProp = argb(ColorTokens.SURFACE_CONTAINER_HIGH),
    public val onSurface: ColorProp = argb(ColorTokens.ON_SURFACE),
    public val onSurfaceVariant: ColorProp = argb(ColorTokens.ON_SURFACE_VARIANT),
    public val outline: ColorProp = argb(ColorTokens.OUTLINE),
    public val outlineVariant: ColorProp = argb(ColorTokens.OUTLINE_VARIANT),
    public val background: ColorProp = argb(ColorTokens.BACKGROUND),
    public val onBackground: ColorProp = argb(ColorTokens.ON_BACKGROUND),
    public val error: ColorProp = argb(ColorTokens.ERROR),
    public val onError: ColorProp = argb(ColorTokens.ON_ERROR),
    public val errorContainer: ColorProp = argb(ColorTokens.ERROR_CONTAINER),
    public val onErrorContainer: ColorProp = argb(ColorTokens.ON_ERROR_CONTAINER),
)
