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
import kotlin.coroutines.CoroutineContext

/**
 * Mutable cell carrying a scene's current cycle unit to the Recomposer and the
 * effect dispatcher — the explicit-reachability alternative to any thread-current.
 */
class SnapshotHolder : CoroutineContext.Element {
    var current: DataSource.Snapshot? = null

    val checkedCurrent: DataSource.Snapshot get() = checkNotNull(current) {
        "Frame isolation is enabled but no snapshot has been set at frame start"
    }

    override val key: CoroutineContext.Key<*>
        get() = Key

    companion object Key : CoroutineContext.Key<SnapshotHolder>
}