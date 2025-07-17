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

package androidx.compose.integration.macrobenchmark.target.complexdifferenttypeslist.common

import androidx.compose.integration.macrobenchmark.target.complexdifferenttypeslist.model.ui.TableRowColorUiModel
import androidx.compose.integration.macrobenchmark.target.complexdifferenttypeslist.theme.SquadTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun resolveTableRowColor(uiModel: TableRowColorUiModel): Color =
    when {
        uiModel.isHighlighted -> SquadTheme.colors.highlightedTableRow
        uiModel.hasDarkerBackground -> SquadTheme.colors.darkerTableRow
        else -> MaterialTheme.colors.surface
    }
