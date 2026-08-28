/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import androidx.compose.ui.util.traceValue

internal class WebPrefetchScheduler : PlatformPrefetchScheduler {
    private val highPriorityPrefetchRequests = ArrayDeque<PlatformPrefetchRequest>()
    private val lowPriorityPrefetchRequests = ArrayDeque<PlatformPrefetchRequest>()

    private val scope = WebPrefetchRequestScope()
    
    /** The handle returned by the last call to [requestIdleCallback]. It is used to cancel the callback if a new request is scheduled before the previous one is executed. */
    private var idleCallbackHandle: Int = -1
    private var isDisposed = false
    private val onIdleCallback = { deadline : IdleDeadline ->
        processPrefetchRequests(deadline)
    }

    fun hasWorkScheduled(): Boolean =
        highPriorityPrefetchRequests.isNotEmpty() || lowPriorityPrefetchRequests.isNotEmpty()

    override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) {
        if (isDisposed) return
        highPriorityPrefetchRequests.addLast(request)
        schedulePrefetchRequests()
    }

    override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) {
        if (isDisposed) return
        lowPriorityPrefetchRequests.addLast(request)
        schedulePrefetchRequests()
    }

    private fun schedulePrefetchRequests() {
        if (!idleCallbackHandle.isPrefetchScheduled()) {
            idleCallbackHandle = requestIdleCallback(onIdleCallback)
        }
    }

    /**
     * Executes the next request in line depending on its priority and whether it has enough time to perform it
     * @return Whether the request has more work to do and should be scheduled for another idle frame
     */
    private fun PlatformPrefetchRequestScope.executeNextRequest(availableTimeNanos : Long): Boolean {
        traceValue("compose:lazy:prefetch:available_time_nanos", availableTimeNanos)

        return if (availableTimeNanos > 0) {
            val requestQueue = when {
                highPriorityPrefetchRequests.isNotEmpty() -> highPriorityPrefetchRequests
                lowPriorityPrefetchRequests.isNotEmpty() -> lowPriorityPrefetchRequests
                else -> return false
            }
            val hasMoreWorkToDo = with(requestQueue.first()) {
                execute()
            }
            if (!hasMoreWorkToDo) {
                requestQueue.removeFirst()
            }
            hasMoreWorkToDo
        } else {
            true
        }
    }

    private fun processPrefetchRequests(deadline: IdleDeadline) {
        idleCallbackHandle = -1

        if (isDisposed || !hasWorkScheduled()) {
            return
        }

        scope.deadline = deadline

        while (hasWorkScheduled()) {
            val availableTimeNanos = scope.availableTimeNanos()
            if (availableTimeNanos <= 0 && !deadline.didTimeout) break
            val hasMoreWorkToDo = scope.executeNextRequest(availableTimeNanos)
            if (hasMoreWorkToDo) break
        }

        if (hasWorkScheduled() && !isDisposed) {
            schedulePrefetchRequests()
        }
    }

    fun dispose() {
        isDisposed = true
        if (idleCallbackHandle.isPrefetchScheduled()) {
            cancelIdleCallback(idleCallbackHandle)
            idleCallbackHandle = -1
        }
        highPriorityPrefetchRequests.clear()
        lowPriorityPrefetchRequests.clear()
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun Int.isPrefetchScheduled() : Boolean = this != -1


    private class WebPrefetchRequestScope : PlatformPrefetchRequestScope {
        var deadline: IdleDeadline? = null
        override fun availableTimeNanos(): Long {
            val currentDeadline = deadline ?: return 0L
            val remainingMs = currentDeadline.timeRemaining()
            return if (remainingMs > 0) (remainingMs * 1_000_000).toLong() else 0L
        }
    }
}

private external interface IdleDeadline : JsAny {
    fun timeRemaining(): Double
    val didTimeout: Boolean
}

internal val isIdleCallbackSupported: Boolean by lazy {
    isIdleApiSupported()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun isIdleApiSupported(): Boolean = js("Boolean('requestIdleCallback' in window)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestIdleCallback(callback: (IdleDeadline) -> Unit): Int =
    //language=JavaScript
    js("window.requestIdleCallback(callback)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelIdleCallback(handle: Int) {
    //language=JavaScript
    js("window.cancelIdleCallback(handle)")
}