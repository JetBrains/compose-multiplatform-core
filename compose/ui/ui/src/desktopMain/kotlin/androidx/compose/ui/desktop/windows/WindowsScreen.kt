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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.desktop.GloballyPositionedScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize

data class WindowsScreen(
    override val nativeScreen: org.jetbrains.desktop.win32.Screen,
    override val name: String = nativeScreen.name ?: "",
    override val size: DpSize = nativeScreen.size.toDpSize(),
    override val density: Density = Density(nativeScreen.scale),
    override val refreshRate: Int = nativeScreen.maximumFramesPerSecond,
    override val position: DpOffset = nativeScreen.origin.toLogical(nativeScreen.scale).toDpOffset(),
    override val bounds: DpRect = DpRect(position, size),
) : GloballyPositionedScreen
