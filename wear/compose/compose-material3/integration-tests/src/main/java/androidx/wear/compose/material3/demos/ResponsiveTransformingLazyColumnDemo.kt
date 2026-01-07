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

package androidx.wear.compose.material3.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.ResponsiveItemType
import androidx.wear.compose.material3.lazy.ResponsiveTransformingLazyColumn
import androidx.wear.compose.material3.lazy.itemsIndexed
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
fun ResponsiveTransformingLazyColumnDemo() {
    val transformationSpec = rememberTransformationSpec()
    val state = rememberTransformingLazyColumnState()

    val initialItems =
        listOf(
            SampleItem.Header(1, "Main Header"), // Expect 13% top
            SampleItem.Text(2, "This is a block of text."), // Expect 13% top, 23% bottom
            SampleItem.Card(3, "Standard Card"), // Expect 23%
            SampleItem.Button(4, "Action Button"), // Expect 23%
            SampleItem.ButtonGroup(5), // Expect 23%
            SampleItem.CompactButton(6, "Compact"), // Expect 13%
            SampleItem.IconButton(7), // Expect 13% (New)
            SampleItem.TextButton(8, "TxBtn"), // Expect 13% (New)
            SampleItem.TitleCard(9, "Title Only"), // Expect 23%
            SampleItem.Header(10, "Section 2"),
            SampleItem.Button(11, "Final Action"),
        )

    var elements by remember { mutableStateOf(initialItems) }

    AppScaffold {
        ScreenScaffold(
            state,
            edgeButton = {
                EdgeButton(onClick = { elements = elements.shuffled() }) { Text("Shuffle") }
            },
        ) { contentPadding ->
            ResponsiveTransformingLazyColumn(state = state, contentPadding = contentPadding) {
                itemsIndexed(
                    items = elements,
                    key = { _, item -> item.id },
                    itemType = { _, item -> item.type },
                ) { _, item ->
                    val modifier =
                        Modifier.transformedHeight(this, transformationSpec).animateItem()
                    val transformation = SurfaceTransformation(transformationSpec)

                    when (item) {
                        is SampleItem.Header -> {
                            ListHeader(modifier = modifier) { Text(item.text) }
                        }
                        is SampleItem.Text -> {
                            Text(
                                text = item.text,
                                modifier = modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        is SampleItem.Card -> {
                            Card(
                                onClick = {},
                                modifier = modifier,
                                transformation = transformation,
                            ) {
                                Text(item.text)
                            }
                        }
                        is SampleItem.TitleCard -> {
                            TitleCard(
                                onClick = {},
                                title = { Text(item.text) },
                                modifier = modifier,
                                transformation = transformation,
                            )
                        }
                        is SampleItem.Button -> {
                            Button(
                                onClick = {},
                                modifier = modifier.fillMaxWidth(),
                                transformation = transformation,
                            ) {
                                Text(item.text)
                            }
                        }
                        is SampleItem.CompactButton -> {
                            CompactButton(
                                onClick = {},
                                modifier = modifier,
                                transformation = transformation,
                            ) {
                                Text(item.text)
                            }
                        }
                        is SampleItem.IconButton -> {
                            IconButton(onClick = {}, modifier = modifier) {
                                Icon(imageVector = Icons.Filled.Home, contentDescription = "Home")
                            }
                        }
                        is SampleItem.TextButton -> {
                            TextButton(onClick = {}, modifier = modifier) { Text(item.text) }
                        }
                        is SampleItem.ButtonGroup -> {
                            // Simulate a ButtonGroup using a Row of CompactButtons
                            Row(
                                modifier = modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CompactButton(onClick = {}, transformation = transformation) {
                                    Icon(
                                        imageVector = Icons.Filled.Home,
                                        contentDescription = "Home",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                CompactButton(onClick = {}, transformation = transformation) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Like",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SampleItem {
    val id: Int
    val type: ResponsiveItemType

    data class Header(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.ListHeader
    }

    data class Text(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.Text
    }

    data class Card(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.Card
    }

    data class TitleCard(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.Card
    }

    data class Button(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.Button
    }

    data class CompactButton(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.CompactButton
    }

    data class IconButton(override val id: Int) : SampleItem {
        override val type = ResponsiveItemType.IconButton
    }

    data class TextButton(override val id: Int, val text: String) : SampleItem {
        override val type = ResponsiveItemType.TextButton
    }

    data class ButtonGroup(override val id: Int) : SampleItem {
        override val type = ResponsiveItemType.ButtonGroup
    }
}
