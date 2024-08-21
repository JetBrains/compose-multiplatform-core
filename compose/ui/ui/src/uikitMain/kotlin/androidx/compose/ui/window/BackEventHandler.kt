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

package androidx.compose.ui.window

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

val LocalBackEventHandler = staticCompositionLocalOf<BackEventHandler> {
    error("BackEventHandler not provided")
}


class BackEventHandler {
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var channel: Channel<BackEventCompat>
    private lateinit var job: Job

    var onBack: (suspend (progress: Flow<BackEventCompat>) -> Unit)? = null
    var isEnabled: Boolean = true

    fun handleOnBackProgressed(touchX: Double, touchY: Double, progress: Double) {
        if (isEnabled) {
            scope.launch {
                runCatching {
                    channel.send(
                        BackEventCompat(
                            touchX.toFloat(),
                            touchY.toFloat(),
                            progress.toFloat().coerceIn(0f, 1f),
                            0
                        )
                    )
                }
            }
        }
    }

    fun recreate() {
        if (isEnabled) {
            channel = Channel(capacity = BUFFERED, onBufferOverflow = SUSPEND)
            job = createJob()
        }
    }

    fun close() {
        if (this::channel.isInitialized) {
            channel.close()
        }
    }

    fun cancel() {
        channel.cancel()
        job.cancel()
    }

    private fun createJob(): Job = scope.launch {
        var completed = false
        onBack?.invoke(channel.consumeAsFlow().onCompletion {
            completed = true
        })
        check(completed) {
            "You must collect the progress flow"
        }
    }
}