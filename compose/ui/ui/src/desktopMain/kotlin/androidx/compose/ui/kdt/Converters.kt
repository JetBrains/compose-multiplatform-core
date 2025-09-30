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

package androidx.compose.ui.kdt

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import org.jetbrains.desktop.macos.Application
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.desktop.macos.Screen
import org.jetbrains.desktop.macos.TextDirection

fun LogicalSize.toIntSize(density: Density = Density(2f)): IntSize {
    return with(density) {
        DpSize(width.dp, height.dp).toSize().toIntSize()
    }
}

fun LogicalSize.toDpSize(): DpSize {
    return DpSize(width.dp, height.dp)
}