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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Dispatches [onFrame] after a call of [scheduleFrame]: the coalescing frame pump of the windowed
 * frame drivers. Mirrors Skiko's `org.jetbrains.skiko.FrameDispatcher` — the primitive behind
 * upstream Compose's desktop render loops — so the drivers keep upstream's scheduling semantics.
 *
 * Delivery rides a conflated channel into a coroutine of [scope], never an OS paint message, so a
 * request cannot be lost to window state (hidden, minimized, mid-creation) or to re-entrant
 * native dispatch. Multiple [scheduleFrame] calls before a frame begins coalesce into one
 * [onFrame]; calls between a frame's begin and end schedule exactly one follow-up frame, because
 * the scheduled flag is consumed before [onFrame] runs.
 */
internal class FrameDispatcher(
    scope: CoroutineScope,
    private val onFrame: suspend CoroutineScope.() -> Unit,
) {
    private val frameChannel = Channel<Unit>(Channel.CONFLATED)
    private var frameScheduled = false

    private val job = scope.launch {
        while (true) {
            frameChannel.receive()
            frameScheduled = false
            onFrame()
            // Return to the dispatcher between frames so a saturated frame loop can never starve
            // input events or other queued work.
            yield()
        }
    }

    fun cancel() {
        job.cancel()
    }

    /**
     * Schedule the next [onFrame]. Safe to call from any thread: a stale read of the dedup flag at
     * most costs one extra send into the conflated channel, never a lost frame.
     */
    fun scheduleFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            frameChannel.trySend(Unit)
        }
    }
}
