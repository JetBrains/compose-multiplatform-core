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

public fun interface PopErrorScopeCallback {
    /**
     * A callback function invoked when popErrorScope completes and returns the captured error.
     *
     * @param status The status of the error scope pop operation.
     * @param type The type of error captured by the scope, if any.
     * @param message A human-readable message describing the error, if any.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("onPopErrorScope")
    public fun onPopErrorScope(
        @PopErrorScopeStatus status: Int,
        @ErrorType type: Int,
        message: String,
    )
}

internal class PopErrorScopeCallbackRunnable(
    private val callback: PopErrorScopeCallback,
    private val status: Int,
    private val type: Int,
    private val message: String,
) : Runnable {
    override fun run() {
        callback.onPopErrorScope(status, type, message)
    }
}
