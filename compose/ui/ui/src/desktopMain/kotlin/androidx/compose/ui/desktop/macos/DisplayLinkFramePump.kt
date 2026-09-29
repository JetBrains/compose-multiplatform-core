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

@file:OptIn(ExperimentalAtomicApi::class)

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.util.ComposeFrameTrace
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/**
 * The display-link tick state machine, extracted from [MacOsWindow] so its latch invariant is
 * unit-testable: after every completed code path either a frame was handed to [presentAsync],
 * or (isFrameRequested == true AND the in-flight latch is clear) so the next tick can retry.
 *
 * Historical breakages: fa73c4bed51 (stuck latch after exceptions), 60aedf81adc (dead link kept).
 *
 * Generic over the picture type [P] (production: `PresentablePicture`) so tests don't need to
 * construct Skia/KDT objects; the type parameter is erased at runtime, so the production wiring
 * is zero-overhead.
 *
 * [onDisplayLinkTick] is called on the display-link thread; the frame body runs in
 * [dispatchOnMain]. The in-flight latch (a CAS over the tick's [FrameTimings]) is what keeps
 * at most one frame in the pipeline, and it also carries the measurements that the completion
 * callback logs.
 *
 * The log line splits the frame into three parts. `dispatch` is the wait on [dispatchOnMain].
 * `prepare` is [preparePicture], which composes, measures, lays out and records the picture.
 * `present` is the rest, so it holds [presentAsync] and the GPU. Only `prepare` is Compose
 * work on the CPU. Two system properties control the log.
 *  - `compose.frame.longFrameMs` sets the threshold in milliseconds. It defaults to 10.
 *  - `compose.frame.logAll` logs every frame, not only a frame over the threshold. Use it to
 *    get a distribution, because a threshold alone hides the frames that are fast.
 *
 * Set `compose.trace.frames` as well to append the [ComposeFrameTrace] section totals of the
 * `prepare` part.
 */
internal class DisplayLinkFramePump<P : AutoCloseable>(
    private val isDisposed: () -> Boolean,
    private val isFrameRequested: () -> Boolean,
    private val setFrameRequested: (Boolean) -> Unit,
    /** Production: `GrandCentralDispatch.dispatchOnMain(highPriority = true, f = block)`. */
    private val dispatchOnMain: (block: () -> Unit) -> Unit,
    private val preparePicture: () -> P?,
    /** Production: `viewContext.presentAsync(picture, waitForCATransaction = false, onComplete)`. */
    private val presentAsync: (picture: P, onComplete: () -> Unit) -> Unit,
    private val logError: (Throwable, String) -> Unit,
) {

    /**
     * The measurements of one frame. The display-link thread creates it. The main thread fills
     * [dispatchNanos], [prepareNanos] and [sections] in. The thread that completes the present
     * reads them, so the fields are volatile.
     */
    private class FrameTimings(val tickAt: TimeSource.Monotonic.ValueTimeMark) {
        @Volatile var dispatchNanos: Long = 0
        @Volatile var prepareNanos: Long = 0
        @Volatile var sections: String? = null
    }

    private val frameInFlight: AtomicReference<FrameTimings?> =
        AtomicReference(null)

    /** True while a frame is being prepared or presented (the latch is held). */
    val isFrameInFlight: Boolean
        get() = frameInFlight.load() != null

    /**
     * Clears the in-flight latch unconditionally; returns true if it was held. Introspection /
     * escape hatch for callers coordinating with the pump outside the tick path.
     *
     * Hazard: calling this while a frame is actually in flight (a present was dispatched via
     * [presentAsync] and hasn't completed yet) clears the latch out from under it. When that
     * present's `onComplete` callback later runs, its `frameInFlight.exchange(null)!!` finds
     * the latch already null and throws an NPE. Callers must only clear the *same* pump instance
     * they know has no in-flight present outstanding — there is no cross-check against
     * [isFrameInFlight] here. There is currently no production caller; this exists as a test-only
     * escape hatch for tests that need to reset pump state between cases.
     */
    fun clearInFlight(): Boolean = frameInFlight.exchange(null) != null

    fun onDisplayLinkTick() {
        val frameTimings = FrameTimings(TimeSource.Monotonic.markNow())
        if (
            !isDisposed() &&
            isFrameRequested() &&
            frameInFlight.compareAndSet(null, frameTimings)
        ) {
            dispatchOnMain {
                setFrameRequested(false)
                frameTimings.dispatchNanos =
                    frameTimings.tickAt.elapsedNow().inWholeNanoseconds
                try {
                    measuredPreparePicture(frameTimings)?.let { presentablePicture ->
                        try {
                            presentAsync(
                                presentablePicture,
                                {
                                    presentablePicture.close()
                                    logFrame(frameInFlight.exchange(null)!!)
                                },
                            )
                        } catch (throwable: Throwable) {
                            logError(throwable, "Could not schedule frame presentation")
                            setFrameRequested(true)
                            frameInFlight.compareAndSet(
                                frameTimings,
                                null,
                            )
                            presentablePicture.close()
                        }
                    } ?: run {
                        setFrameRequested(true)
                        frameInFlight.compareAndSet(
                            frameTimings,
                            null,
                        )
                    }
                } catch (throwable: Throwable) {
                    logError(throwable, "Could not prepare frame")
                    setFrameRequested(true)
                    frameInFlight.compareAndSet(
                        frameTimings,
                        null,
                    )
                }
            }
        }
    }

    /**
     * Runs [preparePicture] and records how long it took in [timings].
     *
     * The method throws what [preparePicture] throws, so the caller keeps its error paths and
     * the latch invariant stays the same.
     */
    private fun measuredPreparePicture(timings: FrameTimings): P? {
        ComposeFrameTrace.beginFrame()
        val prepareAt = TimeSource.Monotonic.markNow()
        try {
            return preparePicture()
        } finally {
            timings.prepareNanos = prepareAt.elapsedNow().inWholeNanoseconds
            timings.sections =
                ComposeFrameTrace.endFrame()?.takeIf { it.sections.isNotEmpty() }?.format()
        }
    }

    private fun logFrame(timings: FrameTimings) {
        val totalNanos = timings.tickAt.elapsedNow().inWholeNanoseconds
        if (!logEveryFrame && totalNanos < longFrameThresholdNanos) {
            return
        }
        val presentNanos = totalNanos - timings.dispatchNanos - timings.prepareNanos
        // Call the eager `debug(message)` overload on purpose. `ConsoleLogger.isDebugEnabled` is
        // always false, so the `debug { }` lambda overload prints nothing, while the eager one
        // prints. The early return above is what keeps the string off the hot path.
        logger.debug(
            buildString {
                append("Frame: total=").append(formatMillis(totalNanos))
                append(" dispatch=").append(formatMillis(timings.dispatchNanos))
                append(" prepare=").append(formatMillis(timings.prepareNanos))
                append(" present=").append(formatMillis(presentNanos))
                timings.sections?.let { append(" | ").append(it) }
            }
        )
    }
}

private val logger = logger<DisplayLinkFramePump<*>>()

/** The threshold of the long-frame log, from the `compose.frame.longFrameMs` property. */
private val longFrameThresholdNanos: Long =
    (System.getProperty("compose.frame.longFrameMs")?.toLongOrNull() ?: 10L) * 1_000_000L

/** True when every frame is logged, from the `compose.frame.logAll` property. */
private val logEveryFrame: Boolean = System.getProperty("compose.frame.logAll").toBoolean()

private fun formatMillis(nanos: Long): String = "%.3fms".format(nanos / 1_000_000.0)
