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

package androidx.compose.runtime

import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.mock.BufferedTestDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pin rotation delivers its pending union at the end of [SnapshotHolder.rotate]. Those handlers
 * read, so they need a view - and specifically the SUCCESSOR's view, since `delivered ⊆ visible`
 * means every token handed over must already be visible in the view the handler reads through.
 *
 * Without a read scope around that dispatch, a foreign source has no view at all, and the substrate
 * would be read through whatever the calling thread happened to have current.
 */
@OptIn(InternalComposeApi::class)
class DataSourceRotationViewTests {

    @Test
    fun tokensDeliveredAtRotationAreReadableThroughTheSuccessorsView() {
        val source = BufferedTestDataSource(requiresBoundView = true)
        val context = DataSourceContext(source)
        val holder = SnapshotHolder(context, isolating = true).also { it.activate() }
        val seen = mutableListOf<Set<Any>>()
        var readInHandler: Int? = null
        val handle =
            holder.registerApplyObserver { changed, _ ->
                seen.add(changed)
                // Runs from inside rotate()'s dispatch. Two things must hold: the source can read
                // at all (some view is bound), and what it reads is the successor's view - the one
                // that already contains the delivered tokens' values.
                context.observe(recordDependency = { false }, recordChange = null) {
                    readInHandler = source.read("k")
                }
            }
        try {
            source.write("k", 7)
            // Publishes the buffered write and pushes its identifiers into every open domain's
            // pending union - ours included, since the commit is not attributed to our cycle.
            source.advanceAndInvalidate()
            assertTrue(seen.isEmpty(), "nothing is delivered to this domain before its rotation")

            holder.rotate()

            assertTrue(seen.isNotEmpty(), "the rotation delivered this domain's pending union")
            assertEquals(
                7,
                readInHandler,
                "delivered subset visible: the handler read the successor's view",
            )
        } finally {
            handle.dispose()
            holder.close()
        }
    }

    /**
     * Invalidation dispatch is NOT read-only work. Compose's own apply observers write: stock
     * `NodeCoordinator.onCommitAffectingLayerParams` re-derives layer parameters, and
     * `Transition.updateTargetValue` writes animation state while doing so. That is AOSP code, so
     * "wrap the write site" is not available as a fix.
     *
     * A transaction restores the thread before it publishes, so its dispatch runs in the ENCLOSING
     * scope. When an ingress has entered the frame's read-only view - which is the whole point of
     * the read scope - that scope rejects writes, and a stock animation crashes the frame.
     * Dispatching must therefore be write-capable.
     */
    @Test
    fun anObserverDispatchedFromAPublishInsideAReadScopeMayWrite() {
        val trigger = mutableStateOf(0)
        val target = mutableStateOf(0)
        val holder = SnapshotHolder(DataSourceContext(), isolating = true).also { it.activate() }
        val handle =
            holder.registerApplyObserver { changed, _ ->
                if (trigger in changed) target.value = target.value + 1
            }
        try {
            val frame = holder.current as DataSourceContext.Snapshot
            // Exactly the render shape: an ingress enters the frame view, then inner work opens and
            // closes a transaction inside it.
            frame.enter { frame.withTransaction { trigger.value = 1 } }
            assertEquals(1, target.value, "an apply observer must be able to write")
        } finally {
            handle.dispose()
            holder.close()
        }
    }
}
