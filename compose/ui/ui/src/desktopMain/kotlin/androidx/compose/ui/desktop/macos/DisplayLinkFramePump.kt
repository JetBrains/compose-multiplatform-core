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
 * [dispatchOnMain]. The in-flight latch (a CAS over the tick's start time mark) is what keeps
 * at most one frame in the pipeline, and it also feeds the long-frame (> 10 ms) debug logging
 * in the completion callback.
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

    private class TimeMarkWrapper(val timeMark: TimeSource.Monotonic.ValueTimeMark)

    private val frameStartTimeMark: AtomicReference<TimeMarkWrapper?> =
        AtomicReference(null)

    /** True while a frame is being prepared or presented (the latch is held). */
    val isFrameInFlight: Boolean
        get() = frameStartTimeMark.load() != null

    /**
     * Clears the in-flight latch unconditionally; returns true if it was held. Introspection /
     * escape hatch for callers coordinating with the pump outside the tick path.
     *
     * Hazard: calling this while a frame is actually in flight (a present was dispatched via
     * [presentAsync] and hasn't completed yet) clears the latch out from under it. When that
     * present's `onComplete` callback later runs, its `frameStartTimeMark.exchange(null)!!` finds
     * the latch already null and throws an NPE. Callers must only clear the *same* pump instance
     * they know has no in-flight present outstanding — there is no cross-check against
     * [isFrameInFlight] here. There is currently no production caller; this exists as a test-only
     * escape hatch for tests that need to reset pump state between cases.
     */
    fun clearInFlight(): Boolean = frameStartTimeMark.exchange(null) != null

    fun onDisplayLinkTick() {
        val frameStartTimeMarkWrapper = TimeMarkWrapper(TimeSource.Monotonic.markNow())
        if (
            !isDisposed() &&
            isFrameRequested() &&
            frameStartTimeMark.compareAndSet(null, frameStartTimeMarkWrapper)
        ) {
            dispatchOnMain {
                setFrameRequested(false)
                try {
                    preparePicture()?.let { presentablePicture ->
                        try {
                            presentAsync(
                                presentablePicture,
                                {
                                    presentablePicture.close()
                                    val elapsedTime = frameStartTimeMark
                                        .exchange(null)!!
                                        .timeMark
                                        .elapsedNow()
                                    if (elapsedTime.inWholeMilliseconds > 10) {
                                        logger.debug("Long frame: ${elapsedTime}")
                                    }
                                },
                            )
                        } catch (throwable: Throwable) {
                            logError(throwable, "Could not schedule frame presentation")
                            setFrameRequested(true)
                            frameStartTimeMark.compareAndSet(
                                frameStartTimeMarkWrapper,
                                null,
                            )
                            presentablePicture.close()
                        }
                    } ?: run {
                        setFrameRequested(true)
                        frameStartTimeMark.compareAndSet(
                            frameStartTimeMarkWrapper,
                            null,
                        )
                    }
                } catch (throwable: Throwable) {
                    logError(throwable, "Could not prepare frame")
                    setFrameRequested(true)
                    frameStartTimeMark.compareAndSet(
                        frameStartTimeMarkWrapper,
                        null,
                    )
                }
            }
        }
    }
}

private val logger = logger<DisplayLinkFramePump<*>>()
