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

package androidx.wear.compose.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.wear.compose.foundation.lazy.LocalTransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.scrollTransform

/**
 * Applies a container style to the current composable and Material 3 Motion if in the scope of a
 * TransformingLazyColumn
 *
 * This modifier provides a background using the given [Painter] and clips it to the given [Shape].
 * If a border is provided, it will be applied around the shape.
 *
 * For items within a TransformingLazyColumn, it applies a scrolling transformation to the container
 * based on its position within the lazy column. For other composables, it simply applies the
 * background and border.
 *
 * @param painter The painter used to draw the background of the container.
 * @param shape The shape of the container. Defaults to [RectangleShape].
 * @param border The border stroke to apply to the container. If null, no border is drawn.
 * @return A modifier that applies the container style.
 */
@Composable
internal fun Modifier.container(
    painter: Painter,
    shape: Shape = RectangleShape,
    border: BorderStroke? = null
): Modifier {
    val borderModifier = if (border != null) border(border = border, shape = shape) else this
    val itemScope = LocalTransformingLazyColumnItemScope.current
    return itemScope?.let { tlcScope -> scrollTransform(tlcScope, shape, painter, border) }
        ?: borderModifier
            .clip(shape = shape)
            .paint(painter = painter, contentScale = ContentScale.Crop)
}

/**
 * Applies a container style to the current composable and Material 3 Motion if in the scope of a
 * TransformingLazyColumn
 *
 * This modifier provides a background using the given [Color] and clips it to the given [Shape]. If
 * a border is provided, it will be applied around the shape.
 *
 * For items within a TransformingLazyColumn, it applies a scrolling transformation to the container
 * based on its position within the lazy column. For other composables, it simply applies the
 * background and border.
 *
 * @param color The color used to draw the background of the container.
 * @param shape The shape of the container. Defaults to [RectangleShape].
 * @param border The border stroke to apply to the container. If null, no border is drawn.
 * @return A modifier that applies the container style.
 */
@Composable
internal fun Modifier.container(
    color: Color,
    shape: Shape = RectangleShape,
    border: BorderStroke? = null
): Modifier {
    val borderModifier = if (border != null) border(border = border, shape = shape) else this
    val itemScope = LocalTransformingLazyColumnItemScope.current
    return itemScope?.let { tlcScope ->
        scrollTransform(tlcScope, shape, ColorPainter(color), border)
    } ?: borderModifier.clip(shape = shape).background(color = color)
}
