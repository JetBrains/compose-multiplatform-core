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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.ui.geometry.Rect
import java.util.IdentityHashMap

/**
 * Records recomposition activity of every composition of one recomposer. Scope callbacks update
 * counters under a lock; names, locations and bounds are resolved at frame end, on the
 * composition thread, from the slot tables registered through [RecompositionInspectable].
 *
 * A frame runs from one [onFrameStart] to the next, so subcompositions performed during layout
 * belong to the frame that laid out.
 */
@OptIn(ExperimentalComposeRuntimeApi::class, ComposeToolingApi::class)
internal class RecompositionRecorder(
    private val settings: InspectorSettings,
    private val clock: () -> Long = System::nanoTime,
    private val onSnapshot: (InspectorSnapshot) -> Unit,
) : CompositionRegistrationObserver, CompositionObserver {

    private class CompositionRecord(
        val composition: ObservableComposition,
        var handle: CompositionObserverHandle?,
    )

    private class InvalidatorRecord(val value: Any) {
        var count = 0
        var rememberedIn: String? = null
        var rememberedInResolved = false
    }

    private class FrameRecord(val frame: Long) {
        val runs = ArrayList<ScopeRecord>()
    }

    private class ScopeRecord(val id: Int, val scope: RecomposeScope) {
        var parent: ScopeRecord? = null
        var depth = 0
        var name: String? = null
        var location: String? = null
        /** The group lookup is done; `name` may still be null for groups without source info. */
        var resolved = false
        var resolveAttempts = 0
        var group: CompositionGroup? = null

        var runCount = 0
        /** Times of recent runs, one per frame, within [RECENT_WINDOW_NANOS]. */
        val runNanos = ArrayDeque<Long>()
        var lastRunFrame = -1L
        /** Time of the last run. */
        var lastRunNanos = 0L
        var consecutiveFrames = 0
        var lastRunCause = RunCause.Initial
        var parentDrivenCount = 0
        var explicitInvalidateCount = 0
        var invalidatedDuringCompositionCount = 0
        var lastInvalidatedDuringCompositionFrame = -1L

        var pendingCause: RunCause? = null
        var ranThisFrame = false
        var enterNanos = 0L
        var childNanos = 0L
        var lastDurationNanos = 0L
        var totalDurationNanos = 0L

        val invalidators = IdentityHashMap<Any, InvalidatorRecord>()
        var bounds: Rect? = null
        /** Part of the inspector's own UI: tracked for the parent chain, never counted or shown. */
        var excluded = false
    }

    private val lock = Any()
    private val compositions = LinkedHashMap<ObservableComposition, CompositionRecord>()
    private val scopes = IdentityHashMap<RecomposeScope, ScopeRecord>()
    private val stack = ArrayList<ScopeRecord>()
    private val findings = LinkedHashMap<Long, Finding>()
    private var currentFrame: FrameRecord? = null
    private var frameCounter = 0L
    private var composingDepth = 0
    private var nextScopeId = 1
    private var lastPublishNanos = 0L
    private var hasSourceInformation = false

    /** Not recording: events are ignored except scope disposal. */
    var isPaused = false
        private set

    // ---- CompositionRegistrationObserver -------------------------------------------------------

    override fun onCompositionRegistered(composition: ObservableComposition) {
        val record = synchronized(lock) {
            compositions.getOrPut(composition) {
                CompositionRecord(composition, handle = null)
            }
        }
        if (record.handle == null) {
            record.handle = composition.setObserver(this)
        }
    }

    override fun onCompositionUnregistered(composition: ObservableComposition) {
        val record = synchronized(lock) { compositions.remove(composition) } ?: return
        record.handle?.dispose()
        record.handle = null
    }

    /**
     * Marks [scope] and everything first entered below it as the inspector's own UI, never
     * counted. Call during the scope's first composition, before its children run.
     */
    fun excludeScope(scope: RecomposeScope) {
        synchronized(lock) {
            val record = recordFor(scope)
            record.excluded = true
        }
    }

    fun dispose() {
        val records = synchronized(lock) {
            val copy = compositions.values.toList()
            compositions.clear()
            copy
        }
        records.forEach { it.handle?.dispose() }
    }

    // ---- CompositionObserver -------------------------------------------------------------------

    override fun onBeginComposition(composition: ObservableComposition) {
        synchronized(lock) { composingDepth++ }
    }

    override fun onEndComposition(composition: ObservableComposition) {
        synchronized(lock) { if (composingDepth > 0) composingDepth-- }
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        synchronized(lock) {
            if (!isActive) return
            val record = recordFor(scope)
            val parent = stack.lastOrNull()
            if (record.runCount == 0 && parent != null) {
                record.parent = parent
                record.depth = parent.depth + 1
                if (parent.excluded) record.excluded = true
            }
            if (record.excluded) {
                record.runCount++
                stack.add(record)
                return
            }
            val cause = when {
                record.runCount == 0 -> RunCause.Initial
                record.pendingCause != null -> record.pendingCause!!
                else -> RunCause.ParentDriven
            }
            if (cause == RunCause.ParentDriven) record.parentDrivenCount++
            record.lastRunCause = cause
            record.pendingCause = null
            record.runCount++
            record.enterNanos = clock()
            record.lastRunNanos = record.enterNanos
            record.childNanos = 0L
            val frame = currentFrame
            if (frame != null && !record.ranThisFrame) {
                record.ranThisFrame = true
                frame.runs.add(record)
                record.consecutiveFrames =
                    if (record.lastRunFrame == frame.frame - 1) record.consecutiveFrames + 1 else 1
                record.lastRunFrame = frame.frame
                record.runNanos.addLast(record.enterNanos)
                while (record.runNanos.size > MAX_RUN_TIMESTAMPS ||
                    record.runNanos.first() < record.enterNanos - RECENT_WINDOW_NANOS
                ) {
                    record.runNanos.removeFirst()
                }
            }
            stack.add(record)
        }
    }

    override fun onScopeExit(scope: RecomposeScope) {
        synchronized(lock) {
            val record = scopes[scope] ?: return
            val index = stack.lastIndexOf(record)
            if (index < 0) return
            // Unwind anything left above (should not happen; keeps the stack consistent).
            while (stack.size > index + 1) stack.removeAt(stack.size - 1)
            stack.removeAt(index)
            if (record.excluded) return
            val elapsed = clock() - record.enterNanos
            val self = (elapsed - record.childNanos).coerceAtLeast(0L)
            record.lastDurationNanos = self
            record.totalDurationNanos += self
            stack.lastOrNull()?.let { it.childNanos += elapsed }
        }
    }

    override fun onReadInScope(scope: RecomposeScope, value: Any) {
        // Reads are not tracked: too costly per state read.
    }

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
        synchronized(lock) {
            if (!isActive) return
            val record = recordFor(scope)
            if (record.excluded) return
            if (value == null) {
                record.pendingCause = RunCause.ExplicitInvalidate
                record.explicitInvalidateCount++
            } else {
                record.pendingCause = RunCause.OwnState
                val invalidator = record.invalidators.getOrPut(value) {
                    if (record.invalidators.size >= MAX_INVALIDATORS) {
                        record.invalidators.remove(record.invalidators.keys.first())
                    }
                    InvalidatorRecord(value)
                }
                invalidator.count++
            }
            if (composingDepth > 0) {
                record.invalidatedDuringCompositionCount++
                currentFrame?.let { record.lastInvalidatedDuringCompositionFrame = it.frame }
            }
        }
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        synchronized(lock) {
            val record = scopes.remove(scope) ?: return
            // Children keep it as their parent; drop what points into the slot table.
            record.group = null
            record.bounds = null
            record.resolved = true
            stack.remove(record)
            // So detect() does not re-add a finding for it at frame end.
            currentFrame?.runs?.remove(record)
            findings.keys.removeAll { key -> (key and 0xFFFFFFFFL).toInt() == record.id }
        }
    }

    // ---- Frames ----------------------------------------------------------------------------------

    fun onFrameStart() {
        synchronized(lock) {
            closeCurrentFrame()
            if (!isActive) return
            frameCounter++
            currentFrame = FrameRecord(frameCounter)
        }
    }

    fun onFrameEnd() {
        val snapshot: InspectorSnapshot? = synchronized(lock) {
            val frame = currentFrame ?: return
            val loopFound = detect(frame) != null
            val now = clock()
            val due = now - lastPublishNanos >= settings.publishIntervalNanos
            if (due || loopFound) {
                lastPublishNanos = now
                buildSnapshot()
            } else {
                null
            }
        }
        if (snapshot != null) onSnapshot(snapshot)
    }

    private fun closeCurrentFrame() {
        val frame = currentFrame ?: return
        frame.runs.forEach { it.ranThisFrame = false }
        currentFrame = null
    }

    private val isActive: Boolean
        get() = !isPaused

    fun pause() = synchronized(lock) { isPaused = true }

    fun resume() = synchronized(lock) { isPaused = false }

    fun clear() {
        synchronized(lock) {
            findings.clear()
            scopes.values.forEach { record ->
                record.runCount = 0
                record.runNanos.clear()
                record.consecutiveFrames = 0
                record.parentDrivenCount = 0
                record.explicitInvalidateCount = 0
                record.invalidatedDuringCompositionCount = 0
                record.lastInvalidatedDuringCompositionFrame = -1L
                record.totalDurationNanos = 0L
                record.lastDurationNanos = 0L
                record.invalidators.clear()
            }
        }
    }

    /** Builds a snapshot right away, outside the frame cadence (e.g. after [clear]). */
    fun snapshotNow(): InspectorSnapshot = synchronized(lock) { buildSnapshot() }

    // ---- Analysis --------------------------------------------------------------------------------

    /**
     * Updates findings for [frame]; returns the most severe loop kind confirmed this frame, or
     * `null` when nothing is looping.
     */
    private fun detect(frame: FrameRecord): FindingKind? {
        var loop: FindingKind? = null
        val threshold = settings.loopFrameThreshold.coerceAtLeast(2)
        for (record in frame.runs) {
            if (record.consecutiveFrames >= threshold) {
                val recentBackwardsWrite =
                    record.lastInvalidatedDuringCompositionFrame >= frame.frame - threshold
                val kind = if (recentBackwardsWrite) FindingKind.RecomposeLoop else FindingKind.RecomposesEveryFrame
                if (kind == FindingKind.RecomposeLoop || loop == null) loop = kind
                putFinding(
                    kind,
                    record,
                    frame,
                    message = if (kind == FindingKind.RecomposeLoop) {
                        "Recomposed in ${record.consecutiveFrames} consecutive frames and was " +
                            "invalidated from inside composition: it writes state it reads. " +
                            "Invalidated by ${describeInvalidators(record)}."
                    } else {
                        "Recomposed in ${record.consecutiveFrames} consecutive frames. " +
                            "Invalidated by ${describeInvalidators(record)}."
                    },
                )
                // A loop finding supersedes the every-frame one for the same scope.
                if (kind == FindingKind.RecomposeLoop) {
                    findings.remove(findingKey(FindingKind.RecomposesEveryFrame, record.id))
                }
            }
            if (record.runCount >= PARENT_DRIVEN_MIN_RUNS &&
                record.parentDrivenCount * 100 / record.runCount >= PARENT_DRIVEN_MIN_PERCENT
            ) {
                putFinding(
                    FindingKind.ParentDriven,
                    record,
                    frame,
                    message = "${record.parentDrivenCount} of ${record.runCount} runs were caused by " +
                        "the parent recomposing, not by state this scope reads. Check its parameters " +
                        "for unstable values (lambdas, new collections).",
                )
            }
            if (record.explicitInvalidateCount >= EXPLICIT_MIN && record.invalidators.isEmpty()) {
                putFinding(
                    FindingKind.ExplicitInvalidations,
                    record,
                    frame,
                    message = "Invalidated ${record.explicitInvalidateCount} times through " +
                        "RecomposeScope.invalidate() without a state object.",
                )
            }
        }
        return loop
    }

    private fun findingKey(kind: FindingKind, scopeId: Int): Long =
        (kind.ordinal.toLong() shl 32) or (scopeId.toLong() and 0xFFFFFFFFL)

    private fun putFinding(kind: FindingKind, record: ScopeRecord, frame: FrameRecord, message: String) {
        if (tables.isEmpty()) tables = RecompositionInspectionTables.snapshot()
        ensureResolved(record)
        findings[findingKey(kind, record.id)] = Finding(
            kind = kind,
            scopeId = record.id,
            scopeName = displayName(record),
            location = record.location,
            message = message,
            frame = frame.frame,
        )
    }

    private fun describeInvalidators(record: ScopeRecord): String {
        if (record.invalidators.isEmpty()) {
            return if (record.explicitInvalidateCount > 0) "explicit invalidate() calls" else "its parent"
        }
        return record.invalidators.values
            .sortedByDescending { it.count }
            .take(3)
            .joinToString { "${describeValue(it.value)} (x${it.count})" }
    }

    // ---- Snapshot --------------------------------------------------------------------------------

    /** Slot tables registered through [RecompositionInspectable]; refreshed once per snapshot. */
    private var tables: List<CompositionData> = emptyList()

    private fun buildSnapshot(): InspectorSnapshot {
        val frame = frameCounter
        val now = clock()
        tables = RecompositionInspectionTables.snapshot()
        val scopeSnapshots = ArrayList<ScopeSnapshot>(scopes.size)
        for (record in scopes.values) {
            if (record.excluded) continue
            ensureResolved(record)
            val sinceLastRun = now - record.lastRunNanos
            if (sinceLastRun <= RECENT_WINDOW_NANOS) {
                record.bounds = ScopeResolver.bounds(record.group)
            } else if (record.bounds != null && sinceLastRun > RECENT_WINDOW_NANOS * 2) {
                record.bounds = null
            }
            scopeSnapshots.add(snapshotOf(record, frame, now))
        }
        return InspectorSnapshot(
            frameCount = frame,
            timeNanos = now,
            isRecording = isActive,
            hasSourceInformation = hasSourceInformation,
            scopes = scopeSnapshots,
            findings = findings.values.sortedWith(
                compareByDescending<Finding> { it.kind.severity }.thenByDescending { it.frame }
            ),
        )
    }

    private fun snapshotOf(record: ScopeRecord, frame: Long, now: Long): ScopeSnapshot {
        val recentRuns = record.runNanos.count { it >= now - RECENT_WINDOW_NANOS }
        val invalidators = record.invalidators.values
            .sortedByDescending { it.count }
            .map { inv ->
                if (!inv.rememberedInResolved) {
                    inv.rememberedInResolved = true
                    inv.rememberedIn = findRememberedIn(record, inv.value)
                }
                InvalidatorSnapshot(
                    description = describeValue(inv.value),
                    count = inv.count,
                    rememberedIn = inv.rememberedIn,
                )
            }
        return ScopeSnapshot(
            id = record.id,
            name = displayName(record),
            location = record.location,
            parentId = record.parent?.id,
            depth = record.depth,
            runCount = record.runCount,
            recentRunCount = recentRuns,
            lastRunFrame = record.lastRunFrame,
            lastRunNanos = record.lastRunNanos,
            lastRunCause = record.lastRunCause,
            parentDrivenCount = record.parentDrivenCount,
            explicitInvalidateCount = record.explicitInvalidateCount,
            invalidatedDuringCompositionCount = record.invalidatedDuringCompositionCount,
            lastDurationNanos = record.lastDurationNanos,
            totalDurationNanos = record.totalDurationNanos,
            consecutiveFrames = if (record.lastRunFrame >= frame - 1) record.consecutiveFrames else 0,
            invalidators = invalidators,
            bounds = record.bounds,
        )
    }

    private fun findRememberedIn(record: ScopeRecord, value: Any): String? {
        var current: ScopeRecord? = record
        var hops = 0
        while (current != null && hops < 6) {
            ensureResolved(current)
            if (ScopeResolver.remembers(current.group, value)) {
                return displayName(current) + (current.location?.let { " ($it)" } ?: "")
            }
            current = current.parent
            hops++
        }
        return null
    }

    private fun ensureResolved(record: ScopeRecord) {
        if (record.resolved) return
        // No table registered yet: retry later without using up an attempt.
        if (tables.isEmpty()) return
        val group = ScopeResolver.group(tables, record.scope)
        if (group == null) {
            // Not in any registered table: give up after a few attempts.
            if (++record.resolveAttempts >= MAX_RESOLVE_ATTEMPTS) record.resolved = true
            return
        }
        record.group = group
        val resolved = ScopeResolver.resolve(group)
        if (resolved.hasSourceInformation) hasSourceInformation = true
        record.name = resolved.name
        record.location = resolved.location
        record.resolved = true
    }

    private fun displayName(record: ScopeRecord): String {
        record.name?.let { return it }
        val parent = record.parent
        return if (parent != null) "λ in ${displayName(parent)}" else "scope#${record.id}"
    }

    private fun recordFor(scope: RecomposeScope): ScopeRecord =
        scopes.getOrPut(scope) { ScopeRecord(nextScopeId++, scope) }

    private fun describeValue(value: Any): String {
        val text = try {
            value.toString()
        } catch (_: Exception) {
            value::class.java.simpleName
        }
        val simple = value::class.java.simpleName.ifEmpty { value::class.java.name }
        val shown = if (text.length > MAX_VALUE_TEXT) text.take(MAX_VALUE_TEXT) + "…" else text
        return if (shown.startsWith(simple) || shown.contains('@')) shown else "$simple($shown)"
    }

    private companion object {
        /** Window of the "recent runs" count and of bounds resolution: one second. */
        const val RECENT_WINDOW_NANOS = 1_000_000_000L
        const val MAX_RUN_TIMESTAMPS = 1024
        const val MAX_INVALIDATORS = 16
        const val MAX_VALUE_TEXT = 80
        const val PARENT_DRIVEN_MIN_RUNS = 20
        const val PARENT_DRIVEN_MIN_PERCENT = 80
        const val EXPLICIT_MIN = 10
        const val MAX_RESOLVE_ATTEMPTS = 3
    }
}
