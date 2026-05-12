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

package androidx.compose.ui

import androidx.compose.ui.desktop.LinuxWindowSystem
import androidx.compose.ui.desktop.currentLinuxWindowSystem
import androidx.compose.ui.desktop.gtk.GtkKdtMainDispatcher
import androidx.compose.ui.desktop.linux.LinuxKdtMainDispatcher
import androidx.compose.ui.desktop.macos.MacOsKdtMainDispatcher
import androidx.compose.ui.platform.DesktopPlatform
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher

actual val ComposeUIDispatcher: MainCoroutineDispatcher
    get() = when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsKdtMainDispatcher.INSTANCE
        DesktopPlatform.Linux -> when (currentLinuxWindowSystem()) {
            LinuxWindowSystem.Wayland -> LinuxKdtMainDispatcher.INSTANCE
            LinuxWindowSystem.Gtk -> GtkKdtMainDispatcher.INSTANCE
        }
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }

internal actual val PostDelayedDispatcher: CoroutineContext
    get() = ComposeUIDispatcher
