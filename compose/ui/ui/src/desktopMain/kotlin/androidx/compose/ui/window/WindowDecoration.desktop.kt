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

package androidx.compose.ui.window

import androidx.compose.ui.platform.DesktopPlatform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual fun windowDecorationCustomTitleBarHeight(): Dp = when (DesktopPlatform.Current) {
    DesktopPlatform.MacOS -> 28.dp
    DesktopPlatform.Windows -> 32.dp
    else -> 24.dp
}

internal actual fun windowDecorationLeftTitleBarElements(): List<WindowDecoration.TitleBarElement> =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> listOf(
            WindowDecoration.TitleBarElement.CloseButton,
            WindowDecoration.TitleBarElement.MinimizeButton,
            WindowDecoration.TitleBarElement.FullscreenButton
        )

        DesktopPlatform.Windows -> listOf(WindowDecoration.TitleBarElement.AppMenu)
        DesktopPlatform.Linux -> emptyList()
        DesktopPlatform.Unknown -> emptyList()
    }

internal actual fun windowDecorationRightTitleBarElements(): List<WindowDecoration.TitleBarElement> =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> emptyList()
        DesktopPlatform.Windows -> listOf(
            WindowDecoration.TitleBarElement.MinimizeButton,
            WindowDecoration.TitleBarElement.MaximizeButton,
            WindowDecoration.TitleBarElement.CloseButton
        )

        DesktopPlatform.Linux -> listOf(
            WindowDecoration.TitleBarElement.MinimizeButton,
            WindowDecoration.TitleBarElement.MaximizeButton,
            WindowDecoration.TitleBarElement.CloseButton
        )

        DesktopPlatform.Unknown -> emptyList()
    }
