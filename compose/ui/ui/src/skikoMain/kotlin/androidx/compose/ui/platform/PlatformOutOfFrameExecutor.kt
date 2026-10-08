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
 * A base implementation of [PlatformOutOfFrameExecutor] that provides a common ground for the
 * platform-specific implementations.
 */
internal abstract class AbstractPlatformOutOfFrameExecutor(
    /** The prefix to use for tracing. */
    private val tracePrefix: String,

    /** Returns whether the current thread is the UI thread. */
    private val isExecutingOnUiThread: () -> Boolean
) : PlatformOutOfFrameExecutor {

    /**
     * The queue of scheduled tasks.
     */
    private val queue = ArrayDeque<() -> Unit>()

    /**
     * A lambda that calls [drain].
     */
    private val drainLambda = ::drain

    /**
     * Whether this executor has been disposed.
     */
    protected var isDisposed = false

    override val hasWorkScheduled: Boolean
        get() = queue.isNotEmpty()

    override fun schedule(block: () -> Unit) {
        requireUiThread()

        if (isDisposed) return

        addToQueueAndSchedule(queue, block, drainLambda)
    }

    /**
     * Adds the given block to the queue and schedules the task if necessary.
     */
    protected abstract fun addToQueueAndSchedule(
        /** The queue of scheduled tasks. */
        queue: ArrayDeque<() -> Unit>,
        /** The task to schedule. */
        block: () -> Unit,
        /** The lambda that drains the queue, running the tasks. */
        drainLambda: () -> Unit
    )

    /**
     * Runs all queued tasks.
     */
    protected open fun drain() {
        requireUiThread()
        trace("$tracePrefix:outOfFrameExecutor") {
            while (queue.isNotEmpty()) {
                queue.removeLast().invoke()
            }
        }
    }

    override fun drainScheduledWorkForTest() = drain()

    /**
     * Disposes of this executor.
     *
     * The queue is cleared and scheduled work is cancelled.
     */
    open fun dispose() {
        requireUiThread()

        isDisposed = true
        queue.clear()
    }

    protected fun requireUiThread() {
        require(isExecutingOnUiThread()) { "Must be called on the UI thread" }
    }
}
