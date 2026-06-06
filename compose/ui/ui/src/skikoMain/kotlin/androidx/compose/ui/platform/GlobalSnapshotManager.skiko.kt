/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Platform-specific mechanism for monitoring global snapshot state writes and scheduling the
 * dispatch of snapshot apply notifications.
 *
 * Unlike Android, which has a single UI thread, a multiplatform process can host several owner
 * contexts at once (e.g. several [FrameRecomposer]s, or an off-UI [ImageComposeScene]). Apply
 * notifications must therefore be delivered on each owner's context, not on a single global one.
 *
 * [ensureStarted] registers an owner [CoroutineContext]: it observes global writes and, coalesced per
 * batch, sends [Snapshot.sendApplyNotifications] on that context. Registrations are **shared and
 * reference-counted** per context - all callers passing the same [CoroutineContext] share a single
 * observer and apply pump, and that registration is released only when the **last** returned
 * [AutoCloseable] is closed. This lets several owners that share one host context (e.g. a main scene
 * and its overlay layers) co-exist without one owner's shutdown tearing the registration out from
 * under the others.
 */
internal object GlobalSnapshotManager {
    private val lock = makeSynchronizedObject()

    /** Live registrations keyed by the host context they pump on. Guarded by [lock]. */
    private val registrations = mutableMapOf<CoroutineContext, Registration>()

    /**
     * Ensures global snapshot writes schedule coalesced [Snapshot.sendApplyNotifications] on the host
     * described by [coroutineContext], starting a shared registration on the first call for that
     * context. Close the returned [AutoCloseable] when done; the underlying observer/pump is released
     * only once every caller for this context has closed its handle.
     */
    fun ensureStarted(coroutineContext: CoroutineContext): AutoCloseable? {
        // Skip registration for inline dispatchers. Otherwise, it might lead to deadlocks.
        if (!coroutineContext.isDispatchNeeded()) {
            return null
        }
        val registration = synchronized(lock) {
            registrations.getOrPut(coroutineContext) { Registration(coroutineContext) }
                .also { it.refCount++ }
        }
        return AutoCloseable { release(registration) }
    }

    private fun release(registration: Registration) {
        synchronized(lock) {
            if (--registration.refCount > 0) return
            registrations.remove(registration.coroutineContext)
        }
        registration.dispose()
    }

    /** A shared observer + coalescing apply pump for one host [coroutineContext]. */
    private class Registration(val coroutineContext: CoroutineContext) {
        /** Number of live handles. Guarded by [GlobalSnapshotManager.lock]. */
        var refCount = 0

        private val scheduled = atomic(false)
        private val channel = Channel<Unit>(Channel.CONFLATED)
        private val scope = CoroutineScope(coroutineContext + Job())
        private val writeObserverHandle: ObserverHandle

        init {
            scope.launch {
                channel.consumeEach {
                    scheduled.value = false
                    Snapshot.sendApplyNotifications()
                }
            }
            writeObserverHandle = Snapshot.registerGlobalWriteObserver {
                if (scheduled.compareAndSet(expect = false, update = true)) {
                    channel.trySend(Unit)
                }
            }
        }

        fun dispose() {
            writeObserverHandle.dispose()
            channel.close()
            scope.cancel()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineContext.isDispatchNeeded(): Boolean {
    // Note: [isDispatchNeeded] is a per-resume, thread-dependent property, so an *immediate*
    // dispatcher cannot be soundly classified by this one-time registration check.
    val dispatcher = get(ContinuationInterceptor) as? CoroutineDispatcher
    return dispatcher != null && dispatcher.isDispatchNeeded(this)
}
