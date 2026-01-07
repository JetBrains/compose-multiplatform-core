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

package androidx.xr.glimmer.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.CardDefaults
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.LocalTextStyle
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.samples.VerticalStackSample
import androidx.xr.glimmer.stack.VerticalStack

internal val StackDemos =
    listOf(
        ComposableDemo("VerticalStack fixed size") { VerticalStackFixedItemSizeDemo() },
        ComposableDemo("VerticalStack varying size") { VerticalStackVaryingItemSizeDemo() },
        ComposableDemo("VerticalStack various content") { VerticalStackVariousContentDemo() },
    )

@Composable
internal fun VerticalStackFixedItemSizeDemo() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VerticalStackSample()
    }
}

@Composable
internal fun VerticalStackVaryingItemSizeDemo() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Vertical stack with items of different sizes.
        VerticalStack(modifier = Modifier.fillMaxWidth().height(364.dp)) {
            items(10) { index ->
                Card(
                    modifier =
                        Modifier.fillMaxHeight(if (index % 2 == 0) 0.5f else 1f)
                            .itemDecoration(CardDefaults.shape)
                ) {
                    Text(
                        "Item-$index",
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerticalStackVariousContentDemo() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VerticalStack(modifier = Modifier.fillMaxWidth().height(364.dp)) {
            item {
                Card(modifier = Modifier.itemDecoration(CardDefaults.shape)) {
                    Text(
                        "This is a card",
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
            item {
                Card(
                    trailingIcon = { Icon(FavoriteIcon, "Localized description") },
                    modifier = Modifier.itemDecoration(CardDefaults.shape),
                ) {
                    Text(
                        "This is a card with a trailing icon",
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
            item {
                Card(
                    title = {
                        Text(
                            "Title",
                            style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                        )
                    },
                    leadingIcon = { Icon(FavoriteIcon, "Localized description") },
                    modifier = Modifier.itemDecoration(CardDefaults.shape),
                ) {
                    Text(
                        "This is a card with a title and leading icon",
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
            item {
                Card(modifier = Modifier.itemDecoration(CardDefaults.shape)) {
                    Text(
                        "This is a card with a lot of text that will wrap to multiple lines. The maximum recommend number of lines of text for a card is 10.",
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
            item {
                Card(
                    title = {
                        Text(
                            "Title",
                            style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                        )
                    },
                    subtitle = {
                        Text(
                            "Subtitle",
                            style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                        )
                    },
                    leadingIcon = { Icon(FavoriteIcon, "Localized description") },
                    modifier = Modifier.itemDecoration(CardDefaults.shape),
                ) {
                    Text(
                        "This is a card with a lot of text that will wrap to multiple lines.",
                        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    )
                }
            }
        }
    }
}

/** Icon taken from material-icons-core */
private val FavoriteIcon: ImageVector =
    ImageVector.Builder(
            name = "Favorite",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 21.35f)
                lineToRelative(-1.45f, -1.32f)
                curveTo(5.4f, 15.36f, 2.0f, 12.28f, 2.0f, 8.5f)
                curveTo(2.0f, 5.42f, 4.42f, 3.0f, 7.5f, 3.0f)
                curveToRelative(1.74f, 0.0f, 3.41f, 0.81f, 4.5f, 2.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3.0f, 16.5f, 3.0f)
                curveTo(19.58f, 3.0f, 22.0f, 5.42f, 22.0f, 8.5f)
                curveToRelative(0.0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
                lineTo(12.0f, 21.35f)
                close()
            }
        }
        .build()
