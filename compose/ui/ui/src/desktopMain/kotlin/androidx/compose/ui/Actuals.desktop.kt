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

import androidx.compose.ui.desktop.KdtMainDispatcher
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * Override for [ComposeUIDispatcher]. When non-null it takes precedence over the KDT main
 * dispatcher below. Set by [androidx.compose.ui.desktop.headless.HeadlessApplication] while it is
 * active (its event-loop thread becomes the Compose UI thread) and cleared on
 * resetForReuse/stopAndJoin; by the Swing/AWT `awaitApplication` entry point and by
 * `ComposeContainer` (reference-counted) while an AWT `ComposeWindow`/`ComposePanel` is attached,
 * both pointing it at the AWT event dispatch thread; and by test fixtures. `Dispatchers.Main`
 * follows automatically: the kdt-dispatcher module's MainDispatcherFactory resolves through the
 * getter on every dispatch.
 */
@Volatile internal var ComposeUIDispatcherOverride: MainCoroutineDispatcher? = null

/**
 * The single Compose desktop dispatcher. It is both the UI thread and the thread Compose's internal
 * scheduling posts to — the global snapshot manager's apply-notification coalescing and
 * RectManager's relayout debounce (via [PostDelayedDispatcher]). These are the same thread by
 * construction, the one that mutates `LayoutNode` state, so there is one seam, not two: backends do
 * not wire scheduling separately.
 *
 * Resolution: the [ComposeUIDispatcherOverride] when set (headless / AWT / tests), otherwise the KDT
 * main dispatcher, which resolves the active [androidx.compose.ui.desktop.Application] and delegates
 * to its UI-thread primitives — so no per-platform switch is needed here. On the KDT path the
 * override stays null. Both Swing/AWT paths set it to the event dispatch thread: `awaitApplication`
 * for the application entry point, and `ComposeContainer` (reference-counted over attached
 * containers) for the bare `ComposeWindow`/`ComposePanel` embedding that never goes through
 * `awaitApplication`.
 */
actual val ComposeUIDispatcher: MainCoroutineDispatcher
    get() = ComposeUIDispatcherOverride ?: KdtMainDispatcher.INSTANCE

internal actual val PostDelayedDispatcher: CoroutineContext
    get() = ComposeUIDispatcher

/**
 * Views [this] plain dispatcher as a [MainCoroutineDispatcher] so it can back
 * [ComposeUIDispatcherOverride] — which must be main-typed, since [ComposeUIDispatcher] exposes
 * `.immediate` and backs `Dispatchers.Main`. Needed for the AWT event dispatch thread (skiko's
 * `MainUIDispatcher` is a plain `CoroutineDispatcher`) and for test dispatchers. A dispatcher that
 * already is a [MainCoroutineDispatcher] is returned unchanged. A delegate that provides [Delay]
 * (e.g. a `StandardTestDispatcher`) keeps it, so virtual-time control of RectManager's `delay`-based
 * debounce survives the wrapping.
 */
@OptIn(InternalCoroutinesApi::class)
internal fun CoroutineDispatcher.asComposeUiMainDispatcher(): MainCoroutineDispatcher =
    when {
        this is MainCoroutineDispatcher -> this
        this is Delay -> DelegatedMainDispatcherWithDelay(this, this)
        else -> DelegatedMainDispatcher(this)
    }

private open class DelegatedMainDispatcher(
    protected val delegate: CoroutineDispatcher,
) : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        delegate.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) =
        delegate.dispatch(context, block)

    override fun toString(): String = "ComposeUIDispatcher($delegate)"
}

@OptIn(InternalCoroutinesApi::class)
private class DelegatedMainDispatcherWithDelay(
    delegate: CoroutineDispatcher,
    delay: Delay,
) : DelegatedMainDispatcher(delegate), Delay by delay
