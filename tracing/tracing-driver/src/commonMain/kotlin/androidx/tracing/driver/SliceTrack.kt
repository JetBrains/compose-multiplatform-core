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

    // Use a single shared trace event scope to avoid allocations.
    @PublishedApi internal val traceEventScope: TraceEventScope = TraceEventScope()

    /**
     * This gives the ability to control how context propagation works for a
     * [androidx.tracing.driver.SliceTrack].
     *
     * The default implementation does not support context propagation in non-suspending contexts.
     * Alternative implementations can choose to override this method to do something different.
     * Examples include using a `ThreadLocal` like primitive track of [PropagationToken]s across
     * threads.
     */
    @DelicateTracingApi public open fun tokenFromThreadContext(): PropagationToken? = null

    /**
     * This gives the ability to control how context propagation works for a
     * [androidx.tracing.driver.SliceTrack].
     *
     * The default implementation uses a [kotlin.coroutines.CoroutineContext] for tracking
     * [PropagationToken] instances. Alternative implementations can choose to override this method
     * to do something different. Examples include using a `ThreadLocal` like primitive track of
     * [PropagationToken]s across suspending methods.
     */
    @DelicateTracingApi
    public open suspend fun tokenFromCoroutineContext(): PropagationToken {
        // Currently, Perfetto flows cannot fully represent fanouts and fanin's.
        // Therefore we simply propagate a single flowId from the parent to the child
        // and carry that throughout. This way, there is only 1 flow id that is used.
        val contextElement = coroutineContext[PlatformThreadContextElement.KEY]
        return contextElement?.token ?: FlowToken(flowIds = listOf(monotonicId()))
    }

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
    public open fun beginSection(
        name: String,
        metadataBlock: (TraceEventScope.() -> Unit)? = null,
    ) {
        if (context.isEnabled) {
            val token = tokenFromThreadContext()
            when (token) {
                null -> {
                    beginSectionInternal(name = name, metadataBlock = metadataBlock)
                }

                else -> {
                    beginSection(name = name, token = token, metadataBlock = metadataBlock)
                }
            }
        }
    }

    internal fun beginSectionInternal(
        name: String,
        metadataBlock: (TraceEventScope.() -> Unit)? = null,
    ) {
        synchronized(traceEventScope) {
            conditionalEmitTraceEvent { event ->
                traceEventScope.event = event
                event.setBeginSection(uuid, name)
                metadataBlock?.invoke(traceEventScope)
                true
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
     * @param token A [PropagationToken] that can be used for context propagation. The default
     *   implementation uses a list of [Long]s which will connect this trace section to other
     *   sections in the trace, potentially on different Tracks. The start and end of each trace
     *   `flow` (connection) between trace sections must share an ID, so each `Long` must be unique
     *   to each `flow` in the trace.
     * @param metadataBlock The block that can include metadata about to the [TraceEvent].
     */
    @JvmOverloads
    public open fun beginSection(
        name: String,
        token: PropagationToken,
        metadataBlock: (TraceEventScope.() -> Unit)? = null,
    ) {
        if (context.isEnabled) {
            synchronized(traceEventScope) {
                require(token is FlowToken) { "Unexpected PropagationToken $token" }
                conditionalEmitTraceEvent { event ->
                    traceEventScope.event = event
                    event.setBeginSectionWithFlows(uuid, name, flowIds = token.flowIds)
                    metadataBlock?.invoke(traceEventScope)
                    true
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
    public open fun endSection() {
        if (context.isEnabled) {
            synchronized(traceEventScope) {
                conditionalEmitTraceEvent { event ->
                    event.setEndSection(uuid)
                    true
                }
            }
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
            synchronized(traceEventScope) {
                conditionalEmitTraceEvent { event ->
                    event.setInstant(uuid, name)
                    true
                }
            }
        }
    }

    /**
     * Traces the [block] as a named section of code in the trace - this is one of the primary entry
     * points for tracing synchronous blocks of code.
     */
    @JvmOverloads
    public inline fun <T> trace(
        name: String,
        noinline metadataBlock: (TraceEventScope.() -> Unit)? = null,
        crossinline block: () -> T,
    ): T {
        beginSection(name, metadataBlock)
        try {
            return block()
        } finally {
            endSection()
        }
    }

    /** Traces the [block] as a named section of code in the trace with context propagation. */
    @JvmOverloads
    @DelicateTracingApi
    public inline fun <T> tracePropagated(
        name: String,
        element: PropagationToken,
        noinline metadataBlock: (TraceEventScope.() -> Unit)? = null,
        crossinline block: () -> T,
    ): T {
        beginSection(name, element, metadataBlock)
        try {
            return block()
        } finally {
            endSection()
        }
    }

    @JvmOverloads
    public suspend inline fun <T> traceCoroutine(
        name: String,
        noinline metadataBlock: (TraceEventScope.() -> Unit)? = null,
        crossinline block: suspend () -> T,
    ): T {
        val element = tokenFromCoroutineContext()
        return traceCoroutine(
            name = name,
            element = element,
            metadataBlock = metadataBlock,
            block = block,
        )
    }

    @JvmOverloads
    @DelicateTracingApi
    public suspend inline fun <T> traceCoroutine(
        name: String,
        element: PropagationToken,
        noinline metadataBlock: (TraceEventScope.() -> Unit)? = null,
        crossinline block: suspend () -> T,
    ): T {
        return when (element) {
            // Standard context propagation.
            is FlowToken ->
                traceCoroutineInternal(
                    name = name,
                    element = element,
                    metadataBlock = metadataBlock,
                    block = block,
                )

            // Custom context propagation
            // We expect developers to have overridden beginSection and endSection at this point.
            else -> {
                beginSection(name = name, token = element, metadataBlock = metadataBlock)
                try {
                    block()
                } finally {
                    endSection()
                }
            }
        }
    }

    /** [Track] scoped async trace slices with flow ids. */
    @PublishedApi
    internal suspend inline fun <T> traceCoroutineInternal(
        name: String,
        element: FlowToken,
        noinline metadataBlock: (TraceEventScope.() -> Unit)? = null,
        crossinline block: suspend () -> T,
    ): T {
        val threadContextElement =
            buildThreadContextElement(
                name = name,
                element = element,
                // This method is called before a coroutine is resumed on a thread that
                // belongs to a dispatcher. This can be called more than once. So avoid creating
                // slices unless we transition to `STATE_END`.
                updateThreadContextBlock = { context ->
                    val contextElement = context[PlatformThreadContextElement.KEY]
                    if (
                        contextElement != null &&
                            contextElement.started.compareAndSet(
                                expected = STATE_END,
                                actual = STATE_BEGIN,
                            )
                    ) {
                        beginSection(name = contextElement.name, token = contextElement.token)
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
            beginSection(name = name, token = element, metadataBlock = metadataBlock)
            try {
                block()
            } finally {
                if (
                    threadContextElement.started.compareAndSet(
                        expected = STATE_BEGIN,
                        actual = STATE_END,
                    )
                ) {
                    // Only end if still in STATE_STARTED.
                    // This prevents superfluous endSection() markers.
                    endSection()
                }
            }
        }
    }
}
