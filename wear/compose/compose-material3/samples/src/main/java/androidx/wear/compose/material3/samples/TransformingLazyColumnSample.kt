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

package androidx.wear.compose.material3.samples

import androidx.annotation.Sampled
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.scrollTransform
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlin.random.Random
import kotlinx.coroutines.launch

@Preview
@Sampled
@Composable
fun TransformingLazyColumnScrollingSample() {
    val state = rememberTransformingLazyColumnState()
    val coroutineScope = rememberCoroutineScope()
    var expandedItemKey by remember { mutableStateOf(-1) }
    var elements by remember { mutableStateOf(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)) }

    var nextElement = 10
    fun addElement(index: Int) {
        elements =
            elements.subList(0, index) +
                listOf(nextElement++) +
                elements.subList(index, elements.count())
    }

    fun rainbowColor(progress: Float): Color {
        val hue = progress * 360f
        val saturation = 1f
        val value = 1f

        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AppScaffold {
        ScreenScaffold(
            state,
            edgeButton = {
                EdgeButton(
                    onClick = {
                        addElement(elements.count())
                        coroutineScope.launch { state.scrollToItem(elements.count() - 1) }
                    }
                ) {
                    Text("Add item")
                }
            }
        ) { contentPadding ->
            val random = remember { Random }
            TransformingLazyColumn(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                items(elements, key = { it }) {
                    val index = elements.indexOf(it)
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .scrollTransform(
                                    this,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .animateItem()
                                .padding(5.dp)
                                .clickable {
                                    elements =
                                        elements.subList(0, index) +
                                            elements.subList(index + 1, elements.count())
                                }
                    ) {
                        Row(
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = spacedBy(2.dp)
                        ) {
                            Text(
                                "Item $it",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            Text("^", Modifier.clickable { addElement(index) })
                            Box(
                                Modifier.size(25.dp)
                                    .drawWithContent {
                                        with(scrollProgress) {
                                            if (isUnspecified) {
                                                return@with
                                            }
                                            drawContent()
                                            val colorProgress =
                                                (topOffsetFraction + bottomOffsetFraction) / 2f
                                            val r = size.height / 2f
                                            drawCircle(
                                                rainbowColor(colorProgress),
                                                radius = r,
                                                center = Offset(size.width - r, r)
                                            )
                                            drawCircle(
                                                rainbowColor(random.nextFloat()),
                                                radius = r / 8,
                                                center = Offset(size.width - r, r)
                                            )
                                        }
                                    }
                                    .clickable {
                                        expandedItemKey =
                                            if (expandedItemKey == it) -1
                                            else {
                                                coroutineScope.launch { state.scrollToItem(index) }
                                                it
                                            }
                                    }
                            )
                        }
                        AnimatedVisibility(expandedItemKey == it) {
                            // Expanded content goes here.
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Sampled
@Preview
@Composable
fun TransformingLazyColumnScalingMorphingEffectSample() {
    val allIngredients = listOf("2 eggs", "tomato", "cheese", "bread")
    val state = rememberTransformingLazyColumnState()
    val coroutineScope = rememberCoroutineScope()
    AppScaffold {
        ScreenScaffold(
            state,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            edgeButton = {
                EdgeButton(onClick = { coroutineScope.launch { state.scrollToItem(1) } }) {
                    Text("To top")
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                item(contentType = "header") {
                    // No modifier is applied - no Material 3 Motion.
                    ListHeader { Text("Ingredients") }
                }

                items(allIngredients, key = { it }) { ingredient ->
                    Text(
                        ingredient,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier.fillMaxWidth()
                                // Apply Material 3 Motion transformations.
                                .scrollTransform(
                                    this,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Sampled
@Preview
@Composable
fun TransformingLazyColumnReducedMotionSample() {
    var enableReduceMotion by remember { mutableStateOf(true) }
    val state = rememberTransformingLazyColumnState()
    AppScaffold {
        ScreenScaffold(
            state,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            edgeButton = {
                EdgeButton(
                    onClick = { enableReduceMotion = !enableReduceMotion },
                    buttonSize = EdgeButtonSize.Large
                ) {
                    Text("Toggle reduce motion")
                }
            }
        ) { contentPadding ->
            CompositionLocalProvider(LocalReduceMotion provides enableReduceMotion) {
                val transformationSpec = rememberTransformationSpec()
                TransformingLazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    items(count = 5) {
                        Button(
                            onClick = {},
                            modifier =
                                Modifier.fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .animateItem(),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text("Item $it")
                        }
                    }
                }
            }
        }
    }
}
