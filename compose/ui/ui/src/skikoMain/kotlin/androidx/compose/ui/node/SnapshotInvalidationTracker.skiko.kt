/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.internal.getCurrentThreadId
import androidx.compose.ui.platform.makeSynchronizedObject
import androidx.compose.ui.platform.synchronized
import androidx.compose.ui.util.fastForEach
import kotlinx.atomicfu.atomic

/**
 * SnapshotCommandList is a class that manages commands and invalidations for snapshot-based
 * recomposition. It allows postponing execution of commands and performing them in the future.
 *
 * @param invalidate a function that is called whenever an invalidation is requested
 * @param deliveryDomain the scene's frame domain, threaded to the [OwnerSnapshotObserver] so its
 *   [androidx.compose.runtime.snapshots.SnapshotStateObserver] registers as a domain-scoped apply
 *   observer instead of a global one.
 */
internal class SnapshotInvalidationTracker(
    private val invalidate: () -> Unit = {},
    private val deliveryDomain: SnapshotHolder? = null,
) {
    private val snapshotChanges = CommandList(invalidate)
    private var needMeasureAndLayout = true
    private var needDraw = true

    /**
     * The id of the thread currently inside [performSnapshotChangesSynchronously].
     *
     * Note that it's not valid to have more than one thread calling it at the same time.
     */
    private var renderingThreadId: Long? by atomic(null)

    val hasInvalidations: Boolean
        get() = needMeasureAndLayout || needDraw || snapshotChanges.hasCommands

    fun requestMeasureAndLayout() {
        needMeasureAndLayout = true
        invalidate()
    }

    fun onMeasureAndLayout() {
        needMeasureAndLayout = false
    }

    fun requestDraw() {
        needDraw = true
        invalidate()
    }

    fun onDraw() {
        needDraw = false
    }

    /**
     * Creates an observer for monitoring changes in the snapshot of an owner.
     *
     * @return the observer for monitoring snapshot changes
     */
    fun snapshotObserver() =
        OwnerSnapshotObserver(deliveryDomain = deliveryDomain) { command ->
            if (renderingThreadId == getCurrentThreadId()) command()
            else snapshotChanges.add(command)
        }

    /**
     * Sends any pending apply notifications and performs the changes they cause.
     *
     * With a delivery domain present this drains the scene's whole [DataSourceContext] rather than
     * only the snapshot substrate - a strict superset, since
     * `DataSourceContext.advanceGlobalSnapshot()` ends in `Snapshot.sendApplyNotifications()`. This
     * function runs immediately before and after every scene entry point (render, input events,
     * setContent) via `BaseComposeScene.postponeInvalidation`, so foreign sources are drained at
     * exactly the boundaries the substrate is drained at - and never inside a frame span, where
     * publishing a foreign source would tear the frame.
     */
    fun sendAndPerformSnapshotChanges() {
        val context = deliveryDomain?.context
        if (context != null) {
            context.advanceGlobalSnapshot()
        } else {
            Snapshot.sendApplyNotifications()
        }
        snapshotChanges.perform()
    }

    /**
     * Runs [block], performing any snapshot changes it generates synchronously.
     *
     * See [OwnerSnapshotObserverTest.observeReadsChangedBeforeDisposeEffect] for more details.
     */
    inline fun <T> performSnapshotChangesSynchronously(block: () -> T): T {
        return try {
            renderingThreadId = getCurrentThreadId()
            block()
        } finally {
            renderingThreadId = null
        }
    }
}

/**
 * Allows postponing execution of some code (command), adding it to the list via [add], and
 * performing all added commands in some time in the future via [perform]
 */
private class CommandList(private var onNewCommand: () -> Unit) {
    private val lock = makeSynchronizedObject()
    private val list = mutableListOf<() -> Unit>()
    private val listCopy = mutableListOf<() -> Unit>()

    /**
     * true if there are any commands added.
     *
     * Can be called concurrently from multiple threads.
     */
    val hasCommands: Boolean
        get() = synchronized(lock) { list.isNotEmpty() }

    /**
     * Add command to the list, and notify observer via [onNewCommand].
     *
     * Can be called concurrently from multiple threads.
     */
    fun add(command: () -> Unit) {
        synchronized(lock) { list.add(command) }
        onNewCommand()
    }

    /**
     * Clear added commands and perform them.
     *
     * Doesn't support multiple [perform]'s from different threads. But does support concurrent
     * [perform] and concurrent [add].
     */
    fun perform() {
        synchronized(lock) {
            listCopy.addAll(list)
            list.clear()
        }
        listCopy.fastForEach { it.invoke() }
        listCopy.clear()
    }
}
