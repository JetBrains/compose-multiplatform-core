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

package androidx.compose.runtime.internal

import androidx.compose.runtime.DataSource
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Mutable cell carrying a scene's current cycle unit to the Recomposer and the
 * effect dispatcher — the explicit-reachability alternative to any thread-current.
 */
class SnapshotHolder : CoroutineContext.Element {
    var current: DataSource.Snapshot? = null

    /** Set once by [close]; late frame work then runs un-isolated instead of failing. */
    @Volatile
    var isClosed: Boolean = false
        private set

    /**
     * The active frame-cycle unit, or `null` once the holder is [close]d — a frame
     * dispatch or effect task that was already queued when its scene closed falls back
     * to the stock, un-isolated path (the scene is gone; nothing consumes its output).
     * While the holder is open, a missing unit is a lifecycle bug and fails fast.
     */
    val checkedCurrent: DataSource.Snapshot?
        get() =
            current
                ?: run {
                    check(isClosed) {
                        "Frame isolation is enabled but no snapshot has been set at frame start"
                    }
                    null
                }

    /** Ends the holder's life: [checkedCurrent] returns `null` from now on. */
    fun close() {
        isClosed = true
        current = null
    }

    override val key: CoroutineContext.Key<*>
        get() = Key

    companion object Key : CoroutineContext.Key<SnapshotHolder>
}
