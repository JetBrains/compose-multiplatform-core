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

package androidx.compose.ui.desktop

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * AIR-5601: the previous heuristic could select [LinuxWindowSystem.Wayland] purely from
 * `XDG_SESSION_TYPE` or `GDK_BACKEND`, without a usable Wayland socket, which crashed on hosts
 * that advertise a Wayland session type but have no compositor socket. Noria's rule is the
 * fix: Wayland iff `WAYLAND_DISPLAY` or `WAYLAND_SOCKET` is set and non-blank; otherwise Gtk
 * (which also serves X11).
 */
@Category(HeadlessTest::class)
class LinuxPlatformDetectionTest {

    private fun detect(env: Map<String, String>): LinuxWindowSystem =
        currentLinuxWindowSystem(getenv = { env[it] })

    @Test
    fun waylandDisplaySelectsWayland() {
        assertEquals(
            LinuxWindowSystem.Wayland,
            detect(mapOf("WAYLAND_DISPLAY" to "wayland-0")),
        )
    }

    @Test
    fun waylandSocketSelectsWayland() {
        assertEquals(
            LinuxWindowSystem.Wayland,
            detect(mapOf("WAYLAND_SOCKET" to "4")),
        )
    }

    @Test
    fun xdgSessionTypeWaylandAloneSelectsGtk() {
        // AIR-5601 fix: no socket, no Wayland, even though the session type says "wayland".
        assertEquals(
            LinuxWindowSystem.Gtk,
            detect(mapOf("XDG_SESSION_TYPE" to "wayland")),
        )
    }

    @Test
    fun gdkBackendWaylandAloneSelectsGtk() {
        // AIR-5601 fix: GDK_BACKEND is a client-side hint, not proof of a usable socket.
        assertEquals(
            LinuxWindowSystem.Gtk,
            detect(mapOf("GDK_BACKEND" to "wayland")),
        )
    }

    @Test
    fun noEnvironmentSelectsGtk() {
        assertEquals(LinuxWindowSystem.Gtk, detect(emptyMap()))
    }

    @Test
    fun blankWaylandDisplaySelectsGtk() {
        assertEquals(
            LinuxWindowSystem.Gtk,
            detect(mapOf("WAYLAND_DISPLAY" to "")),
        )
    }
}
