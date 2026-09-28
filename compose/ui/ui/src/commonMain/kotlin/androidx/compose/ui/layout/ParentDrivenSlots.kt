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

package androidx.compose.ui.layout

import androidx.compose.runtime.Composition
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ParentDrivenHost
import androidx.compose.runtime.ParentDrivenHosting
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNode.LayoutState
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.canAffectParentInLookahead
import androidx.compose.ui.node.canAffectPlacedParent
import androidx.compose.ui.node.measuredByPlacedParent
import androidx.compose.ui.node.requireOwner

/**
 * The slot compositions of one [SubcomposeLayout] that its layout pass has to compose, and the
 * compositions that wait for them.
 *
 * A slot composition takes its content from its host's measure, so it must not compose ahead of a
 * composition enclosing it that has not composed yet in this frame: it would pair the values that
 * composition captured earlier with fresh reads. So when what a slot captured can be stale, the
 * recomposer does not recompose it, nor a composition without a host nested in it: it hands the
 * slot to its host, whose measure re-runs it if a composition or host above gives it new content,
 * after the enclosing composition has recomposed and applied, and it hands a composition without
 * a host to the nearest enclosing slot, which recomposes it once it is current itself. Node order
 * then orders plain nesting. The pending state below orders the rest: a slot whose host measures
 * before the host of the slot enclosing it, as an overlay's does, is held until that slot reports.
 * When nothing a slot captured can be stale, the slot declines, and the recomposer recomposes it
 * as stock Compose does.
 *
 * A slot stays pending until it reports, is disposed, or its owner completes a full layout pass.
 * A full pass measures every host that is scheduled, so a slot still pending after one was not
 * re-run by its host, and nothing fresher will reach it in this frame: it expires, which composes
 * it on its own at the end of the pass, as the recomposer would have, and then releases what
 * waits for it. For a slot handed over while its host was not scheduled, and whose content
 * nothing above changed, that is the usual delivery: its host does not measure for it. A
 * remeasure of a single node, which can run between the frame's recompose and its layout,
 * expires nothing, so it cannot let a waiter hosted shallower than the slot it waits for, as an
 * overlay's is, compose ahead of it. An owner that cannot tell the two kinds of layout apart
 * expires at either, which keeps every delivery but not always the order.
 *
 * A slot that reports or expires inside or at the end of its host's layout pass composes nothing
 * inside the pass, because that would compose inside the host's measure. A waiter whose host will
 * measure later in the pass is refreshed at once; the rest are released when the pass ends. The
 * draw's trailing layout pass then delivers them in the same frame. That holds for one deferral:
 * a chain that crosses a second one, in the trailing pass, costs a frame per extra deferral.
 *
 * All of it runs on the thread that recomposes and lays out, which is the UI thread, so none of
 * it is synchronized.
 */
@OptIn(InternalComposeApi::class)
internal class ParentDrivenSlots(private val root: LayoutNode) {
    // The slots marked pending since the owner's last full layout pass.
    private val expiring = mutableVectorOf<ParentDrivenSlot>()
    private var expiryRegistered = false

    // The waiters released at the end of the host's current layout pass, in release order, and
    // the slots that complete after them.
    private val releasing = mutableVectorOf<ParentDrivenHosting>()
    private var releaseRegistered = false

    /**
     * Expires the slots still pending when the owner completes a full layout pass, see
     * [ParentDrivenSlot.expire].
     *
     * The layout delegate takes a listener off its list before it calls it, and calls one
     * registered during its dispatch in that same dispatch. So the listener counts as
     * unregistered from the moment it runs: a slot marked pending while it runs, or later in the
     * dispatch of full-pass listeners, registers it again and expires at that call. The delegate
     * calls the other layout-completed listeners after these, so a slot marked from one of them
     * expires at the next full pass, and what an expiry leaves for the end of the pass is released
     * after every expiry of this pass. The expiry itself marks nothing pending that was not.
     *
     * An expiry composes nothing: it only decides, and leaves the compose to the end of the pass.
     * A compose applies, and an effect it applies can lay out a single node, which calls the
     * end-of-pass listeners from inside this dispatch. So every expiry of the pass decides before
     * anything composes, and what one expiry leaves pending, such as a slot its host stopped
     * using, still holds the slots nested in it when they expire later in the dispatch.
     *
     * A slot whose release waits for the end of this pass is not expired: that release runs
     * after this, refreshes the slot's host, and keeps the slot pending until the next full pass.
     *
     * It takes each slot off the list before expiring it, as the delegate takes its listeners,
     * so a call nested in this one, or a slot added while it runs, cannot make it walk a list
     * that another call is changing.
     */
    private val expireOnFullLayoutCompleted =
        object : Owner.OnLayoutCompletedListener {
            override fun onLayoutComplete() {
                expiryRegistered = false
                while (expiring.isNotEmpty()) {
                    val slot = expiring.removeAt(0)
                    if (slot.pending) slot.expire()
                }
            }
        }

    /**
     * Releases, at the end of the host's layout pass, the waiters that a report inside the pass
     * left for it, in the order the reports left them.
     *
     * A release that stops, because the recomposer is in its error state or a waiter threw, drops
     * the rest, as a release inside a report does: recovery recomposes everything. A slot among
     * them must not stay held by its host for good, so it goes back to plain pending, and expires
     * at the next full pass unless it reports first.
     *
     * A released composition can lay out again, for example from a `SideEffect` that forces a
     * remeasure, and a report in that layout can leave more waiters here and call this listener
     * from inside itself. So it takes each waiter off the list before releasing it, as the
     * delegate takes its listeners: the nested call releases what is left, in order, including
     * what was added, and no waiter is released twice.
     */
    private val releaseOnLayoutCompleted =
        object : Owner.OnLayoutCompletedListener {
            override fun onLayoutComplete() {
                releaseRegistered = false
                try {
                    while (releasing.isNotEmpty()) {
                        val waiter = releasing.removeAt(0)
                        val slot = waiter.host as? ParentDrivenSlot
                        val released =
                            if (slot != null) slot.releaseAfterPass() else waiter.recomposeNow()
                        if (!released) break
                    }
                } finally {
                    while (releasing.isNotEmpty()) {
                        (releasing.removeAt(0).host as? ParentDrivenSlot)?.dropDeferredRelease()
                    }
                }
            }
        }

    /**
     * Whether the host's measure will run, so it re-runs a slot handed to it. This is the
     * condition under which the layout delegate schedules a node that requested a remeasure or a
     * lookahead remeasure, built from the delegate's own checks. A host that is measure-pending
     * but not scheduled, because it is detached, deactivated, or placed neither in the lookahead
     * pass nor in the main one and not measured by a parent that is, would never re-run the slot.
     * A host that is measuring right now is not pending any more, so it does not count either.
     */
    val isScheduled: Boolean
        get() =
            root.owner != null &&
                !root.isDeactivated &&
                ((root.lookaheadMeasurePending &&
                    (root.isPlacedInLookahead == true || root.canAffectParentInLookahead)) ||
                    (root.measurePending && (root.isPlaced || root.canAffectPlacedParent)))

    /**
     * Whether a remeasure request would schedule the host's measure: it is attached, not
     * deactivated, and placed, in the lookahead pass or the main one, or measured by a parent that
     * is. A host that cannot be scheduled would not re-run a slot handed to it.
     */
    val canBeScheduled: Boolean
        get() =
            root.owner != null &&
                !root.isDeactivated &&
                (root.isPlaced ||
                    root.isPlacedInLookahead == true ||
                    root.measuredByPlacedParent)

    /**
     * Marks [slot] pending until it reports, is disposed, or the owner completes a full layout
     * pass after this call, whichever call made it pending, including a mark made from inside a
     * layout-completed listener.
     */
    fun markPending(slot: ParentDrivenSlot) {
        if (slot.pending) return
        slot.pending = true
        expireAtFullLayout(slot)
    }

    /**
     * Expires [slot], if it is still pending then, when the owner's next full layout pass
     * completes. A slot added twice expires once.
     */
    fun expireAtFullLayout(slot: ParentDrivenSlot) {
        expiring += slot
        if (expiryRegistered) return
        // A host without an owner registers once a slot is marked pending with one. Its slots
        // meanwhile expire with the slots they wait for.
        val owner = root.owner ?: return
        owner.registerOnFullLayoutCompletedListener(expireOnFullLayoutCompleted)
        expiryRegistered = true
    }

    /**
     * Releases [waiter] when the host's current layout pass ends, after the waiters left for that
     * point before it. The owner calls its layout-completed listeners after every layout pass, and
     * a frame's draw runs one more pass after its layout, which re-runs the slots of hosts that
     * this release refreshes. So the waiter is still delivered in the same frame, unless its own
     * release in that trailing pass has to wait for the end of the pass again.
     */
    fun releaseAtEndOfPass(waiter: ParentDrivenHosting) {
        releasing += waiter
        if (releaseRegistered) return
        root.requireOwner().registerOnLayoutCompletedListener(releaseOnLayoutCompleted)
        releaseRegistered = true
    }

    /**
     * Whether the host is outside a layout pass of its own. Composing one of its slots then does
     * not run inside its measure.
     */
    val isIdle: Boolean
        get() = root.layoutState == LayoutState.Idle

    /**
     * Requests the host's measure, which re-runs a held slot. A slot in a lookahead scope is
     * composed from the lookahead measure, so it needs a lookahead remeasure there.
     */
    fun requestRefresh() {
        if (root.lookaheadRoot != null) {
            if (!root.lookaheadMeasurePending) root.requestLookaheadRemeasure()
        } else {
            if (!root.measurePending) root.requestRemeasure()
        }
    }
}

/**
 * One slot composition's side of the ordering: whether it is due to compose in its host's layout
 * pass and has not yet, and the compositions to release once it is current.
 *
 * @param usedByHost whether the host's last measure used this slot, see [isUsedByHost].
 */
@OptIn(InternalComposeApi::class)
internal class ParentDrivenSlot(
    private val slots: ParentDrivenSlots,
    private val hosting: ParentDrivenHosting,
    private val usedByHost: () -> Boolean,
) : ParentDrivenHost {
    /**
     * Whether the host's last measure used this slot. A precomposed slot, or one kept for reuse,
     * is not re-run by the host's next measure: the recomposer recomposes it, and a release
     * composes it on its own rather than asking its host to measure.
     */
    val isUsedByHost: Boolean
        get() = usedByHost()

    /**
     * Due to compose in the host's layout pass, or waited for there, and not current since: handed
     * over by the recomposer, or held at measure time. A report, an expiry and a dispose clear it.
     */
    var pending = false

    /**
     * Released by a report inside a layout pass, which left this slot's release to the end of
     * that pass because a waiter released before it had to be. Until then its host keeps its old
     * content and it does not report, so neither it nor what is nested in it composes ahead of
     * that waiter; it stays pending, so what is nested in it waits for it.
     */
    var releaseDeferred = false
        private set

    /**
     * Reported current inside a layout pass, and released a waiter without a host that has to
     * wait for the end of that pass. Until then this slot stays pending, although it composed: a
     * composition nested in that waiter, through a host the release does not know about, can
     * only find the waiter through this slot, because a composition without a host has no slot.
     * It then waits for the end of the pass with the waiter instead of composing ahead of it.
     */
    private var completionDeferred = false

    /**
     * Expired in a full layout pass whose measure stopped using it. It stays pending until the end
     * of that pass, so a slot nested in its content that expires later in the same dispatch waits
     * behind it rather than composing against content its host has just dropped, and then hands
     * back everything that waits for it, see [abandonWaiters].
     */
    private var abandonDeferred = false

    /**
     * Expired in a full layout pass with nothing that re-runs it: it composes on its own at the
     * end of that pass, in expiry order, and stays pending until then, so a slot nested in it
     * that expires later in the same dispatch waits behind it.
     */
    private var composeDeferred = false

    /**
     * Left pending for its host's measure, because the host was scheduled when the slot expired,
     * or when its compose at the end of the pass came. A full pass measures every scheduled host
     * that will measure, so a host still scheduled at the next expiry will not re-run the slot:
     * the slot then composes on its own instead of waiting again, which would force another full
     * pass, and so on for as long as the host stays scheduled.
     */
    private var leftForScheduledHost = false

    // The compositions to release once this one is current, in the order they were added: the
    // recomposer hands them over in ascending depth, and holds at measure time add later ones at
    // the end.
    private var waiters: MutableList<ParentDrivenHosting>? = null

    /** Whether a report has anything to do. It is false for almost every slot on every measure. */
    val isReportDue: Boolean
        get() = pending || waiters != null

    // Takes the slot if its host lays it out and what it captured can be stale: a composition
    // enclosing it changed earlier in this recompose block, and its apply can give the host new
    // content, or a layout pass that is due can re-supply it, see [isRefreshDue]. Otherwise the
    // recomposer recomposes it now, as stock Compose does, and its host does not measure. A taken
    // slot does not request its host's measure. If the apply, or the measure of a host above,
    // gives the host new content, that schedules the host, whose measure re-runs the slot with
    // it. If nothing does, the slot's content is current, and the owner's full layout pass
    // expires it, which composes it on its own, after everything above it has composed.
    override fun onInvalidated(enclosingRecomposed: Boolean): Boolean {
        if (!isUsedByHost || !slots.canBeScheduled) return false
        if (!enclosingRecomposed && !isRefreshDue()) return false
        markPending()
        return true
    }

    // Takes a composition without a host nested in this slot, to recompose it once this slot is
    // current, under the same condition as [onInvalidated]. Its host's measure is not requested:
    // if something schedules it, this slot composes there and reports; if not, this slot has
    // nothing to compose, and the owner's full layout pass expires it, which releases the waiter.
    // Either way the waiter composes at the end of the pass, after this slot, without a measure of
    // its own.
    override fun onWaiterInvalidated(
        waiter: ParentDrivenHosting,
        enclosingRecomposed: Boolean,
    ): Boolean {
        if (!isUsedByHost || !slots.canBeScheduled) return false
        if (!enclosingRecomposed && !isRefreshDue()) return false
        addWaiter(waiter)
        markPending()
        return true
    }

    /**
     * Whether a layout pass that is due can re-supply this slot's content, or the content of a
     * slot enclosing it: this slot's host, or the host of an enclosing slot, is scheduled to
     * measure, or this slot or an enclosing one is still due to compose in its host's layout pass.
     *
     * A host's measure that reads a state itself, not through a composition, is marked pending by
     * the owner's snapshot observer when the change is applied, and the frame recomposes after
     * that, so a change the frame recomposes for is visible here. A host scheduled only during
     * the layout, because a slot it measured changed its size, is not: nothing before the layout
     * can see that.
     */
    private fun isRefreshDue(): Boolean {
        if (pending || slots.isScheduled) return true
        var enclosing = hosting.enclosingHosted
        while (enclosing != null) {
            val slot = enclosing.host as? ParentDrivenSlot
            if (slot != null && (slot.pending || slot.slots.isScheduled)) return true
            enclosing = enclosing.enclosingHosted
        }
        return false
    }

    override fun onDisposed() {
        pending = false
        releaseDeferred = false
        completionDeferred = false
        abandonDeferred = false
        composeDeferred = false
        leftForScheduledHost = false
        val waiters = takeWaiters() ?: return
        release(waiters, disposing = true)
    }

    /**
     * Holds this slot, right before its host would compose a change into it, while a composition
     * enclosing it is still pending, or while its own release waits for the end of the layout
     * pass. Returns true if it is held: the host then keeps the old content, and the enclosing
     * slot's report, or the release at the end of the pass, refreshes the host. Marking it pending
     * makes a composition nested in it, measured before the enclosing slot reports, wait for this
     * slot rather than compose ahead of it.
     */
    fun holdBehindPendingEnclosing(): Boolean {
        if (releaseDeferred) return true
        val anchor = nearestPendingEnclosing() ?: return false
        waitBehind(anchor)
        return true
    }

    /**
     * Reports this slot current: it composed, or had nothing to compose. Releases its waiters,
     * unless a composition enclosing it is still pending, in which case this slot waits for that
     * one, and its own waiters wait with it. Inside its host's layout pass the release composes
     * nothing, see [releaseInPass]; outside one it releases at once.
     */
    fun reportCurrent() {
        if (!isReportDue || releaseDeferred || completionDeferred || abandonDeferred) return
        val anchor = nearestPendingEnclosing()
        if (anchor != null) {
            waitBehind(anchor)
            return
        }
        pending = false
        val waiters = takeWaiters() ?: return
        if (slots.isIdle) release(waiters, disposing = false) else releaseInPass(waiters)
    }

    /**
     * The owner's full layout pass completed with this slot still pending: the host did not re-run
     * it, so nothing fresher reaches it in this frame. It composes on its own at the end of the
     * pass, as the recomposer would have composed it, which is nothing for a slot that was
     * pending only for its waiters, and then releases them, see [composeAfterPass]. It stays
     * pending until then. A slot whose release waits for the end of the pass is left to that
     * release. A slot still waiting for an enclosing slot that is pending is left to that one,
     * whose report or compose releases it; composing it on its own would compose it ahead of that
     * slot. An enclosing slot that is only waiting for its own compose at the end of the pass
     * composes before this one, so this one queues its compose after it instead. A slot its host
     * stopped using composes nothing, and hands what waits for it back to the recomposer at the
     * end of the pass, so a slot nested in it waits for it. A slot whose host is scheduled is left
     * to that host's next measure, once, see [leftForScheduledHost].
     */
    fun expire() {
        if (
            !pending || releaseDeferred || completionDeferred || abandonDeferred || composeDeferred
        ) {
            return
        }
        val anchor = nearestPendingEnclosing()
        if (anchor != null && !anchor.composeDeferred) {
            waitBehind(anchor)
            return
        }
        if (slots.isScheduled) {
            if (!leftForScheduledHost) {
                // Composing it would pair its old content with fresh reads. Registering for the
                // next full pass from inside this dispatch would be called in this dispatch
                // again, so the end of the pass hands it on, as a release left for then does:
                // the draw's trailing layout pass measures its host.
                leftForScheduledHost = true
                releaseDeferred = true
                slots.releaseAtEndOfPass(hosting)
                return
            }
            println(
                "Compose: a SubcomposeLayout stayed scheduled through a full layout pass that " +
                    "did not measure it; its slot composes without it"
            )
        } else {
            leftForScheduledHost = false
        }
        if (!isUsedByHost) {
            // A slot nested in its content can be pending in another host, whose expiry runs
            // later in this dispatch. The recomposer offered that slot while this one was still
            // used, so it did not wait for this one; it finds this one pending now instead.
            abandonDeferred = true
            slots.releaseAtEndOfPass(hosting)
            return
        }
        composeDeferred = true
        slots.releaseAtEndOfPass(hosting)
    }

    // The end of the full layout pass whose expiry left this slot's compose for then. A compose
    // before this one at the end of the pass can have changed what this slot waits for: an
    // enclosing slot left pending again holds it; a host given new content, and so scheduled, is
    // left to re-run it, unless it already failed to once; a host that stopped using it hands
    // back what waits for it. Otherwise it composes on its own and releases its waiters here,
    // before any compose queued after it, so a composition nested in one of them that is queued
    // there does not compose ahead of it. Returns false if the recomposer is in its error state.
    private fun composeAfterPass(): Boolean {
        composeDeferred = false
        // Reported since, or left for a later point of the pass, which handles it.
        if (!pending || releaseDeferred || completionDeferred || abandonDeferred) return true
        val anchor = nearestPendingEnclosing()
        if (anchor != null) {
            waitBehind(anchor)
            return true
        }
        if (slots.isScheduled && !leftForScheduledHost) {
            leftForScheduledHost = true
            slots.expireAtFullLayout(this)
            return true
        }
        pending = false
        if (!isUsedByHost) {
            abandonWaiters()
            return true
        }
        if (!hosting.recomposeNow()) return false
        val waiters = takeWaiters() ?: return true
        return release(waiters, disposing = false)
    }

    // The host stopped using this slot in the pass, and keeps it for reuse or disposes it: its
    // content is gone, and what waits for it was created in that content, or is anchored in it,
    // and is about to be torn down by its owner. Delivering it now would compose it against the
    // state that removed its anchor. So it is only queued for the recomposer's next frame, which
    // finds it disposed, or recomposes it on its own if its owner kept it: the host of this slot
    // no longer takes it. A hosted waiter stops being pending, so it neither expires into a
    // compose nor holds what is nested in it.
    private fun abandonWaiters() {
        val waiters = takeWaiters() ?: return
        for (i in waiters.indices) {
            val waiter = waiters[i]
            if (waiter.isDisposed) continue
            (waiter.host as? ParentDrivenSlot)?.pending = false
            waiter.recomposeLater()
        }
    }

    /**
     * The end of the layout pass in which a report left this slot's release for later. Refreshes
     * its host as a release inside a report does, see [refresh], and keeps it pending until the
     * next full pass if the refresh left it pending. For a slot whose completion was left for
     * later, the waiters it released have been released by now, so it stops being pending and
     * releases what waited for it since. For a slot whose expiry left its compose for later, see
     * [composeAfterPass]. Returns false if the recomposer is in its error state.
     */
    fun releaseAfterPass(): Boolean {
        if (composeDeferred) return composeAfterPass()
        if (abandonDeferred) {
            abandonDeferred = false
            pending = false
            abandonWaiters()
            return true
        }
        if (completionDeferred) {
            completionDeferred = false
            pending = false
            val waiters = takeWaiters() ?: return true
            return release(waiters, disposing = false)
        }
        if (!releaseDeferred) return true
        releaseDeferred = false
        if (hosting.isDisposed) return true
        val released = refresh(disposing = false)
        if (pending) slots.expireAtFullLayout(this)
        return released
    }

    /**
     * A release that stopped before it reached this slot: it is held by nothing but [pending]. A
     * slot whose completion was left for later composed, so it is not pending any more either.
     */
    fun dropDeferredRelease() {
        if (composeDeferred) {
            // It expires again at the next full pass, where the recomposer's recovery has run.
            composeDeferred = false
            if (pending) slots.expireAtFullLayout(this)
            return
        }
        if (abandonDeferred) {
            // Hands back without composing, so it is safe on the error path too.
            abandonDeferred = false
            pending = false
            abandonWaiters()
            return
        }
        if (completionDeferred) {
            completionDeferred = false
            pending = false
            return
        }
        if (!releaseDeferred) return
        releaseDeferred = false
        if (pending) slots.expireAtFullLayout(this)
    }

    private fun markPending() {
        // A new wait for the host starts afresh.
        if (!pending) leftForScheduledHost = false
        slots.markPending(this)
    }

    // Waits for [anchor], which has not composed yet in this frame. If the anchor composed and
    // only its completion waits for the end of the layout pass, its waiters are no longer
    // collected, so this slot's own release waits for that point, after the anchor's.
    private fun waitBehind(anchor: ParentDrivenSlot) {
        markPending()
        if (!anchor.completionDeferred) {
            anchor.addWaiter(hosting)
        } else if (!releaseDeferred) {
            releaseDeferred = true
            anchor.slots.releaseAtEndOfPass(hosting)
        }
    }

    // Requests the host's measure, which re-runs this slot and delivers its held change. A host
    // that will not measure would leave the change undelivered. One outside a layout pass, such
    // as one that is not placed, will not re-run the slot at all, so the slot recomposes here,
    // standalone as the recomposer recomposes a slot of an unscheduled host, and then reports,
    // which releases what waits for it. One that is measuring or laying out right now swallows or
    // postpones the request and may have passed this slot already; the slot must not compose
    // inside that host's measure, so it goes back to the recomposer's next frame, which hands it
    // to its host again, or recomposes it if the host will not lay it out. A release at the end
    // of a pass meets such a host only if one owner's layout ran inside another's, which no
    // shipped scene does, so that branch is defensive there. A dispose must not compose, so it
    // goes back to the recomposer too.
    //
    // A host that does not use this slot, because it is precomposed or kept for reuse, would
    // measure without re-running it. So the slot is not refreshed: it recomposes on its own, as
    // the recomposer recomposes a slot its host does not use, and its host does not measure. A
    // slot its host took and has stopped using since is still pending; it is left to its expiry,
    // which hands back what waits for it rather than composing against content being dropped.
    // Returns false if the recomposer is in its error state.
    private fun refresh(disposing: Boolean): Boolean {
        if (isUsedByHost) {
            slots.requestRefresh()
            if (slots.isScheduled) return true
        } else if (pending) {
            slots.expireAtFullLayout(this)
            return true
        }
        if (disposing || !slots.isIdle) {
            hosting.recomposeLater()
            return true
        }
        if (!hosting.recomposeNow()) return false
        reportCurrent()
        return true
    }

    // The nearest enclosing slot that has not composed yet. It follows the composition chain, not
    // the node tree, so an overlay's slot finds its anchor's slot although the overlay's host is
    // shallower, and a slot in a popup finds the slot the popup is anchored in, in another owner.
    private fun nearestPendingEnclosing(): ParentDrivenSlot? {
        var enclosing = hosting.enclosingHosted
        while (enclosing != null) {
            val slot = enclosing.host as? ParentDrivenSlot
            if (slot != null && slot.pending) return slot
            enclosing = enclosing.enclosingHosted
        }
        return null
    }

    // Internal for a test that drives a slot without an attached host.
    internal fun addWaiter(waiter: ParentDrivenHosting) {
        val waiters = waiters ?: ArrayList<ParentDrivenHosting>(2).also { waiters = it }
        if (waiter !in waiters) waiters += waiter
    }

    private fun takeWaiters(): List<ParentDrivenHosting>? {
        val waiters = waiters
        this.waiters = null
        return waiters
    }

    // The release of a report outside the host's layout pass, such as a paused precomposition
    // applied out of frame, and of a dispose. Releases in list order, so a composition without a
    // host recomposes before the hosted ones nested in it are refreshed. A hosted waiter's host
    // re-runs it in its next measure, at any node depth, and it then releases its own waiters;
    // for a host that will not, see [refresh]. A waiter in two lists is refreshed twice and
    // reports once. A dispose must not compose, so a waiter without a host falls back to the next
    // frame instead.
    // Returns false if it stopped because the recomposer is in its error state.
    private fun release(waiters: List<ParentDrivenHosting>, disposing: Boolean): Boolean {
        for (i in waiters.indices) {
            val waiter = waiters[i]
            if (waiter.isDisposed) continue
            val slot = waiter.host as? ParentDrivenSlot
            when {
                // A false from either branch that composes means the recomposer is in its error
                // state, and its recovery recomposes everything. The hosted waiters not refreshed
                // stay pending until their layout expires them.
                slot != null -> {
                    if (!slot.refresh(disposing)) return false
                    // One left pending waits for its host's measure, and expires at the next full
                    // pass if that does not re-run it.
                    if (!disposing && slot.pending) slot.slots.expireAtFullLayout(slot)
                }
                disposing -> waiter.recomposeLater()
                !waiter.recomposeNow() -> return false
            }
        }
        return true
    }

    // The release of a report inside the host's layout pass, which must not compose: composing
    // here would compose inside the host's measure. A hosted waiter whose host is scheduled after
    // the refresh request is re-run by it later in this pass. Everything else waits for the end of
    // the pass, in list order: a waiter without a host, and a hosted one whose host will not
    // measure in this pass. So does every waiter after the first that waits, because a later one
    // can be nested in it and must not compose ahead of it; a refreshed one would only be held
    // again, by the pending state below, at the cost of a measure. A waiter hosted by the same
    // host as this slot always waits, because a host that is measuring swallows the refresh
    // request; its host is then measured once more, in the draw's trailing layout pass.
    //
    // A composition nested in a waiter without a host can also be one this list does not hold,
    // such as a slot handed to its own host by the recomposer, whose host measures later in this
    // pass. It finds what it waits for only through this slot, so this slot stays pending until
    // its completion, queued after the waiters, at the end of the pass.
    //
    private fun releaseInPass(waiters: List<ParentDrivenHosting>) {
        var deferring = false
        var deferredWithoutHost = false
        for (i in waiters.indices) {
            val waiter = waiters[i]
            if (waiter.isDisposed) continue
            val slot = waiter.host as? ParentDrivenSlot
            // A waiter whose host does not use it is not re-run by that host's measure, so it
            // waits for the end of the pass like a waiter without a host.
            if (!deferring && slot != null && slot.isUsedByHost) {
                slot.slots.requestRefresh()
                if (slot.slots.isScheduled) continue
            }
            deferring = true
            if (slot != null) {
                // A waiter in two lists is left for later once, and one already left for the end
                // of the pass is handled there.
                if (
                    slot.releaseDeferred ||
                        slot.completionDeferred ||
                        slot.abandonDeferred ||
                        slot.composeDeferred
                ) {
                    continue
                }
                slot.releaseDeferred = true
            } else {
                deferredWithoutHost = true
            }
            slots.releaseAtEndOfPass(waiter)
        }
        if (deferredWithoutHost) {
            pending = true
            completionDeferred = true
            slots.releaseAtEndOfPass(hosting)
        }
    }
}

@OptIn(InternalComposeApi::class)
private val ParentDrivenHosting.isDisposed: Boolean
    get() = (this as? Composition)?.isDisposed == true
