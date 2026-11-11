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

package org.jetbrains.compose.benchmarks.app

import kotlin.time.TimeSource
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(ExperimentalForeignApi::class)
internal actual fun getProcessStartTime(): TimeSource.Monotonic.ValueTimeMark {
    memScoped {
        val mib = allocArray<IntVar>(4)
        mib[0] = CTL_KERN
        mib[1] = KERN_PROC
        mib[2] = KERN_PROC_PID
        mib[3] = getpid()

        val size = alloc<size_tVar>()
        size.value = sizeOf<kinfo_proc>().convert()
        val info = alloc<kinfo_proc>()

        if (sysctl(mib, 4u, info.ptr, size.ptr, null, 0uL) != 0) {
            error("Failed to get process start time")
        }

        // extern_proc.p_un (a union) is the first field of extern_proc, which is the first field of
        // kinfo_proc. The union member __p_starttime (timeval) starts at offset 0 within the union.
        // Therefore timeval is at offset 0 of kinfo_proc, and we can read it directly via reinterpret.
        // Kotlin/Native doesn't expose extern_proc's inner fields, so we use this layout-based access.
        val startTime = info.ptr.reinterpret<timeval>().pointed

        val now = alloc<timeval>()
        gettimeofday(now.ptr, null)

        val elapsedNs = (now.tv_sec - startTime.tv_sec) * 1_000_000_000L +
                        (now.tv_usec - startTime.tv_usec) * 1_000L

        return TimeSource.Monotonic.markNow() - elapsedNs.nanoseconds
    }
}
