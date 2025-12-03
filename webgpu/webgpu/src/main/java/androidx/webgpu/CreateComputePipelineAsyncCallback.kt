/*
 * Copyright (C) 2025 The Android Open Source Project
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

public fun interface CreateComputePipelineAsyncCallback {
    /**
     * A callback function invoked upon the completion of asynchronous compute pipeline creation.
     *
     * @param status The status of the asynchronous compute pipeline creation.
     * @param pipeline The created compute pipeline object on success.
     * @param message A human-readable message providing context on the status.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("onCreateComputePipelineAsync")
    public fun onCreateComputePipelineAsync(
        @CreatePipelineAsyncStatus status: Int,
        message: String,
        pipeline: GPUComputePipeline?,
    )
}

internal class CreateComputePipelineAsyncCallbackRunnable(
    private val callback: CreateComputePipelineAsyncCallback,
    private val status: Int,
    private val message: String,
    private val pipeline: GPUComputePipeline?,
) : Runnable {
    override fun run() {
        callback.onCreateComputePipelineAsync(status, message, pipeline)
    }
}
