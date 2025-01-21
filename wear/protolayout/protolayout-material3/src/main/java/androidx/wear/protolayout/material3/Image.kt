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
import androidx.wear.protolayout.DimensionBuilders.ImageDimension
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.ContentScaleMode
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers

/**
 * Returns the image background with the defined style.
 *
 * Material components provide proper defaults for the background image. In order to take advantage
 * of those defaults, this should be used with the resource ID only: `backgroundImage("id")`.
 *
 * @param protoLayoutResourceId The protolayout resource id of the icon. Node that, this is not an
 *   Android resource id.
 * @param width The width of an image. Usually, this matches the width of the parent component this
 *   is used in.
 * @param height The height of an image. Usually, this matches the height of the parent component
 *   this is used in.
 * @param overlayColor The color used to provide the overlay over the image for better readability.
 *   It's recommended to use [ColorScheme.background] color with 60% opacity.
 * @param overlayWidth The width of the overlay on top of the image background
 * @param overlayHeight The height of the overlay on top of the image background
 * @param shape The shape of the corners for the image
 * @param contentScaleMode The content scale mode for the image to define how image will adapt to
 *   the given size
 */
public fun MaterialScope.backgroundImage(
    protoLayoutResourceId: String,
    width: ImageDimension = defaultBackgroundImageStyle.width,
    height: ImageDimension = defaultBackgroundImageStyle.height,
    overlayColor: ColorProp = defaultBackgroundImageStyle.overlayColor,
    overlayWidth: ContainerDimension = defaultBackgroundImageStyle.overlayWidth,
    overlayHeight: ContainerDimension = defaultBackgroundImageStyle.overlayHeight,
    shape: Corner = defaultBackgroundImageStyle.shape,
    @ContentScaleMode contentScaleMode: Int = defaultBackgroundImageStyle.contentScaleMode,
): LayoutElement =
    Box.Builder()
        .setWidth(overlayWidth)
        .setHeight(overlayHeight)
        // Image content
        .addContent(
            Image.Builder()
                .setWidth(width)
                .setHeight(height)
                .setModifiers(Modifiers.Builder().setBackground(shape.toBackground()).build())
                .setResourceId(protoLayoutResourceId)
                .setContentScaleMode(contentScaleMode)
                .build()
        )
        // Overlay above it for contrast
        .addContent(
            Box.Builder()
                .setWidth(overlayWidth)
                .setHeight(overlayHeight)
                .setModifiers(
                    Modifiers.Builder().setBackground(overlayColor.toBackground()).build()
                )
                .build()
        )
        .build()
