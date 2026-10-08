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

import java.awt.EventQueue

/**
 * An implementation of [PlatformOutOfFrameExecutor] for the desktop.
 */
internal class DesktopPlatformOutOfFrameExecutor :
    AbstractPlatformOutOfFrameExecutor(
        tracePrefix = "DesktopOutOfFrameExecutor",
    ) {

    override fun addToQueueAndSchedule(
        queue: ArrayDeque<() -> Unit>,
        block: () -> Unit,
        drainLambda: () -> Unit
    ) {
        val shouldSchedule = queue.isEmpty()
        queue.addLast(block)

        if (shouldSchedule) {
            EventQueue.invokeLater(drainLambda)
        }
    }

    override fun isExecutingOnUiThread() = EventQueue.isDispatchThread()

    /**
     * This must be called before a frame is executed.
     */
    fun onBeforeFrame() {
        requireUiThread()
        if (isDisposed) return

        drain()
    }
}