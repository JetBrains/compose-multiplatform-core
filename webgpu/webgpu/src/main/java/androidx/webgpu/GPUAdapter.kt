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

import dalvik.annotation.optimization.FastNative
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Represents an abstract graphics card on the system. */
public class GPUAdapter private constructor(public val handle: Long) : AutoCloseable {
    /** Gets the set of features supported by the adapter. */
    @FastNative @JvmName("getFeatures") public external fun getFeatures(): SupportedFeatures

    /**
     * Gets detailed information about the adapter.
     *
     * @return Status code of the operation.
     */
    @FastNative
    @JvmName("getInfo")
    @Throws(WebGpuException::class)
    public external fun getInfo(): AdapterInfo

    /**
     * Gets the limits supported by the adapter.
     *
     * @return Status code of the operation.
     */
    @FastNative
    @JvmName("getLimits")
    @Throws(WebGpuException::class)
    public external fun getLimits(): Limits

    /**
     * Checks if a specific feature is supported by the adapter.
     *
     * @param feature The feature to check for support.
     * @return True if the feature is supported, {@code false} otherwise.
     */
    @FastNative
    @JvmName("hasFeature")
    public external fun hasFeature(@FeatureName feature: Int): Boolean

    /** Requests a GPU device object from the adapter asynchronously. */
    @FastNative
    @JvmName("requestDevice")
    @JvmOverloads
    public external fun requestDevice(
        callbackExecutor: java.util.concurrent.Executor,
        descriptor: DeviceDescriptor? = null,
        callback: RequestDeviceCallback,
    ): Unit

    /**
     * Requests a GPU device object from the adapter asynchronously.
     *
     * @param descriptor A descriptor specifying creation options for the device.
     */
    @Throws(WebGpuException::class)
    public suspend fun requestDevice(descriptor: DeviceDescriptor? = null): GPUDevice =
        suspendCancellableCoroutine {
            requestDevice(
                Executor(Runnable::run),
                descriptor,
                { status, message, device ->
                    if (!it.isActive) {
                        // Coroutine was aborted.
                    } else if (status != Status.Success) {
                        it.resumeWithException(WebGpuException(status = status, reason = message))
                    } else if (device == null) {
                        it.resumeWithException(
                            WebGpuException(status = status, reason = "Null value returned")
                        )
                    } else {
                        it.resume(device)
                    }
                },
            )
        }

    external override fun close()

    override fun equals(other: Any?): Boolean = other is GPUAdapter && other.handle == handle

    override fun hashCode(): Int = handle.hashCode()
}
