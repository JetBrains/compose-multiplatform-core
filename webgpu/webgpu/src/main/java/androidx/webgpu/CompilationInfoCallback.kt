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

public fun interface CompilationInfoCallback {
    /**
     * A callback function invoked with the compilation results for a shader module.
     *
     * @param status The status of the compilation info request.
     * @param compilationInfo Information about the shader module's compilation process.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("onCompilationInfo")
    public fun onCompilationInfo(
        @CompilationInfoRequestStatus status: Int,
        compilationInfo: CompilationInfo,
    )
}

internal class CompilationInfoCallbackRunnable(
    private val callback: CompilationInfoCallback,
    private val status: Int,
    private val compilationInfo: CompilationInfo,
) : Runnable {
    override fun run() {
        callback.onCompilationInfo(status, compilationInfo)
    }
}
