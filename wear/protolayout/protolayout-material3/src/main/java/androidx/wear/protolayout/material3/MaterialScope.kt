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

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.ImageDimension
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.LayoutElementBuilders.TextAlignment
import androidx.wear.protolayout.LayoutElementBuilders.TextOverflow
import androidx.wear.protolayout.material3.Typography.TypographyToken
import androidx.wear.protolayout.material3.tokens.ColorTokens

/**
 * Receiver scope which is used by all ProtoLayout Material3 components and layout to support
 * opinionated defaults and to provide the global information for styling Material3 components.
 *
 * The MaterialScope includes:
 * * theme, which is used to retrieve the color, typography or shape values.
 * * [DeviceParameters], which contains screen size, font scale, renderer schema version etc.
 * * Default usage of system theme, with the option to opt out.
 */
// TODO: b/352308384 - Add helper to read the exported Json or XML file from the Material Theme
//    Builder tool.
// TODO: b/350927030 - Customization setters of shape and typography, which are not fully
// TODO: b/352308384 - Add helper to read the exported Json or XML file from the Material Theme
//    Builder tool.
// TODO: b/350927030 - Customization setters of shape and typography, which are not fully
// customizable.
// TODO: b/369116159 - Add samples on usage.
@MaterialScopeMarker
public open class MaterialScope
/**
 * @param context The Android Context for the Tile service
 * @param deviceConfiguration The device parameters for where the components will be rendered
 * @param allowDynamicTheme If dynamic colors theme should be used on components, meaning that
 * @param theme The theme to be used. If not set, default Material theme will be applied
 * @param defaultTextElementStyle The opinionated text style that text component can use as defaults
 * @param defaultIconStyle The opinionated icon style that icon component can use as defaults
 */
internal constructor(
    internal val context: Context,
    /** The device parameters for where the components will be rendered. */
    public val deviceConfiguration: DeviceParameters,
    internal val allowDynamicTheme: Boolean,
    internal val theme: MaterialTheme,
    internal val defaultTextElementStyle: TextElementStyle,
    internal val defaultIconStyle: IconStyle
) {
    /** Color Scheme used within this scope and its components. */
    public val colorScheme: ColorScheme = theme.colorScheme

    /** Shapes theme used within this scope and its components. */
    public val shapes: Shapes = theme.shapes

    internal fun withStyle(
        defaultTextElementStyle: TextElementStyle = this.defaultTextElementStyle,
        defaultIconStyle: IconStyle = this.defaultIconStyle
    ): MaterialScope =
        MaterialScope(
            context = context,
            deviceConfiguration = deviceConfiguration,
            theme = theme,
            allowDynamicTheme = allowDynamicTheme,
            defaultTextElementStyle = defaultTextElementStyle,
            defaultIconStyle = defaultIconStyle
        )
}

/**
 * Creates a top-level receiver scope [MaterialScope] that calls the given [layout] to support for
 * opinionated defaults and building Material3 components and layout, with default dynamic theme.
 *
 * @param context The Android Context for the Tile service
 * @param deviceConfiguration The device parameters for where the components will be rendered
 * @param allowDynamicTheme If dynamic colors theme should be used on components, meaning that
 *   colors will follow the system theme if enabled on the device. If not set, defaults to using the
 *   system theme
 * @param defaultColorScheme Color Scheme with static colors. The color theme to be used, when
 *   `allowDynamicTheme` is false, or when dynamic theming is disabled by the system or user. If not
 *   set, defaults to default theme.
 * @param layout Scoped slot for the content of layout to be displayed
 */
// TODO: b/370976767 - Specify in docs that MaterialTileService should be used instead of using this
// directly.
public fun materialScope(
    context: Context,
    deviceConfiguration: DeviceParameters,
    allowDynamicTheme: Boolean = true,
    defaultColorScheme: ColorScheme = ColorScheme(),
    layout: MaterialScope.() -> LayoutElement
): LayoutElement =
    MaterialScope(
            context = context,
            deviceConfiguration = deviceConfiguration,
            allowDynamicTheme = allowDynamicTheme,
            theme =
                MaterialTheme(
                    colorScheme =
                        if (allowDynamicTheme) {
                            dynamicColorScheme(
                                context = context,
                                defaultColorScheme = defaultColorScheme
                            )
                        } else {
                            defaultColorScheme
                        }
                ),
            defaultTextElementStyle = TextElementStyle(),
            defaultIconStyle = IconStyle()
        )
        .layout()

@DslMarker public annotation class MaterialScopeMarker

internal class TextElementStyle(
    @TypographyToken val typography: Int = Typography.BODY_MEDIUM,
    val color: ColorProp = argb(ColorTokens.PRIMARY),
    val italic: Boolean = false,
    val underline: Boolean = false,
    val scalable: Boolean = TypographyFontSelection.getFontScalability(typography),
    val maxLines: Int = 1,
    @TextAlignment val multilineAlignment: Int = TEXT_ALIGN_CENTER,
    @TextOverflow val overflow: Int = TEXT_OVERFLOW_ELLIPSIZE,
)

internal class IconStyle(
    val size: ImageDimension = 24.toDp(),
    val tintColor: ColorProp = argb(ColorTokens.PRIMARY),
)
