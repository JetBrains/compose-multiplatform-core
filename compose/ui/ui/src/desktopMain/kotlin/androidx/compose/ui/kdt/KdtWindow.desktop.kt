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

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement

@Composable
fun KdtWindow(content: @Composable KdtWindowScope.() -> Unit) {
}

interface KdtWindowScope {
    val window: KdtWindow
}

interface KdtWindow {
    val size: DpSize
    var requestedConstraints: Constraints
    val decoration: WindowDecoration
    var preferredDecoration: WindowDecoration
    suspend fun requestPlacement(placement: WindowPlacement): Boolean
    fun showWindowMenu(position: DpOffset)
    val isActive: Boolean
    val isKey: Boolean
    val hasActiveAppearance: Boolean
}