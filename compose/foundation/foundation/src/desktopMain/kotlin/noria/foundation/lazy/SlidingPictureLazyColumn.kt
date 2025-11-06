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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import noria.Cell

data class SlideAnimation(val toggledIndices: IntRange, val slideProgress: State<Float>, val isExpansion: Boolean)

fun slidingPictureLazyColumn(slideAnimationCell: Cell<SlideAnimation?>, state: LazyListState, contentPadding: PaddingValues, spacing: Int): @Composable (size: Int, nth: (Int) -> Row) -> Unit = { size, nth ->
  // TODO
  approximatingLazyColumn(size, state, contentPadding, spacing = spacing, nth = nth)
}
