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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Card is a component used to group related information into a single digestible unit. A card can
 * adapt to display a wide range of content, from simple text blurbs to more complex summaries with
 * multiple elements. A card contains text [content], and may also have any combination of [title],
 * [subtitle], [leadingIcon], and [trailingIcon]. If specified, [title] is placed on top of the
 * [subtitle], which is placed on top of the [content]. A card fills the maximum width available by
 * default.
 *
 * This Card is focusable - see the other [Card] overload for a clickable Card.
 *
 * A simple Card with just text:
 *
 * @sample androidx.xr.glimmer.samples.CardSample
 *
 * A Card with a trailing icon:
 *
 * @sample androidx.xr.glimmer.samples.CardWithTrailingIconSample
 *
 * A Card with a title, subtitle, and a leading icon:
 *
 * @sample androidx.xr.glimmer.samples.CardWithTitleAndSubtitleAndLeadingIconSample
 * @param modifier the [Modifier] to be applied to this card
 * @param title optional title to be placed above [subtitle] and [content]
 * @param subtitle optional subtitle to be placed above [content], below [title]
 * @param leadingIcon optional leading icon to be placed before [content]. This is typically an
 *   [Icon]. [Colors.primary] is provided as the content color by default.
 * @param trailingIcon optional trailing icon to be placed after [content]. This is typically an
 *   [Icon]. [Colors.primary] is provided as the content color by default.
 * @param shape the [Shape] used to clip this card, and also used to draw the background and border
 * @param color background color of this card
 * @param contentColor content color used by components inside [content], [title] and [subtitle].
 * @param border the border to draw around this card
 * @param contentPadding the spacing values to apply internally between the container and the
 *   content. Note that there is additional padding applied around the content / text / icons inside
 *   a card, this only affects the outermost content padding.
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting Interactions for this card. You can use this to change the card's appearance or
 *   preview the card in different states. Note that if `null` is provided, interactions will still
 *   happen internally.
 * @param content the main content / body text to display inside this card. This is recommended to
 *   be limited to 10 lines of text.
 */
@Composable
public fun Card(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    subtitle: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = GlimmerTheme.shapes.medium,
    color: Color = GlimmerTheme.colors.surface,
    contentColor: Color = calculateContentColor(color),
    border: BorderStroke? = SurfaceDefaults.border(),
    contentPadding: PaddingValues = CardDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    CardImpl(
        modifier = modifier,
        onClick = null,
        focusable = true,
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Card is a component used to group related information into a single digestible unit. A card can
 * adapt to display a wide range of content, from simple text blurbs to more complex summaries with
 * multiple elements. A card contains text [content], and may also have any combination of [title],
 * [subtitle], [leadingIcon], and [trailingIcon]. If specified, [title] is placed on top of the
 * [subtitle], which is placed on top of the [content]. A card fills the maximum width available by
 * default.
 *
 * This Card is focusable and clickable - see the other [Card] overload for a Card that is only
 * focusable.
 *
 * A simple clickable Card with just text:
 *
 * @sample androidx.xr.glimmer.samples.ClickableCardSample
 *
 * A clickable Card with a trailing icon:
 *
 * @sample androidx.xr.glimmer.samples.ClickableCardWithTrailingIconSample
 *
 * A clickable Card with a title, subtitle, and a leading icon:
 *
 * @sample androidx.xr.glimmer.samples.ClickableCardWithTitleAndSubtitleAndLeadingIconSample
 * @param onClick called when this card item is clicked
 * @param modifier the [Modifier] to be applied to this card
 * @param title optional title to be placed above [subtitle] and [content]
 * @param subtitle optional subtitle to be placed above [content], below [title]
 * @param leadingIcon optional leading icon to be placed before [content]. This is typically an
 *   [Icon]. [Colors.primary] is provided as the content color by default.
 * @param trailingIcon optional trailing icon to be placed after [content]. This is typically an
 *   [Icon]. [Colors.primary] is provided as the content color by default.
 * @param shape the [Shape] used to clip this card, and also used to draw the background and border
 * @param color background color of this card
 * @param contentColor content color used by components inside [content], [title] and [subtitle].
 * @param border the border to draw around this card
 * @param contentPadding the spacing values to apply internally between the container and the
 *   content. Note that there is additional padding applied around the content / text / icons inside
 *   a card, this only affects the outermost content padding.
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting Interactions for this card. You can use this to change the card's appearance or
 *   preview the card in different states. Note that if `null` is provided, interactions will still
 *   happen internally.
 * @param content the main content / body text to display inside this card. This is recommended to
 *   be limited to 10 lines of text.
 */
@Composable
public fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    subtitle: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = GlimmerTheme.shapes.medium,
    color: Color = GlimmerTheme.colors.surface,
    contentColor: Color = calculateContentColor(color),
    border: BorderStroke? = SurfaceDefaults.border(),
    contentPadding: PaddingValues = CardDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    CardImpl(
        modifier = modifier,
        onClick = onClick,
        focusable = true,
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun CardImpl(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    focusable: Boolean,
    title: @Composable (() -> Unit)?,
    subtitle: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    shape: Shape,
    color: Color,
    contentColor: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    content: @Composable () -> Unit,
) {
    val colors = GlimmerTheme.colors
    val iconSize = GlimmerTheme.iconSizes.large
    val typography = GlimmerTheme.typography
    val surfaceModifier =
        if (onClick != null) {
            Modifier.surface(
                onClick = onClick,
                shape = shape,
                color = color,
                contentColor = contentColor,
                border = border,
                interactionSource = interactionSource,
            )
        } else {
            Modifier.surface(
                focusable = focusable,
                shape = shape,
                color = color,
                contentColor = contentColor,
                border = border,
                interactionSource = interactionSource,
            )
        }
    Row(
        modifier =
            modifier
                .then(surfaceModifier)
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinimumHeight)
                .padding(contentPadding)
                .padding(InnerPadding),
        verticalAlignment = CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.align(Alignment.Top)
                    .padding(end = IconSpacing)
                    .contentColorProvider(colors.primary),
                contentAlignment = Alignment.TopStart,
            ) {
                CompositionLocalProvider(LocalIconSize provides iconSize, content = leadingIcon)
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TextVerticalSpacing),
        ) {
            if (title != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides typography.bodyMedium,
                    content = title,
                )
            }

            if (subtitle != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides typography.bodySmall,
                    content = subtitle,
                )
            }

            CompositionLocalProvider(
                LocalTextStyle provides typography.bodySmall,
                content = content,
            )
        }
        if (trailingIcon != null) {
            Box(
                Modifier.align(Alignment.Top)
                    .padding(start = IconSpacing)
                    .contentColorProvider(colors.primary),
                Alignment.TopEnd,
            ) {
                CompositionLocalProvider(LocalIconSize provides iconSize, content = trailingIcon)
            }
        }
    }
}

/** Default values used for [Card] */
public object CardDefaults {
    /**
     * Default content padding used for a [Card]
     *
     * This affects the outermost content padding applied around header images and the content
     * container. Note that there is additional padding applied around the content / text / icons
     * inside a card, this only represents the outer padding for the entire content.
     */
    public val ContentPadding: PaddingValues = PaddingValues(16.dp)
}

/** Default minimum height for a [Card] */
private val MinimumHeight = 80.dp

/** Spacing between icons and the text in a [Card] */
private val IconSpacing = 12.dp

/** Padding around the internal content (text / icons), but not added around header images. */
private val InnerPadding = 8.dp

/** Spacing between title / subtitle / body text */
private val TextVerticalSpacing = 3.dp
