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
import androidx.compose.runtime.remember
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.kdt.macos.PresentablePicture
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.atomicfu.atomic
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.Window
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

interface ComposeWindowScope {
    val window: ComposeWindow
}

interface ComposeWindow {
    val size: DpSize
    val contentSize: DpSize
    val isActive: Boolean
    val isKey: Boolean
//    var requestedConstraints: Constraints
//    val decoration: WindowDecoration
//    var preferredDecoration: WindowDecoration
//    suspend fun requestPlacement(placement: WindowPlacement): Boolean
//    fun showWindowMenu(position: DpOffset)
//    val hasActiveAppearance: Boolean

    // todo[ps] this functions actually shouldn't be a part of the api
    fun setContent(content: @Composable () -> Unit)
}

@Composable
fun Window(content: @Composable ComposeWindowScope.() -> Unit) {
    val application = LocalComposeApplication.current
    val composeWindow = remember { application.createWindow() }
    val windowScope = object: ComposeWindowScope {
        override val window: ComposeWindow = composeWindow
    }
    composeWindow.setContent {
        windowScope.content()
    }
}