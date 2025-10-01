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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.awaitCancellation

interface ComposeWindowScope {
    val window: ComposeWindow
}

interface ComposeWindow : AutoCloseable {
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
fun Window(
    onCloseRequested: () -> Unit,
    content: @Composable ComposeWindowScope.() -> Unit
) {
    val application = LocalComposeApplication.current
    // todo[ps] update the callback
    val composeWindow = remember { application.createWindow(onCloseRequested) }
    DisposableEffect(Unit) {
        onDispose {
            println("close was called on window")
            composeWindow.close()
        }
    }
    val windowScope = object : ComposeWindowScope {
        override val window: ComposeWindow = composeWindow
    }
    // We need this launch effect here to prevent Recomposer form joining
    LaunchedEffect(Unit) {
        awaitCancellation()
    }
    composeWindow.setContent {
        windowScope.content()
    }
}