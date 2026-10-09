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

package androidx.compose.ui.window

import androidx.compose.ui.platform.AbstractPlatformOutOfFrameExecutor
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Drains out-of-frame work around Metal frame production.
 *
 * Work scheduled during a frame is drained in [onFrameEnd], after that frame has been recorded and
 * before the next frame starts. Work scheduled between frames is already out of the current frame,
 * so it should run before the next frame starts instead of waiting for that next frame to finish.
 * The async main-queue drain is a best-effort way to run such work early, while [onFrameStart]
 * provides the ordering guarantee if the display-link callback wins the race.
 */
internal class IosOutOfFrameExecutor :
    AbstractPlatformOutOfFrameExecutor(
        tracePrefix = "IosOutOfFrameExecutor",
    ) {
    private var isFrameInProgress = false
    private var isDrainScheduled = false
    private var isDraining = false

    override fun addToQueueAndSchedule(
        queue: ArrayDeque<() -> Unit>,
        block: () -> Unit,
        drainLambda: () -> Unit
    ) {
        queue.addLast(block)

        if (!isFrameInProgress && !isDraining && !isDrainScheduled) {
            // When work is scheduled during frame recording, onFrameEnd() drains it before the next
            // frame starts. Outside a frame there is no such drain point, but running the block
            // inline would make scheduling synchronous and could mutate composition state from the
            // current rendering/layout stack. Post one main-queue drain to defer the work while
            // keeping it on the main thread.
            isDrainScheduled = true
            dispatch_async(dispatch_get_main_queue(), drainLambda)
        }
    }

    override fun isExecutingOnUiThread() = NSThread.isMainThread

    fun onFrameStart() {
        requireUiThread()
        if (isDisposed) {
            return
        }

        // The async main-queue drain is best-effort and can lose the race to a display-link
        // callback. Drain pending work before starting frame production to preserve the contract
        // that out-of-frame work runs before the next frame starts.
        drain()

        isFrameInProgress = true
    }

    fun onFrameEnd() {
        requireUiThread()
        if (isDisposed) {
            return
        }

        isFrameInProgress = false
        drain()
    }

    override fun dispose() {
        super.dispose()

        isFrameInProgress = false
        isDrainScheduled = false
    }

    override fun drain() {
        requireUiThread()

        if (isDisposed || isDraining) {
            return
        }

        isDrainScheduled = false
        isDraining = true

        try {
            super.drain()
        } finally {
            isDraining = false
        }
    }
}
