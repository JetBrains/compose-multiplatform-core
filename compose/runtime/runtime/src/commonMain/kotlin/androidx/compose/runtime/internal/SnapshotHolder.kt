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
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.enter
import androidx.compose.runtime.platform.makeSynchronizedObject
import androidx.compose.runtime.platform.synchronized
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.globalAttributionSnapshot
import androidx.compose.runtime.withTransaction
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * A frame domain: the cell carrying a scene's (or scene-less composition's)
 * [DataSourceContext], its current cycle unit while frame isolation is on, and - as the
 * DELIVERY DOMAIN - the pending invalidations other cycles have published since this
 * domain's last rotation, plus the observers that consume them.
 *
 * Delivery contract: observers registered here (the domain's Recomposer and state
 * observers) hear about a foreign commit at this domain's own next [rotate] - exactly when
 * the new cycle unit's view starts including the committed values. Globally registered
 * observers hear immediately. The committing domain's own observers hear immediately (the
 * same-frame contract).
 */
class SnapshotHolder(
    val context: DataSourceContext,
    val isolating: Boolean,
) : CoroutineContext.Element {
    var current: DataSource.Snapshot? = null

    @Volatile
    var isClosed: Boolean = false
        private set

    /**
     * The active frame-cycle unit. Null when frame isolation is off or after [close];
     * while isolating and open, a missing unit is a lifecycle bug and fails fast.
     */
    val checkedCurrent: DataSource.Snapshot?
        get() =
            current
                ?: run {
                    check(!isolating || isClosed) {
                        "Frame isolation is enabled but no snapshot has been set at frame start"
                    }
                    null
                }

    // --- the delivery domain ---

    private val domainLock = makeSynchronizedObject()
    private var pendingDelivery = LinkedHashSet<Any>()
    private var observers = emptyList<(Set<Any>, Snapshot) -> Unit>()

    /**
     * Content-free wake-up, fired (on the committing thread) when a foreign commit lands
     * in this domain's pending union: scenes schedule a render, scene-less domains their
     * rotation task.
     */
    var onPendingDelivery: (() -> Unit)? = null

    val hasPendingDelivery: Boolean
        get() = synchronized(domainLock) { pendingDelivery.isNotEmpty() }

    /** Registers a DOMAIN-scoped apply observer: delivered at this domain's rotations. */
    fun registerApplyObserver(observer: (Set<Any>, Snapshot) -> Unit): ObserverHandle {
        synchronized(domainLock) { observers += observer }
        return ObserverHandle { synchronized(domainLock) { observers -= observer } }
    }

    /** Takes the first cycle unit and registers this domain for delivery routing. */
    fun activate() {
        check(isolating) { "Only isolating holders own a standing cycle unit" }
        val unit = context.takeSnapshot()
        current = unit
        registerOpenDeliveryDomain(this, (unit as? DataSourceContext.Snapshot)?.substrateRoot)
    }

    /**
     * The pin rotation, in the ONE order that keeps `delivered ⊆ visible`:
     * (1) swap the pending union out - everything in it was pushed before the swap, hence
     * published before the take below, hence visible to the new view. A commit racing the
     * rotation lands in the fresh union and delivers next time, against a view that already
     * contains it (one-frame lag, self-correcting). Swapping AFTER the take would consume
     * tokens against a view that predates their values: a no-op recompose now and no
     * trigger left when the value arrives - permanently stale.
     * (2) take the successor and re-key attribution, (3) dispose the predecessor,
     * (4) deliver the swapped union on this (the domain's) thread, INSIDE the successor's read
     * scope - the handlers read, and `delivered ⊆ visible` is a statement about the view they
     * read through, so it only holds if that view is the successor's.
     *
     * The scope in step 4 is balanced and self-contained rather than a transfer of the calling
     * thread's current view: [rotate] is also called with no scope entered at all (a scene-less
     * domain's own async task) and from a thread holding a DIFFERENT domain's view (the render
     * thread pumping scene-less rotations), so transferring would strand the caller in the wrong
     * domain's pin.
     */
    fun rotate() {
        val old = current ?: return // isolation off, or closed: nothing pins
        val delivered =
            synchronized(domainLock) {
                if (pendingDelivery.isEmpty()) {
                    null
                } else {
                    pendingDelivery.also { pendingDelivery = LinkedHashSet() }
                }
            }
        val next = context.takeSnapshot()
        current = next
        rekeyOpenDeliveryDomain(this, (next as? DataSourceContext.Snapshot)?.substrateRoot)
        old.dispose()
        // `current` is already the successor, so dispatchNow enters and transacts on it.
        if (delivered != null) dispatchNow(delivered, globalAttributionSnapshot())
    }

    /**
     * Ends the domain: unregisters it from delivery routing, DROPS the pending union (its
     * only consumers are this domain's own, which are being torn down; nothing global was
     * ever gated on it), and disposes the standing unit. [checkedCurrent] returns null from
     * now on, so straggler work falls back to the stock un-isolated path.
     */
    fun close() {
        if (isClosed) return
        isClosed = true
        unregisterOpenDeliveryDomain(this)
        synchronized(domainLock) { pendingDelivery = LinkedHashSet() }
        val old = current
        current = null
        old?.dispose()
    }

    internal fun pushPendingDelivery(changed: Set<Any>) {
        synchronized(domainLock) { pendingDelivery.addAll(changed) }
        onPendingDelivery?.invoke()
    }

    /**
     * Notifies this domain's observers, in the frame's read scope AND a transaction.
     *
     * Dispatch is not read-only work: Compose's own apply observers write. Stock
     * `NodeCoordinator.onCommitAffectingLayerParams` re-derives layer parameters and
     * `Transition.updateTargetValue` writes animation state while doing so - AOSP code, so wrapping
     * the individual write sites is not an option. And a transaction restores the thread before it
     * publishes, so its dispatch lands in the enclosing scope, which for any entered ingress is the
     * read-only frame view.
     *
     * So: [DataSource.Snapshot.enter] for the view a foreign source needs, and
     * [DataSource.Snapshot.withTransaction] for the writes. The transaction also makes those writes
     * ISOLATED - previously they escaped to the global snapshot unattributed, invisible to the frame
     * that caused them.
     *
     * Re-entrant by construction and self-terminating: observer writes publish when this transaction
     * closes, which dispatches again; a round in which nothing is written produces an empty batch,
     * and [deliverInvalidations] returns immediately for an empty change set.
     */
    internal fun dispatchNow(changed: Set<Any>, snapshot: Snapshot) {
        val toNotify = synchronized(domainLock) { observers }
        if (toNotify.isEmpty()) return
        val frame = current
        if (frame == null) {
            // Isolation off, or the domain is closed: stock un-isolated dispatch.
            toNotify.forEach { it(changed, snapshot) }
            return
        }
        frame.enter { frame.withTransaction { toNotify.forEach { it(changed, snapshot) } } }
    }

    internal fun pendingDeliveryCountForTest(): Int =
        synchronized(domainLock) { pendingDelivery.size }

    override val key: CoroutineContext.Key<*>
        get() = Key

    companion object Key : CoroutineContext.Key<SnapshotHolder>
}

// ---------------------------------------------------------------------------------------
// The open-domain registry: which delivery domains are live, and which substrate root
// currently identifies each one (for own-cycle attribution). Guarded by its own lock;
// entries are added at activate, re-keyed at rotate, removed at close.
// ---------------------------------------------------------------------------------------

private val deliveryDomainLock = makeSynchronizedObject()
private val openDeliveryDomains = mutableListOf<SnapshotHolder>()
private val domainRoots = HashMap<SnapshotHolder, Snapshot?>()

private fun registerOpenDeliveryDomain(domain: SnapshotHolder, root: Snapshot?) {
    synchronized(deliveryDomainLock) {
        openDeliveryDomains.add(domain)
        domainRoots[domain] = root
    }
}

private fun rekeyOpenDeliveryDomain(domain: SnapshotHolder, root: Snapshot?) {
    synchronized(deliveryDomainLock) { domainRoots[domain] = root }
}

private fun unregisterOpenDeliveryDomain(domain: SnapshotHolder) {
    synchronized(deliveryDomainLock) {
        openDeliveryDomains.remove(domain)
        domainRoots.remove(domain)
    }
}

/**
 * Partitions the open domains for a delivery attributed to [root]: the committer (the
 * domain whose current unit's substrate root IS the attribution root, if any) and all
 * others. Called by the runtime's delivery function.
 */
internal fun committerAndOtherDomains(
    root: Snapshot?
): Pair<SnapshotHolder?, List<SnapshotHolder>> =
    synchronized(deliveryDomainLock) {
        if (openDeliveryDomains.isEmpty()) return@synchronized null to emptyList()
        var committer: SnapshotHolder? = null
        val others = ArrayList<SnapshotHolder>(openDeliveryDomains.size)
        for (domain in openDeliveryDomains) {
            if (root != null && domainRoots[domain] === root) committer = domain
            else others.add(domain)
        }
        committer to others
    }
