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

package androidx.compose.foundation.lazy.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
internal actual fun rememberDefaultPrefetchScheduler(): PrefetchScheduler {
    return remember {
        if(isIdleCallbackSupported) WebPrefetchScheduler() else NoOpPrefetchScheduler
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private val isIdleCallbackSupported: Boolean by lazy {
    isIdleApiSupported()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun isIdleApiSupported(): Boolean = js("Boolean('requestIdleCallback' in window)")

@Suppress("DEPRECATION")
private class WebPrefetchScheduler : RememberObserver, PriorityPrefetchScheduler {

    /**
     * List of pending prefetch requests.
     * High-priority requests are always at the beginning of the list, and low-priority requests are at the end of the list.
     *
     * When a high-priority request is added, it is inserted at the index of [highPriorityCount], and then [highPriorityCount] is incremented.
     * This way, all high-priority requests are always before low-priority requests in the list.
     */
    private val prefetchRequests = ArrayDeque<PrefetchRequest>()

    /**
     * Number of high-priority requests at the beginning of [prefetchRequests].
     * This allows having requests with priority in a single list and executing them in the right order without needing to reorder the list when new high-priority requests are added.
     */
    private var highPriorityCount = 0
    private var prefetchScheduled = false
    private var isActive = false
    private var idleCallbackHandle: Int = -1

    private var scope: WebPrefetchRequestScope? = null

    private val onIdleCallback = toJsCallback { deadline ->
        processPrefetchRequests(deadline)
    }

    override fun scheduleLowPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        prefetchRequests.addLast(prefetchRequest)
        startScheduling()
    }

    override fun scheduleHighPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        prefetchRequests.add(highPriorityCount, prefetchRequest)
        highPriorityCount++
        startScheduling()
    }

    private fun startScheduling() {
        if (!prefetchScheduled && isActive) {
            prefetchScheduled = true
            scheduleIdleCallback()
        }
    }

    private fun scheduleIdleCallback() {
        if (idleCallbackHandle != -1) {
            cancelIdleCallback(idleCallbackHandle)
        }
        idleCallbackHandle = requestIdleCallback(onIdleCallback)
    }

    private fun processPrefetchRequests(deadline: IdleDeadline) {
        idleCallbackHandle = -1

        if (!isActive || prefetchRequests.isEmpty()) {
            prefetchScheduled = false
            return
        }

        val scope = scope?.apply { updateDeadline(deadline) }
            ?: WebPrefetchRequestScope(deadline).also { scope = it }

        while (prefetchRequests.isNotEmpty()) {
            if (scope.availableTimeNanos() <= 0 && !deadline.didTimeout) {
                break
            }

            val task = prefetchRequests.first()
            val hasMoreWorkToDo = with(task) { scope.execute() }

            if (!hasMoreWorkToDo) {
                prefetchRequests.removeFirst()
                if (highPriorityCount > 0) highPriorityCount--
            } else break
        }

        if (prefetchRequests.isNotEmpty()) {
            scheduleIdleCallback()
        } else {
            prefetchScheduled = false
        }
    }

    override fun onRemembered() {
        isActive = true
        if (prefetchRequests.isNotEmpty()) {
            startScheduling()
        }
    }

    override fun onForgotten() {
        isActive = false
        if (idleCallbackHandle != -1) {
            cancelIdleCallback(idleCallbackHandle)
            idleCallbackHandle = -1
        }
        prefetchRequests.clear()
        highPriorityCount = 0
    }

    override fun onAbandoned() = onForgotten()

    private class WebPrefetchRequestScope(
        private var deadline: IdleDeadline,
    ) : PrefetchRequestScope {

        fun updateDeadline(newDeadline: IdleDeadline) {
            deadline = newDeadline
        }

        override fun availableTimeNanos(): Long {
            val remainingMs = deadline.timeRemaining()
            return if (remainingMs > 0) (remainingMs * 1_000_000).toLong() else 0
        }
    }
}


private external interface IdleDeadline {
    fun timeRemaining(): Double
    val didTimeout: Boolean
}


@OptIn(ExperimentalWasmJsInterop::class)
private fun requestIdleCallback(callback: JsAny): Int =
    //language=JavaScript
    js("window.requestIdleCallback(callback)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelIdleCallback(handle: Int) {
    //language=JavaScript
    js("window.cancelIdleCallback(handle)")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun toJsCallback(callback: (IdleDeadline) -> Unit): JsAny =
    //language=JavaScript
    js("(deadline) => callback(deadline)")