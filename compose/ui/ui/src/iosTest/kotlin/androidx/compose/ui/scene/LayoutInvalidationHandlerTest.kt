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

package androidx.compose.ui.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class LayoutInvalidationHandlerTest {

    @Test
    fun invalidationOutsidePostponeInvokesImmediately() = runTest {
        var count = 0
        val handler = LayoutInvalidationHandler(coroutineContext) { count++ }

        handler.invalidateLayoutIfNeeded()
        assertEquals(1, count, "invalidation outside a postpone block must run synchronously")

        handler.invalidateLayoutIfNeeded()
        assertEquals(2, count, "each invalidation outside a postpone block triggers a layout pass")
    }

    @Test
    fun invalidationDuringPostponeIsDeferredThenFlushed() = runTest {
        var count = 0
        val handler = LayoutInvalidationHandler(coroutineContext) { count++ }

        handler.postponeLayoutInvalidationCalls {
            handler.invalidateLayoutIfNeeded()
            assertEquals(0, count, "invalidations must not run synchronously while postponed")
        }

        // The pending invalidation is flushed asynchronously once the block completes.
        flushPendingLaunches()
        assertEquals(1, count, "a postponed invalidation must be delivered after the block")
    }

    @Test
    fun multipleDeferredInvalidationsCollapseIntoOne() = runTest {
        var count = 0
        val handler = LayoutInvalidationHandler(coroutineContext) { count++ }

        handler.postponeLayoutInvalidationCalls {
            repeat(5) { handler.invalidateLayoutIfNeeded() }
        }

        flushPendingLaunches()
        assertEquals(1, count, "coalesced invalidations must produce a single layout pass")
    }

    @Test
    fun repeatedPostponeDoesNotFlushWhenNothingWasInvalidated() = runTest {
        var count = 0
        val handler = LayoutInvalidationHandler(coroutineContext) { count++ }

        // Regression guard: the "has invalidations" flag must be reset after each flush, otherwise
        // every subsequent postponed render would schedule a spurious layout pass.
        handler.postponeLayoutInvalidationCalls {
            handler.invalidateLayoutIfNeeded()
        }
        flushPendingLaunches()
        assertEquals(1, count)

        repeat(3) {
            handler.postponeLayoutInvalidationCalls {
                // No invalidation requested during this render.
            }
            flushPendingLaunches()
        }
        assertEquals(1, count, "an idle postpone block must not schedule a layout pass")
    }

    @Test
    fun invalidationAfterDisposalIsIgnored() = runTest {
        var count = 0
        val job = Job(coroutineContext.job)
        val handler = LayoutInvalidationHandler(coroutineContext + job) { count++ }

        handler.invalidateLayoutIfNeeded()
        assertEquals(1, count)

        // Completing the scope's job must detach the invalidation callback.
        job.cancelAndJoin()

        handler.invalidateLayoutIfNeeded()
        assertEquals(1, count, "no invalidation must be delivered after the scope is disposed")
    }

    /**
     * Lets any coroutines scheduled via `scope.launch` on the [runTest] test dispatcher run to
     * completion before assertions are made.
     */
    private suspend fun flushPendingLaunches() {
        repeat(10) { yield() }
    }
}
