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

import androidx.collection.mutableObjectListOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

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

private fun isIdleApiSupported(): Boolean = js("Boolean('requestIdleCallback' in window)")

@Suppress("DEPRECATION")
private class WebPrefetchScheduler : PrefetchScheduler, RememberObserver, PriorityPrefetchScheduler {

    private val prefetchRequests = mutableObjectListOf<PrefetchRequest>()
    private var prefetchScheduled = false
    private var isActive = false
    private var idleCallbackHandle: Int? = null

    private var scope: WebPrefetchRequestScope? = null

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) =
        scheduleHighPriorityPrefetch(prefetchRequest)

    override fun scheduleLowPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        prefetchRequests.add(prefetchRequest)
        startScheduling()
    }

    override fun scheduleHighPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        prefetchRequests.add(0, prefetchRequest)
        startScheduling()
    }

    private fun startScheduling() {
        if (!prefetchScheduled && isActive) {
            prefetchScheduled = true
            scheduleIdleCallback()
        }
    }

    private fun scheduleIdleCallback() {
        idleCallbackHandle?.let { cancelIdleCallback(it) }
        idleCallbackHandle = requestIdleCallback { deadline ->
            processPrefetchRequests(deadline)
        }
    }

    private fun processPrefetchRequests(deadline: IdleDeadline) {
        idleCallbackHandle = null

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

            val task = prefetchRequests.first { true }
            val hasMoreWorkToDo = with(task) { scope.execute() }

            if (!hasMoreWorkToDo) {
                prefetchRequests.removeAt(0)
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
        idleCallbackHandle?.let {
            cancelIdleCallback(it)
            idleCallbackHandle = null
        }
        prefetchRequests.clear()
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
private fun requestIdleCallback(
    callback: (IdleDeadline) -> Unit,
): Int = //language=JavaScript
    js("window.requestIdleCallback(callback)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelIdleCallback(handle: Int) {
    //language=JavaScript
    js("window.cancelIdleCallback(handle)")
}