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

package noria.foundation.layout

import androidx.compose.runtime.Composable
import noria.ClosureContext
import noria.NoriaContext

data class Row(
    val key: Any,
    val heightKey: Any = Unit,
    val render: @Composable NoriaContext.() -> Unit,
)

data class ItemVerticalPosition(
    val y: Int,
    val height: Int,
)

val EMPTY_VERTICAL_POSITION = ItemVerticalPosition(0, 0)

data class TrackedItemWidth(val key: Any, val appliedMinWidth: Int, val width: Int)

typealias LazyColumnOverscrollPolicy = (lastItemHeight: Int) -> Int

@Composable
fun NoriaContext.heightKeyBasedLazyColumn(
    size: Int,
    overscrollPolicy: LazyColumnOverscrollPolicy = { 0 },
    measureItemsWithWidthConstraints: Boolean = false,
    spacing: Int = 0,
    nth: ClosureContext.(Int) -> Row,
): (Int) -> ItemVerticalPosition {
    // TODO
    return approximatingLazyColumn(size, overscrollPolicy, spacing, nth = nth)
}
