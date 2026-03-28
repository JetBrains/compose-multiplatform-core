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
import kotlinx.browser.window
import org.w3c.dom.MessageChannel
import org.w3c.dom.MessageEvent

internal object WebOutOfFrameExecutor : OutOfFrameExecutor {
    private val queue = ArrayDeque<() -> Unit>()
    private val outOfFrameCallback = { message: MessageEvent ->
            while (queue.isNotEmpty()) {
                queue.removeFirst().invoke()
            }
    }
    @OptIn(ExperimentalWasmJsInterop::class)
    override fun schedule(block: () -> Unit) {
        val shouldSchedule = queue.isEmpty()
        queue.addLast(block)

        if (shouldSchedule) {
            //Runs tasks after the current frame is rendered. Logic extracted from -> https://webperf.tips/tip/measuring-paint-time/#detecting-when-paint-occurs
            window.requestAnimationFrame {
                val channel = MessageChannel()
                channel.port1.onmessage = outOfFrameCallback
                channel.port2.postMessage(null)
            }

        }
    }
}