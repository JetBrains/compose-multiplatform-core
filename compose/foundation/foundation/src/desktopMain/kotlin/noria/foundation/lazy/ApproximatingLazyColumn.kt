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

package noria.foundation.lazy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun approximatingLazyColumn(
    count: Int,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    overscrollPolicy: LazyColumnOverscrollPolicy = { 0 },
    spacing: Int = 0,
    maxViewportHeight: Int = 0,
    startLayoutFromBottom: Boolean = false,
    nth: (Int) -> Row
) {
    var maxSeenWidth by remember(state) { mutableStateOf(0.dp) }
    // TODO custom VerticalArrangement to respect overscrollPolicy or translate it into an OverscrollEffect
    val verticalArrangement = if (spacing != 0) {
        Arrangement.spacedBy(LocalDensity.current.run { spacing.toDp() })
    } else {
        Arrangement.Top
    }
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        reverseLayout = startLayoutFromBottom,
        verticalArrangement = verticalArrangement,
    ) {
        items(
            count,
            key = { index -> nth(index).key },
            contentType = { index ->
                nth(index).heightKey.takeIf { it != Unit }
            }
        ) { index ->
            val density = LocalDensity.current
            Box(
                Modifier
                    .onSizeChanged {
                        val widthInDp = density.run { it.width.toDp() }
                        if (widthInDp > maxSeenWidth) {
                            maxSeenWidth = widthInDp
                        }
                    }
                    .widthIn(min = maxSeenWidth),
                propagateMinConstraints = true
            ) {
                nth(index).render()
            }
        }
    }
}
