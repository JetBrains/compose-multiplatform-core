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
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.material3.CardDefaults.DEFAULT_CONTENT_PADDING
import androidx.wear.protolayout.material3.CardDefaults.METADATA_TAG

/**
 * ProtoLayout Material3 clickable component card that offers a single slot to take any content.
 *
 * It can be used as the container for more opinionated Card components that take specific content
 * such as icons, images, primary label, secondary label, etc.
 *
 * The Card is Rectangle shaped with some rounded corners by default. It is highly recommended to
 * set its width and height to fill the available space, by [expand] or [weight] for optimal
 * experience across different screen sizes.
 *
 * It can be used for displaying any clickable container with additional data, text or graphics.
 *
 * @param onClick Associated [Clickable] for click events. When the card is clicked it will fire the
 *   associated action.
 * @param contentDescription The content description to be read by Talkback.
 * @param shape Defines the card's shape, in other words the corner radius for this card.
 * @param backgroundColor The color to be used as a background of this card. If the background image
 *   is also specified, it will be laid out on top of this color.
 * @param background The background object to be used behind the content in the card. It is
 *   recommended the default styling that is automatically provided by only calling
 *   [backgroundImage] with the content. It can be combined with the specified [backgroundColor]
 *   behind it.
 * @param width The width of this card. It's highly recommended to set this to [expand] or [weight]
 * @param height The height of this card. It's highly recommended to set this to [expand] or
 *   [weight]
 * @param contentPadding The inner padding used to prevent inner content from being too close to the
 *   card's edge. It's highly recommended to keep the default.
 * @param content The inner content to be put inside of this card.
 * @sample androidx.wear.protolayout.material3.samples.cardSample
 */
// TODO(b/346958146): link Card visuals in DAC
// TODO: b/373578620 - Add how corners affects margins in the layout.
public fun MaterialScope.card(
    onClick: Clickable,
    contentDescription: StringProp,
    width: ContainerDimension = wrapWithMinTapTargetDimension(),
    height: ContainerDimension = wrapWithMinTapTargetDimension(),
    shape: Corner = shapes.large,
    backgroundColor: ColorProp? = null,
    background: (MaterialScope.() -> LayoutElement)? = null,
    contentPadding: Padding = Padding.Builder().setAll(DEFAULT_CONTENT_PADDING.toDp()).build(),
    content: (MaterialScope.() -> LayoutElement)
): LayoutElement {
    val backgroundBuilder = Background.Builder().setCorner(shape)

    backgroundColor?.let { backgroundBuilder.setColor(it) }

    val modifiers =
        Modifiers.Builder()
            .setClickable(onClick)
            .setSemantics(contentDescription.buttonRoleSemantics())
            .setMetadata(METADATA_TAG.toElementMetadata())
            .setBackground(backgroundBuilder.build())

    val cardContainer = Box.Builder().setHeight(height).setWidth(width).addContent(content())

    if (background == null) {
        modifiers.setPadding(contentPadding)
        cardContainer.setModifiers(modifiers.build())
        return cardContainer.build()
    }

    return Box.Builder()
        .setModifiers(modifiers.build())
        .addContent(
            withStyle(
                    defaultBackgroundImageStyle =
                        BackgroundImageStyle(
                            width = expand(),
                            height = expand(),
                            overlayColor = colorScheme.primary.withOpacity(0.6f),
                            overlayWidth = width,
                            overlayHeight = height,
                            shape = shape,
                            contentScaleMode = LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS
                        )
                )
                .background()
        )
        .setWidth(width)
        .setHeight(height)
        .addContent(
            cardContainer
                // Padding in this case is needed on the inner content, not the whole card.
                .setModifiers(contentPadding.toModifiers())
                .build()
        )
        .build()
}

public object CardDefaults {
    internal const val METADATA_TAG: String = "CR"
    internal const val DEFAULT_CONTENT_PADDING: Int = 4
}
