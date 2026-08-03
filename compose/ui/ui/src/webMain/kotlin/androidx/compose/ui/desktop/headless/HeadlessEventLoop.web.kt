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

package androidx.compose.ui.desktop.headless

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

actual fun createHeadlessEventLoop(): HeadlessEventLoop = WebHeadlessEventLoop()

private class WebHeadlessEventLoop : HeadlessEventLoop {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var closed = false
    private var pendingTasks = 0

    override fun dispatch(block: () -> Unit) {
        if (closed) return
        pendingTasks += 1
        scope.launch {
            if (pendingTasks > 0) {
                pendingTasks -= 1
            }
            if (!closed) {
                block()
            }
        }
    }

    override val pendingTasksCount: Int
        get() = pendingTasks

    // The browser is single-threaded; every caller is on the event-loop thread.
    override fun isCurrentThread(): Boolean = true

    override fun close(dropPendingTasks: Boolean) {
        closed = true
        if (dropPendingTasks) {
            pendingTasks = 0
            scope.cancel()
        }
    }
}
