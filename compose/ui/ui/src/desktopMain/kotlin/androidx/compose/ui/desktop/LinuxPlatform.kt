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

internal fun currentLinuxWindowSystem(): LinuxWindowSystem {
    val gdkBackend = System.getenv("GDK_BACKEND")?.lowercase()
    val xdgSessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase()
    val waylandDisplay = System.getenv("WAYLAND_DISPLAY")

    return when {
        !waylandDisplay.isNullOrBlank() -> LinuxWindowSystem.Wayland
        xdgSessionType == "wayland" -> LinuxWindowSystem.Wayland
        gdkBackend
            ?.split(',')
            ?.map(String::trim)
            ?.any { it == "wayland" } == true -> LinuxWindowSystem.Wayland
        else -> LinuxWindowSystem.Gtk
    }
}
