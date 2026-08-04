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

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * The single KDT UI-thread dispatcher, shared by every backend. It resolves the active
 * [Application] via [currentApplication] and delegates to its `invokeOnUiThread`/`isUiThread`
 * primitives, so there is no per-platform (or Wayland-vs-GTK) branching here — the active
 * application already IS the right backend. Replaces the former per-backend
 * `Mac/Linux/Gtk/WindowsKdtMainDispatcher` classes.
 */
internal sealed class KdtMainDispatcherBase : MainCoroutineDispatcher() {
    override val immediate: ImmediateKdtMainDispatcher by lazy { ImmediateKdtMainDispatcher() }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        currentApplication().invokeOnUiThread {
            block.run()
        }
    }
}

internal class ImmediateKdtMainDispatcher : KdtMainDispatcherBase() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !currentApplication().isUiThread()
    }

    override fun toString(): String {
        return "Dispatchers.MainKDT.immediate"
    }
}

internal class KdtMainDispatcher : KdtMainDispatcherBase() {
    override fun toString(): String {
        return "Dispatchers.MainKDT"
    }

    companion object {
        /** Process-wide instance so call sites can avoid constructing one each time. */
        val INSTANCE: KdtMainDispatcher = KdtMainDispatcher()
    }
}
