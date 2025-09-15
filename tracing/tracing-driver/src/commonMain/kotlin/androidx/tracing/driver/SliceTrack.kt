/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.tracing.driver

import androidx.tracing.driver.PlatformThreadContextElement.Companion.STATE_BEGIN
import androidx.tracing.driver.PlatformThreadContextElement.Companion.STATE_END
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/**
 * Horizontal track of time in a trace which contains slice events (`beginSection` / `endSection`).
 */
// False positive: https://youtrack.jetbrains.com/issue/KTIJ-22326
@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
public abstract class SliceTrack(
    /** The [TraceContext] instance. */
    context: TraceContext,
    /**
     * The uuid for the track descriptor.
     *
     * This ID must be unique within all [Track]s in a given trace produced by [TraceDriver] - it is
     * used to connect recorded trace events to the containing track.
     */
    uuid: Long,
) : Track(context = context, uuid = uuid) {
    @PublishedApi internal val packetLock: Any = Any()

    /**
     * Writes a trace message indicating that a given section of code has begun.
     *
     * Should be followed by a corresponding call to [endSection] on the same [SliceTrack]. If a
     * corresponding [endSection] is missing, the section will be present in the trace, but
     * non-terminating (generally shown as fading out to the left).
     *
     * @param name The name of the code section to appear in the trace.
     * @param metadataBlock The block that can include metadata about to the [TraceEvent].
     */
    @JvmOverloads
    public inline fun beginSection(
        name: String,
        crossinline metadataBlock: (TraceEventScope.() -> Unit) = {},
    ) {
        if (context.isEnabled) {
            synchronized(packetLock) {
                emitTraceEvent { event ->
                    event.setBeginSection(uuid, name)
                    metadataBlock.invoke(TraceEventScope(event))
                }
            }
        }
    }

    /**
     * Writes a trace message indicating that a given section of code has begun.
     *
     * Should be followed by a corresponding call to [endSection] on the same [SliceTrack]. If a
     * corresponding [endSection] is missing, the section will be present in the trace, but
     * non-terminating (generally shown as fading out to the left).
     *
     * @param name The name of the code section to appear in the trace.
     * @param flowIds A list of [Long]s which will connect this trace section to other sections in
     *   the trace, potentially on different Tracks. The start and end of each trace `flow`
     *   (connection) between trace sections must share an ID, so each `Long` must be unique to each
     *   `flow` in the trace.
     * @param metadataBlock The block that can include metadata about to the [TraceEvent].
     */
    @JvmOverloads
    public inline fun beginSection(
        name: String,
        flowIds: List<Long>,
        crossinline metadataBlock: (TraceEventScope.() -> Unit) = {},
    ) {
        if (context.isEnabled) {
            synchronized(packetLock) {
                emitTraceEvent { event ->
                    event.setBeginSectionWithFlows(uuid, name, flowIds)
                    metadataBlock.invoke(TraceEventScope(event))
                }
            }
        }
    }

    /**
     * Writes a trace message indicating that a given section of code has ended.
     *
     * Must be preceded by a corresponding call to [beginSection] on the same [SliceTrack]. Any
     * un-paired calls to [endSection] are ignored when the trace is displayed.
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun endSection() {
        if (context.isEnabled) {
            synchronized(packetLock) { emitTraceEvent { event -> event.setEndSection(uuid) } }
        }
    }

    /**
     * Writes a zero duration section to the [SliceTrack].
     *
     * Similar to calling:
     * ```
     * beginSection(name)
     * endSection()
     * ```
     *
     * Except it is faster to write, and guaranteed zero duration.
     */
    public fun instant(name: String) {
        if (context.isEnabled) {
            synchronized(packetLock) { emitTraceEvent { event -> event.setInstant(uuid, name) } }
        }
    }

    /**
     * Traces the [block] as a named section of code in the trace - this is one of the primary entry
     * points for tracing synchronous blocks of code.
     */
    @JvmOverloads
    public inline fun <T> trace(
        name: String,
        crossinline metadataBlock: (TraceEventScope.() -> Unit) = {},
        crossinline block: () -> T,
    ): T {
        beginSection(name, metadataBlock)
        try {
            return block()
        } finally {
            endSection()
        }
    }

    /** [Track] scoped async trace slices. */
    @JvmOverloads
    public suspend inline fun <T> traceCoroutine(
        name: String,
        flowId: Long = monotonicId(),
        crossinline metadataBlock: (TraceEventScope.() -> Unit) = {},
        crossinline block: suspend () -> T,
    ): T {
        val element = obtainPlatformThreadContextElement()
        // Currently, Perfetto flows cannot fully represent fanouts and fanin's.
        // Therefore we simply propagate a single flowId from the parent to the child
        // and carry that throughout. This way, there is only 1 flow id that is used.
        val flowIds = element?.flowIds ?: listOf(flowId)
        val threadContextElement =
            buildThreadContextElement(
                name = name,
                flowIds = flowIds,
                // This method is called before a coroutine is resumed on a thread that
                // belongs to a dispatcher. This can be called more than once. So avoid creating
                // slices unless we transition to `STATE_END`.
                updateThreadContextBlock = { context ->
                    val element = context[PlatformThreadContextElement.KEY]
                    if (
                        element != null &&
                            element.started.compareAndSet(
                                expected = STATE_END,
                                actual = STATE_BEGIN,
                            )
                    ) {
                        beginSection(element.name, flowIds)
                    }
                },
                // This method is called **after** a coroutine is suspend on the current thread.
                // This method might be called more than once as well. So we want to be idempotent.
                restoreThreadContextBlock = { context ->
                    val element = context[PlatformThreadContextElement.KEY]
                    if (
                        element != null &&
                            element.started.compareAndSet(
                                expected = STATE_BEGIN,
                                actual = STATE_END,
                            )
                    ) {
                        endSection()
                    }
                },
            )
        return withContext(coroutineContext + threadContextElement) {
            beginSection(name = name, flowIds = flowIds, metadataBlock = metadataBlock)
            try {
                block()
            } finally {
                endSection()
            }
        }
    }
}
