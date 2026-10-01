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

import androidx.compose.ui.geometry.Rect

/** Why a scope ran in a given frame. */
enum class RunCause {
    /** First composition of the scope. */
    Initial,
    /** A state object the scope read (or a memo/cell feeding it) changed. */
    OwnState,
    /** `RecomposeScope.invalidate()` was called with no state object attached. */
    ExplicitInvalidate,
    /**
     * The scope was not invalidated itself. Its parent re-ran and the scope could not be skipped,
     * usually because a parameter compared unequal (unstable lambda, new list instance, ...).
     */
    ParentDriven,
}

/** One state object (or memo slot) that invalidated a scope, and how often. */
class InvalidatorSnapshot(
    val description: String,
    val count: Int,
    /** Scope whose slots hold the invalidator, when it was `remember`ed. */
    val rememberedIn: String?,
)

/** Immutable view of one recompose scope, for display. */
class ScopeSnapshot(
    /** Stable id for the lifetime of the inspector. */
    val id: Int,
    val name: String,
    /** `File.kt:line` when source information is available. */
    val location: String?,
    val parentId: Int?,
    val depth: Int,
    /** Times the scope ran, including the initial composition. */
    val runCount: Int,
    /** Runs in the second before the snapshot was taken. */
    val recentRunCount: Int,
    val lastRunFrame: Long,
    /** `System.nanoTime()` at the start of the last run. */
    val lastRunNanos: Long,
    val lastRunCause: RunCause,
    val parentDrivenCount: Int,
    val explicitInvalidateCount: Int,
    val invalidatedDuringCompositionCount: Int,
    val lastDurationNanos: Long,
    val totalDurationNanos: Long,
    val consecutiveFrames: Int,
    val invalidators: List<InvalidatorSnapshot>,
    /** Window-pixel bounds of the nodes the scope emits, if it emits any that are placed. */
    val bounds: Rect?,
)

/** What the inspector flags; [severity] orders findings, 3 being the worst. */
enum class FindingKind(val severity: Int) {
    /**
     * Recomposed for N consecutive frames and was invalidated from inside a composition pass:
     * composition writes state it reads.
     */
    RecomposeLoop(3),
    /** Recomposed for N consecutive frames without an in-composition write: hot state or effect loop. */
    RecomposesEveryFrame(2),
    /** Mostly runs because its parent ran, not because its own state changed. */
    ParentDriven(1),
    /** Invalidated by `RecomposeScope.invalidate()` calls rather than state. */
    ExplicitInvalidations(1),
}

class Finding(
    val kind: FindingKind,
    val scopeId: Int,
    val scopeName: String,
    val location: String?,
    val message: String,
    /** Frame the finding was last confirmed in. */
    val frame: Long,
)

/** Everything the inspector knows, published once per frame (throttled) as a value. */
class InspectorSnapshot(
    val frameCount: Long,
    /** `System.nanoTime()` when the snapshot was built; the reference for ages shown in the UI. */
    val timeNanos: Long,
    val isRecording: Boolean,
    val hasSourceInformation: Boolean,
    val scopes: List<ScopeSnapshot>,
    val findings: List<Finding>,
) {
    val scopesById: Map<Int, ScopeSnapshot> by lazy { scopes.associateBy { it.id } }

    companion object {
        val Empty = InspectorSnapshot(
            frameCount = 0,
            timeNanos = 0,
            isRecording = false,
            hasSourceInformation = false,
            scopes = emptyList(),
            findings = emptyList(),
        )
    }
}

/** Mutable settings of a [RecompositionInspector]. Changes apply from the next frame. */
class InspectorSettings {
    /** Consecutive frames a scope must run in before it is reported as looping. */
    var loopFrameThreshold: Int = 5

    /** Minimum interval between snapshot publications, in nanoseconds (~30 Hz by default). */
    var publishIntervalNanos: Long = 33_000_000L
}
