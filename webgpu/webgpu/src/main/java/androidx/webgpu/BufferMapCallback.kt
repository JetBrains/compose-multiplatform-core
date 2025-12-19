/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.webgpu

public fun interface BufferMapCallback {
    /**
     * A callback function invoked when a buffer's mapAsync operation completes.
     *
     * @param status The status of the map operation (e.g., success, error, cancelled).
     * @param message A human-readable message providing context on the status.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("onBufferMap")
    public fun onBufferMap(@MapAsyncStatus status: Int, message: String)
}

internal class BufferMapCallbackRunnable(
    private val callback: BufferMapCallback,
    private val status: Int,
    private val message: String,
) : Runnable {
    override fun run() {
        callback.onBufferMap(status, message)
    }
}
