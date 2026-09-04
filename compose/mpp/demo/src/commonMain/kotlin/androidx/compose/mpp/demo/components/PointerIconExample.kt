/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * A cross-platform demo showcasing [PointerIcon] capabilities. Each box changes the cursor
 * appearance when hovered.
 *
 * Built-in [PointerIcon] aliases (Default, Crosshair, Text, Hand) are available on every target.
 * Additional platform-specific icons are contributed via [platformPointerIcons]:
 *   - Desktop: cursors built with `PointerIcon(java.awt.Cursor(...))`.
 *   - Web: cursors built with `PointerIcon(...)` (CSS cursor keywords).
 */
@Composable
fun PointerIconExample() {
    val allIcons = builtInPointerIcons + platformPointerIcons
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(allIcons) { (label, icon) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(120.dp)
                        .background(Color(0xFFEEEEEE))
                        .pointerHoverIcon(icon),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label)
                }
            }
        }
    }
}

private val builtInPointerIcons: List<Pair<String, PointerIcon>> = listOf(
    "Default" to PointerIcon.Default,
    "Crosshair" to PointerIcon.Crosshair,
    "Text" to PointerIcon.Text,
    "Hand" to PointerIcon.Hand,
)

/**
 * Platform-specific pointer icons contributed by each target's `actual`. Each entry is a
 * label-to-[PointerIcon] pair rendered as an additional hoverable box in [PointerIconExample].
 */
internal expect val platformPointerIcons: List<Pair<String, PointerIcon>>
