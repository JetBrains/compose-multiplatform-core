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
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.ObserverHandle
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

/**
 * Drives a composition tree over a [DataSourceContext] without a scene — the application-level
 * composition's counterpart to a scene's holder plus render-loop rotation.
 *
 * Include [recomposerContext] in the [Recomposer]'s construction context. It carries the
 * [androidx.compose.runtime.internal.SnapshotHolder] (which flips `Recomposer.composing` onto the
 * context-observing branches: source reads record dependencies and passes run in composite units)
 * and an effect dispatcher that runs every scheduled task as one isolation slice.
 *
 * With [isolating] = false, every recompose pass and effect task is an internally atomic slice;
 * generations may advance between slices, and nothing pins between them. With [isolating] = true
 * the domain holds a standing cycle unit — everything between rotations shares one generation —
 * rotation is wake-driven via the domain's own [SnapshotHolder.onPendingDelivery]: a foreign commit
 * lands in this domain's pending union and fires the wake, which schedules the swap (the
 * render-loop role without a render loop). A starved async queue delays only this domain's own
 * delivery (bounded union), never anyone else.
 *
 * Composition `withFrameNanos` awaiters need no extra treatment here: they resume through the
 * Recomposer's internal frame dispatch, whose segments already slice against the holder.
 *
 * [close] must not be called from inside one of the domain's own slices (the standing unit cannot
 * be disposed while a child transaction is open): call it after the composition is disposed, on
 * [dispatcher].
 */
@OptIn(InternalComposeApi::class)
class DataSourceCompositionDomain(
    val dataSourceContext: DataSourceContext,
    private val isolating: Boolean,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    // Declared before `holder`/`contextWakeHandle` below: both wire up `::scheduleRotation` as a
    // wake during their own initialization (`holder`'s `onPendingDelivery`, then
    // `contextWakeHandle`'s registration with the context), and a wake firing in that window
    // reads/writes these four properties. They must therefore already be initialized by then.
    private val rotationLock = makeSynchronizedObject()
    private var rotationDue = false
    private var asyncRotationQueued = false
    private var isClosed = false

    private val holder =
        SnapshotHolder(dataSourceContext, isolating).apply {
            if (isolating) {
                // Wake wired before activation so no push can ever land unnoticed.
                onPendingDelivery = ::scheduleRotation
                activate()
            }
        }

    private val pumpHandle: ObserverHandle? =
        if (isolating) registerScenelessRotationPump(::rotateIfDue) else null

    /**
     * A member of this domain's context holding unpublished data must be able to cause a pass that
     * drains it, exactly as a foreign commit can cause one. Registered unconditionally: with
     * [isolating] false there is no pin to swap, but the drain in [rotateIfDue] still has to
     * happen.
     *
     * Fully qualified because this file imports the identically-named
     * `androidx.compose.runtime.snapshots.ObserverHandle` for the pump handle above, while
     * `registerWake` returns the one declared in `DataSource.kt`.
     */
    private val contextWakeHandle: androidx.compose.runtime.ObserverHandle =
        dataSourceContext.registerWake(::scheduleRotation)

    /** Context elements for this domain's [Recomposer]: add to its construction context. */
    val recomposerContext: CoroutineContext = SliceDispatcher() + holder

    /**
     * Marks a rotation due and keeps at most ONE async task in flight: the platform main loop may
     * starve its async queue for long stretches under continuous rendering, and launching per
     * notification would then pile dead tasks into the very queue that is starving. A starved queue
     * only delays this domain's own delivery — its pending union is bounded by distinct tokens, and
     * nobody else is gated on it.
     */
    private fun scheduleRotation() {
        val launchTask =
            synchronized(rotationLock) {
                if (isClosed) return
                rotationDue = true
                if (asyncRotationQueued) {
                    false
                } else {
                    asyncRotationQueued = true
                    true
                }
            }
        if (launchTask) {
            coroutineScope.launch(dispatcher) {
                synchronized(rotationLock) { asyncRotationQueued = false }
                rotateIfDue()
            }
        }
    }

    /**
     * The pin swap, performed by the async-scheduled task on the domain's single UI thread, so
     * swaps never race the domain's slices. [SnapshotHolder.rotate] swaps the pending union out
     * before taking the successor unit, so delivered tokens are always already visible in the new
     * unit's view.
     */
    private fun rotateIfDue() {
        synchronized(rotationLock) {
            if (!rotationDue || isClosed) return
            rotationDue = false
        }
        // Drain the context's members BEFORE the swap, so whatever they publish is included
        // in the successor's view and its identifiers are delivered by this same rotation
        // (`delivered subset visible`). Draining after the swap would deliver tokens against
        // a view that predates their values.
        dataSourceContext.advanceGlobalSnapshot()
        holder.rotate()
    }

    /**
     * Ends the domain: unregisters it and drops its pending union (its only consumers are the
     * domain's own, which are being torn down along with it). Already-queued work falls back to the
     * stock un-isolated path ([SnapshotHolder.checkedCurrent] turns null).
     */
    fun close() {
        synchronized(rotationLock) {
            if (isClosed) return
            isClosed = true
        }
        pumpHandle?.dispose()
        contextWakeHandle.dispose()
        holder.close()
    }

    /** Every dispatched task is one isolation slice, published atomically on return. */
    // The domain's slices and its async rotation task execute on the same UI thread (via
    // [dispatcher]), which is what makes the unit swap lock-free.
    private inner class SliceDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcher.dispatch(
                context,
                Runnable {
                    val unit = holder.checkedCurrent
                    when {
                        unit != null -> unit.withTransaction { block.run() }
                        isolating -> block.run() // closed: stock fallback
                        else -> dataSourceContext.withTransaction { block.run() }
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// The render-path rotation pump.
//
// Per-consumer delivery removed the pump's original justification (a starved domain no
// longer gates anyone's DELIVERY - its pending union is bounded), but a starved domain's
// standing pin still has a process-wide cost: an open old snapshot forces the runtime to
// RETAIN superseded state records for every object written since, so under a hot writer
// the record chains - and with them every snapshot read - grow without bound. Rotation is
// what collapses that retention, and the async main-thread queue that schedules it is
// exactly what saturating render loads starve (KDT drains it only in idle gaps). So frame
// domains pump the pending rotations of scene-less domains from their render path:
// starvation implies scenes are rendering, so the pump runs; with no scenes rendering
// nothing saturates and the async task suffices. Only the pin swap is lifted onto the
// render path; the domain's recompose and effect slices stay on the async queue.
// ---------------------------------------------------------------------------------------

private val rotationPumpLock = makeSynchronizedObject()
private val rotationPumps = mutableListOf<() -> Unit>()

/** Registers a scene-less domain's pending-rotation pump. */
internal fun registerScenelessRotationPump(pump: () -> Unit): ObserverHandle {
    synchronized(rotationPumpLock) { rotationPumps.add(pump) }
    return ObserverHandle { synchronized(rotationPumpLock) { rotationPumps.remove(pump) } }
}

/**
 * Runs the pending pin rotations of all scene-less composition domains. Frame-domain integrations
 * call this from their render path's own rotation site, ON THE UI THREAD, outside any isolation
 * slice. Rotations are coalesced; with none due this is a cheap no-op.
 */
@InternalComposeApi
fun pumpScenelessDomainRotations() {
    val pumps = synchronized(rotationPumpLock) { rotationPumps.toList() }
    pumps.forEach { it() }
}
