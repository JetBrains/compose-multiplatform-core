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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import noria.ClosureContext
import noria.NoriaContext
import noria.foundation.ScrollTarget

@Composable
fun NoriaContext.approximatingLazyColumn(
    count: Int,
    overscrollPolicy: LazyColumnOverscrollPolicy = { 0 },
    spacing: Int = 0,
    scrollTarget: ScrollTarget? = null,
    maxViewportHeight: Int = 0,
    startLayoutFromBottom: Boolean = false,
    nth: ClosureContext.(Int) -> Row
): (Int) -> ItemVerticalPosition {
    val state = rememberLazyListState()
    // TODO custom VerticalArrangement to respect overscrollPolicy
    val verticalArrangement = if (spacing != 0) {
        Arrangement.spacedBy(LocalDensity.current.run { spacing.toDp() })
    } else {
        Arrangement.Top
    }
    LazyColumn(verticalArrangement = verticalArrangement) {
        items(
            count,
            key = { index -> nth(index).key },
            contentType = { index ->
                nth(index).heightKey.takeIf { it != Unit }
            }
        ) { index ->
            nth(index).render(this@approximatingLazyColumn)
        }
    }

    return { index ->
        state.layoutInfo.run {
            visibleItemsInfo.find { it.index == index }?.let {
                ItemVerticalPosition(it.offset, it.size)
            } ?: ItemVerticalPosition(0, 0) // TODO
        }
    }
}
