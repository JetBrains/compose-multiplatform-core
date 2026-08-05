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

package androidx.compose.ui.platform

import androidx.compose.ui.ComposeUIDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * The dispatcher the desktop backends register with [GlobalSnapshotManager] for their
 * apply-notification coalescing.
 *
 * Passing [ComposeUIDispatcher] itself would pin whatever it resolved to at registration time:
 * fine when that is the KDT main dispatcher (a stable object that re-resolves the active
 * application per dispatch), but stale when it is a concrete override — a headless event loop or
 * the AWT EDT. A JVM that switches active application (a test runner alternating headless and
 * real-UI apps) would then keep posting apply notifications to the application that registered
 * first, dead or not. [GlobalSnapshotManager] also keys its shared, reference-counted registrations
 * by dispatcher identity, so a value that changes identity would fragment them.
 *
 * This is therefore a stable indirection that re-resolves [ComposeUIDispatcher] on every dispatch:
 * one registration whose pump always targets the currently active application's UI thread.
 */
internal val GlobalSnapshotManagerDispatcher: CoroutineDispatcher
    get() = CurrentComposeUiDispatcher

private object CurrentComposeUiDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        ComposeUIDispatcher.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) =
        ComposeUIDispatcher.dispatch(context, block)

    override fun toString(): String = "GlobalSnapshotManagerDispatcher(-> ComposeUIDispatcher)"
}
