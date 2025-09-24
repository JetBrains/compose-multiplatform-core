/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.compose.desktop.examples.vsynctest

import androidx.compose.desktop.examples.vsync.WindowContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    AppWindow()
//    AppWindow()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApplicationScope.AppWindow() {
    Window(
        onCloseRequest = ::exitApplication,
        initialSize = DpSize(800.dp, 800.dp)
    ) {
        WindowContent(
            windowSize = DpSize(window.width.dp, window.height.dp),
            refreshRate = window.graphicsConfiguration.device.displayMode.refreshRate
        )
    }
}