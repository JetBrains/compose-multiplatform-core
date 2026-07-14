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

import androidx.compose.ui.node.OutOfFrameExecutor
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

internal class WebOutOfFrameExecutor : PlatformOutOfFrameExecutor  {
    private val queue = ArrayDeque<() -> Unit>()
    private var isDisposed = false
    private val drainCallback = {
        if (!isDisposed) {
            while (queue.isNotEmpty()) {
                queue.removeLast().invoke()
            }
        }
    }

    override fun schedule(block: () -> Unit) {
        if (isDisposed) {
            return
        }
        val shouldSchedule = queue.isEmpty()
        queue.addLast(block)

        if (shouldSchedule) {
            schedulerPostTask(drainCallback)
        }
    }

    override fun drainScheduledWorkForTest() {
        drainCallback()
    }

    override val hasWorkScheduled: Boolean
        get() = queue.isNotEmpty()

    fun dispose() {
        isDisposed = true
        queue.clear()
    }
}

internal val isPostingTasksSupported: Boolean by lazy {
    isSchedulerApiSupported()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun isSchedulerApiSupported(): Boolean = js("Boolean('scheduler' in window)")


/**
 * Better reflects [OutOfFrameExecutor] contract
 */
@OptIn(ExperimentalWasmJsInterop::class)
//language=javascript
private fun schedulerPostTask(block: () -> Unit): Unit =
    js("scheduler.postTask(block, { priority: 'user-blocking',})")