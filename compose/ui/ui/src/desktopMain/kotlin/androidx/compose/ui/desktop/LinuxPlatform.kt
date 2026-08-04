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

package androidx.compose.ui.desktop

internal enum class LinuxWindowSystem {
    Wayland,
    Gtk,
}

internal fun currentLinuxWindowSystem(
    getenv: (String) -> String? = System::getenv,
): LinuxWindowSystem {
    val waylandDisplay: String? = getenv("WAYLAND_DISPLAY")
    val waylandSocket: String? = getenv("WAYLAND_SOCKET")
    return if (!waylandDisplay.isNullOrBlank() || !waylandSocket.isNullOrBlank()) {
        LinuxWindowSystem.Wayland
    } else {
        LinuxWindowSystem.Gtk
    }
}
