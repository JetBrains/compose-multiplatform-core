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

import android.R
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb

/**
 * Creates a dynamic color scheme.
 *
 * Use this function to create a color scheme based on the current watchface. If the user changes
 * the watchface colors, this color scheme will change accordingly. This function checks whether the
 * dynamic color scheme can be used and returns [defaultColorScheme] otherwise.
 *
 * @param context The context required to get system resource data.
 * @param defaultColorScheme The fallback [ColorScheme] to return if the dynamic color scheme is
 *   switched off or unavailable on this device.
 */
public fun dynamicColorScheme(
    context: Context,
    defaultColorScheme: ColorScheme = ColorScheme()
): ColorScheme =
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isDynamicColorSchemeEnabled(context)
    ) {
        // From
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/res/res/values/public-final.xml;l=3500;drc=2a8b6a18e0b7f696013ffede0cc0ab1904864d09
        // TODO: b/340192801 - Confirm once it's fully supported in system.
        ColorScheme(
            primary = getColorProp(context, R.color.system_primary_fixed),
            primaryDim = getColorProp(context, R.color.system_primary_fixed_dim),
            primaryContainer = getColorProp(context, R.color.system_primary_container_dark),
            onPrimary = getColorProp(context, R.color.system_on_primary_fixed),
            onPrimaryContainer = getColorProp(context, R.color.system_on_primary_container_dark),
            secondary = getColorProp(context, R.color.system_secondary_fixed),
            secondaryDim = getColorProp(context, R.color.system_secondary_fixed_dim),
            secondaryContainer = getColorProp(context, R.color.system_secondary_container_dark),
            onSecondary = getColorProp(context, R.color.system_on_secondary_fixed),
            onSecondaryContainer =
                getColorProp(context, R.color.system_on_secondary_container_dark),
            tertiary = getColorProp(context, R.color.system_tertiary_fixed),
            tertiaryDim = getColorProp(context, R.color.system_tertiary_fixed_dim),
            tertiaryContainer = getColorProp(context, R.color.system_tertiary_container_dark),
            onTertiary = getColorProp(context, R.color.system_on_tertiary_fixed),
            onTertiaryContainer = getColorProp(context, R.color.system_on_tertiary_container_dark),
            surfaceContainerLow = getColorProp(context, R.color.system_surface_container_low_dark),
            surfaceContainer = getColorProp(context, R.color.system_surface_container_dark),
            surfaceContainerHigh =
                getColorProp(context, R.color.system_surface_container_high_dark),
            onSurface = getColorProp(context, R.color.system_on_surface_dark),
            onSurfaceVariant = getColorProp(context, R.color.system_on_surface_variant_dark),
            outline = getColorProp(context, R.color.system_outline_dark),
            outlineVariant = getColorProp(context, R.color.system_outline_variant_dark),
            background = getColorProp(context, R.color.system_background_dark),
            onBackground = getColorProp(context, R.color.system_on_background_dark),
            error = getColorProp(context, R.color.system_error_dark),
            onError = getColorProp(context, R.color.system_on_error_dark),
            errorContainer = getColorProp(context, R.color.system_error_container_dark),
            onErrorContainer = getColorProp(context, R.color.system_on_error_container_dark),
        )
    } else {
        defaultColorScheme
    }

/**
 * Returns whether the dynamic colors scheme (colors following the system theme) is enabled.
 *
 * If enabled, and elements or [MaterialScope] are opted in to using dynamic theme, colors will
 * change whenever system theme changes.
 */
public fun isDynamicColorSchemeEnabled(context: Context): Boolean {
    val overlaySetting: String? =
        Settings.Secure.getString(context.contentResolver, THEME_CUSTOMIZATION_OVERLAY_PACKAGES)
    return (!overlaySetting.isNullOrEmpty() && overlaySetting != "{}")
}

/** This maps to `android.provider.Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES`. */
@VisibleForTesting
internal const val THEME_CUSTOMIZATION_OVERLAY_PACKAGES: String =
    "theme_customization_overlay_packages"

/** Retrieves the [ColorProp] from the dynamic system theme with the given color token name. */
private fun getColorProp(context: Context, @ColorRes id: Int): ColorProp =
    argb(context.resources.getColor(id, context.theme))
