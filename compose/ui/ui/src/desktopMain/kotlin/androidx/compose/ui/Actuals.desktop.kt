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
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher

actual val ComposeUIDispatcher: MainCoroutineDispatcher
    get() =
        when (DesktopPlatform.Current) {
            DesktopPlatform.MacOS -> MacOsKdtMainDispatcher.INSTANCE
            DesktopPlatform.Linux ->
                when (currentLinuxWindowSystem()) {
                    LinuxWindowSystem.Wayland -> LinuxKdtMainDispatcher.INSTANCE
                    LinuxWindowSystem.Gtk -> GtkKdtMainDispatcher.INSTANCE
                }
            DesktopPlatform.Windows -> TODO()
            DesktopPlatform.Unknown ->
                error("Unsupported desktop platform: ${DesktopPlatform.Current}")
        }

/**
 * The dispatcher Compose's internal scheduling posts to: the global snapshot manager's
 * apply-notification coalescing and RectManager's relayout debounce.
 *
 * Set once by whichever windowing backend owns the process — each platform's
 * `Application.initialize()` — or by a test fixture. Deliberately NOT defaulted: this work must run
 * on the same thread that mutates `LayoutNode` state, and the AWT EDT and the KDT main thread are
 * different threads, so guessing a default is a silent correctness bug. An unconfigured seam fails
 * loudly instead.
 *
 * Known gap: the KDT `Application` path (macOS/Linux/GTK) sets this from its `initialize()`. The
 * Swing/AWT embedding path in `ComposeContainer.desktop.kt` — reached via the public
 * `CanvasLayersComposeScene`/`PlatformLayersComposeScene` factories that back
 * `androidx.compose.ui.awt`'s `ComposeWindow`/`ComposePanel` — never calls any platform's
 * `initialize()`, so a scene built that way hits the `error` below as soon as it is constructed or
 * attaches its first node. This is pre-existing, not introduced by this seam: that embedding path
 * already unconditionally depended on the KDT dispatcher before this seam named it, and would have
 * failed the same way at the same point — a native GCD linkage failure on macOS, or an
 * `IllegalStateException` from the not-yet-initialized KDT application on Linux/GTK — the first
 * time scheduling actually dispatched. The fix, when someone gets to it, is for that embedding path
 * to set this seam to its own UI-thread dispatcher instead of leaving it unset.
 */
@Volatile internal var ComposeSchedulingDispatcher: CoroutineDispatcher? = null

internal val requiredSchedulingDispatcher: CoroutineDispatcher
    get() =
        ComposeSchedulingDispatcher
            ?: error(
                "No Compose scheduling dispatcher configured. A windowing backend sets this from " +
                    "its Application.initialize(); tests install one via SchedulingDispatcherFixture."
            )

internal actual val PostDelayedDispatcher: CoroutineContext
    get() = requiredSchedulingDispatcher
