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

package noria.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalMap
import noria.NoriaContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize

private object NoHeightKey
object ConstantHeightKey

class MeasureCache<Item>(
  val heightKey: (Item) -> Any = { item -> item as Any },
  val itemBuilder: @Composable NoriaContext.(Item) -> Unit,
  val constraints: Constraints,
  val compositionLocalMap: CompositionLocalMap,
) {

  private val measureCache = mutableMapOf<Any, IntSize>()

  fun measureItem(item: Item): IntSize {
    return IntSize(0, 0) // TODO
  }
}
