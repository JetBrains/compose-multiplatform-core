/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Sampled
@Preview
@Composable
fun LazyLayoutPrefetchStateSample() {
    val items = remember { (0..100).toList().map { it.toString() } }
    var currentHandle = remember<LazyLayoutPrefetchState.PrefetchHandle?> { null }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    // Create an item provider
    val itemProvider = remember {
        {
            object : LazyLayoutItemProvider {
                override val itemCount: Int
                    get() = 100

                @Composable
                override fun Item(index: Int, key: Any) {
                    Box(
                        modifier =
                            Modifier.width(100.dp)
                                .height(100.dp)
                                .background(color = if (index % 2 == 0) Color.Red else Color.Green)
                    ) {
                        Text(text = items[index])
                    }
                }
            }
        }
    }

    Column {
        Button(onClick = { currentHandle = prefetchState.schedulePrecomposition(10) }) {
            Text(text = "Prefetch Item 10")
        }
        Button(onClick = { currentHandle?.cancel() }) { Text(text = "Cancel Prefetch") }
        LazyLayout(modifier = Modifier.size(500.dp), itemProvider = itemProvider) { constraints ->
            // plug the measure policy, this is how we create and layout items.
            val placeablesCache = mutableListOf<Pair<Placeable, Int>>()
            fun Placeable.mainAxisSize() = width
            fun Placeable.crossAxisSize() = height

            val childConstraints =
                Constraints(maxWidth = Constraints.Infinity, maxHeight = constraints.maxHeight)

            var currentItemIndex = 0
            var crossAxisSize = 0
            var mainAxisSize = 0

            // measure items until we either fill in the space or run out of items.
            while (mainAxisSize < constraints.maxWidth && currentItemIndex < items.size) {
                val itemPlaceables = compose(currentItemIndex).map { it.measure(childConstraints) }
                for (item in itemPlaceables) {
                    // save placeable to be placed later.
                    placeablesCache.add(item to mainAxisSize)

                    mainAxisSize += item.mainAxisSize() // item size contributes to main axis size
                    // cross axis size will the size of tallest/widest item
                    crossAxisSize = maxOf(crossAxisSize, item.crossAxisSize())
                }
                currentItemIndex++
            }

            val layoutWidth = minOf(mainAxisSize, constraints.maxHeight)
            val layoutHeight = crossAxisSize

            layout(layoutWidth, layoutHeight) {
                // since this is a linear list all items are placed on the same cross-axis position
                for ((placeable, position) in placeablesCache) {
                    placeable.place(position, 0)
                }
            }
        }
    }
}

/** A simple Layout that will place items right to left with scrolling support. */
@Sampled
@Preview
@Composable
fun LazyLayoutSample() {
    val items = remember { (0..100).toList().map { it.toString() } }

    // Create an item provider
    val itemProvider = remember {
        {
            object : LazyLayoutItemProvider {
                override val itemCount: Int
                    get() = 100

                @Composable
                override fun Item(index: Int, key: Any) {
                    Box(
                        modifier =
                            Modifier.width(100.dp)
                                .height(100.dp)
                                .background(color = if (index % 2 == 0) Color.Red else Color.Green)
                    ) {
                        Text(text = items[index])
                    }
                }
            }
        }
    }

    LazyLayout(modifier = Modifier.size(500.dp), itemProvider = itemProvider) { constraints ->
        // plug the measure policy, this is how we create and layout items.
        val placeablesCache = mutableListOf<Pair<Placeable, Int>>()
        fun Placeable.mainAxisSize() = width
        fun Placeable.crossAxisSize() = height

        val childConstraints =
            Constraints(maxWidth = Constraints.Infinity, maxHeight = constraints.maxHeight)

        var currentItemIndex = 0
        var crossAxisSize = 0
        var mainAxisSize = 0

        // measure items until we either fill in the space or run out of items.
        while (mainAxisSize < constraints.maxWidth && currentItemIndex < items.size) {
            val itemPlaceables = compose(currentItemIndex).map { it.measure(childConstraints) }
            for (item in itemPlaceables) {
                // save placeable to be placed later.
                placeablesCache.add(item to mainAxisSize)

                mainAxisSize += item.mainAxisSize() // item size contributes to main axis size
                // cross axis size will the size of tallest/widest item
                crossAxisSize = maxOf(crossAxisSize, item.crossAxisSize())
            }
            currentItemIndex++
        }

        val layoutWidth = minOf(mainAxisSize, constraints.maxHeight)
        val layoutHeight = crossAxisSize

        layout(layoutWidth, layoutHeight) {
            // since this is a linear list all items are placed on the same cross-axis position
            for ((placeable, position) in placeablesCache) {
                placeable.place(position, 0)
            }
        }
    }
}
