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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.node.OutOfFrameExecutor
import androidx.compose.ui.util.trace

/**
 * Platform-specific scheduler for work that should be deferred out of the current
 * composition/layout/rendering stack.
 *
 * @see OutOfFrameExecutor
 */
@InternalComposeUiApi
interface PlatformOutOfFrameExecutor {
    /**
     * `true` when there is scheduled work that has not been executed yet.
     */
    val hasWorkScheduled: Boolean
        get() = false

    /**
     * Schedules [block] to run out of the current call stack.
     */
    fun schedule(block: () -> Unit)

    /**
     * Runs pending work scheduled by [schedule] immediately for tests.
     */
    fun drainScheduledWorkForTest()
}

/**
 * A generic implementation of [PlatformOutOfFrameExecutor] that uses the platform's
 * "schedule-on-EDT" method to schedule the work.
 *
 * The platform must call [GenericPlatformOutOfFrameExecutor.onBeforeFrame] before executing each
 * frame (recomposition etc.)
 */
internal class GenericPlatformOutOfFrameExecutor(
    /** Schedules a task on the EDT. */
    private val scheduleTask: (block: () -> Unit) -> Unit,
    /** Returns whether the current thread is the EDT. */
    private val isExecutingOnEdtThread: () -> Boolean
) : PlatformOutOfFrameExecutor {

    /**
     * The queue of scheduled tasks.
     */
    private val queue = ArrayDeque<() -> Unit>()

    /**
     * Whether this executor has been disposed.
     */
    private var isDisposed = false

    private val drainLambda = ::drain

    override val hasWorkScheduled: Boolean
        get() = queue.isNotEmpty()

    override fun schedule(block: () -> Unit) {
        requireEdt()

        if (isDisposed) return

        val shouldSchedule = queue.isEmpty()
        queue.addLast(block)

        if (shouldSchedule) {
            scheduleTask(drainLambda)
        }
    }

    /**
     * Runs all queued tasks.
     */
    private fun drain() {
        trace("GenericPlatformOutOfFrameExecutor:outOfFrameExecutor") {
            while (queue.isNotEmpty()) {
                queue.removeLast().invoke()
            }
        }
    }

    override fun drainScheduledWorkForTest() = drain()

    /**
     * This must be called before a frame is executed.
     */
    fun onBeforeFrame() {
        requireEdt()
        if (isDisposed) return

        drain()
    }

    /**
     * Disposes of this executor.
     *
     * The queue is cleared and scheduled work is cancelled.
     */
    fun dispose() {
        requireEdt()

        isDisposed = true
        queue.clear()
    }

    private fun requireEdt() {
        require(isExecutingOnEdtThread()) { "Must be called on the event dispatching thread" }
    }
}