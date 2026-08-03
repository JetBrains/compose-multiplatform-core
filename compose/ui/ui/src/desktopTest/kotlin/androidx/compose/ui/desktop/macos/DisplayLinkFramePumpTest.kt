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

package androidx.compose.ui.desktop.macos

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Invariant matrix for [DisplayLinkFramePump]: after every completed code path either a frame
 * was handed to presentAsync, or (isFrameRequested == true AND the in-flight latch is clear)
 * so the next tick can retry. Historical breakages this guards against: fa73c4bed51 (stuck
 * latch after exceptions), 60aedf81adc (dead link kept).
 */
@Category(HeadlessTest::class)
class DisplayLinkFramePumpTest {

    private class FakePicture : AutoCloseable {
        var closeCount = 0
        override fun close() {
            closeCount++
        }
    }

    private var disposed = false
    private var frameRequested = false
    private var dispatchCount = 0
    private val presented = mutableListOf<FakePicture>()
    private val pendingCompletions = mutableListOf<() -> Unit>()
    private val loggedErrors = mutableListOf<Pair<Throwable, String>>()

    private var prepare: () -> FakePicture? = { FakePicture() }
    private var present: (FakePicture, () -> Unit) -> Unit = { picture, onComplete ->
        presented += picture
        pendingCompletions += onComplete
    }

    private val pump = DisplayLinkFramePump<FakePicture>(
        isDisposed = { disposed },
        isFrameRequested = { frameRequested },
        setFrameRequested = { frameRequested = it },
        // Production dispatches to the GCD main queue; the fake runs the block inline so a
        // tick's dispatched body completes within onDisplayLinkTick.
        dispatchOnMain = { block ->
            dispatchCount++
            block()
        },
        preparePicture = { prepare() },
        presentAsync = { picture, onComplete -> present(picture, onComplete) },
        logError = { throwable, message -> loggedErrors += throwable to message },
    )

    /**
     * The liveness invariant, asserted at the end of every test: in the just-completed path,
     * either a frame was handed to presentAsync exactly once, XOR the pump is retryable
     * (frameRequested is true again AND the latch is clear so the next tick can proceed).
     */
    private fun assertInvariant(presentedInPath: Int) {
        assertTrue(
            (presentedInPath == 1) xor (frameRequested && !pump.isFrameInFlight),
            "invariant violated: presentedInPath=$presentedInPath, " +
                "frameRequested=$frameRequested, isFrameInFlight=${pump.isFrameInFlight}",
        )
    }

    @Test
    fun happyPathPresentsExactlyOnceAndCompletionClearsTheLatch() {
        frameRequested = true
        pump.onDisplayLinkTick()

        assertEquals(1, dispatchCount, "tick with a pending request must dispatch")
        assertEquals(1, presented.size, "exactly one frame must be handed to presentAsync")
        assertFalse(frameRequested, "the request must be consumed before preparing")
        assertTrue(pump.isFrameInFlight, "latch must be held until onComplete")

        // BEFORE onComplete: a second tick is a no-op (latch held).
        frameRequested = true
        pump.onDisplayLinkTick()
        assertEquals(1, dispatchCount, "tick while in flight must not dispatch")
        assertEquals(1, presented.size)

        // onComplete closes the picture and clears the latch.
        pendingCompletions.removeFirst().invoke()
        assertEquals(1, presented.single().closeCount, "onComplete must close the picture once")
        assertFalse(pump.isFrameInFlight, "onComplete must clear the latch")
        assertFalse(pump.clearInFlight(), "latch must already be clear after onComplete")

        // AFTER onComplete: the still-pending request is served by the next tick.
        pump.onDisplayLinkTick()
        assertEquals(2, dispatchCount)
        assertEquals(2, presented.size, "a new tick must present again once the latch is clear")
        pendingCompletions.removeFirst().invoke()
        assertEquals(1, presented[1].closeCount)

        assertInvariant(presentedInPath = presented.size - 1)
    }

    @Test
    fun nullPictureRestoresFrameRequestedAndClearsLatch() {
        prepare = { null }
        frameRequested = true
        pump.onDisplayLinkTick()

        assertEquals(1, dispatchCount)
        assertTrue(presented.isEmpty(), "no frame must be presented when prepare returns null")
        assertTrue(frameRequested, "frameRequested must be restored so the next tick retries")
        assertFalse(pump.isFrameInFlight, "latch must be cleared on the null-picture path")
        assertInvariant(presentedInPath = 0)

        // The next tick actually retries and presents.
        prepare = { FakePicture() }
        pump.onDisplayLinkTick()
        assertEquals(2, dispatchCount)
        assertEquals(1, presented.size)
        assertInvariant(presentedInPath = 1)
    }

    @Test
    fun preparePictureThrowingRestoresFrameRequestedAndClearsLatch() {
        val boom = IllegalStateException("prepare boom")
        prepare = { throw boom }
        frameRequested = true
        pump.onDisplayLinkTick()

        assertEquals(1, dispatchCount)
        assertTrue(presented.isEmpty())
        assertTrue(frameRequested, "frameRequested must be restored after prepare throws")
        assertFalse(pump.isFrameInFlight, "latch must be cleared after prepare throws")
        assertEquals(listOf<Pair<Throwable, String>>(boom to "Could not prepare frame"), loggedErrors)
        assertInvariant(presentedInPath = 0)
    }

    @Test
    fun presentAsyncThrowingRestoresFrameRequestedClearsLatchAndClosesPicture() {
        val picture = FakePicture()
        val boom = IllegalStateException("present boom")
        prepare = { picture }
        present = { _, _ -> throw boom }
        frameRequested = true
        pump.onDisplayLinkTick()

        assertEquals(1, dispatchCount)
        assertEquals(
            1,
            picture.closeCount,
            "the picture must be closed exactly once on the presentAsync-throw path",
        )
        assertTrue(frameRequested, "frameRequested must be restored after presentAsync throws")
        assertFalse(pump.isFrameInFlight, "latch must be cleared after presentAsync throws")
        assertEquals(
            listOf<Pair<Throwable, String>>(boom to "Could not schedule frame presentation"),
            loggedErrors,
            "the inner catch must log once; the outer catch must not double-handle",
        )
        assertInvariant(presentedInPath = 0)
    }

    @Test
    fun tickWithoutFrameRequestDoesNothing() {
        pump.onDisplayLinkTick()

        assertEquals(0, dispatchCount, "tick without a request must not dispatch")
        assertTrue(presented.isEmpty())
        assertFalse(frameRequested)
        assertFalse(pump.isFrameInFlight, "the skip must not take the latch")

        // The skip left the pump serviceable: a later request is served on the next tick.
        frameRequested = true
        pump.onDisplayLinkTick()
        assertEquals(1, dispatchCount)
        assertEquals(1, presented.size)
        assertInvariant(presentedInPath = 1)
    }

    @Test
    fun tickAfterDisposeDoesNothing() {
        disposed = true
        frameRequested = true
        pump.onDisplayLinkTick()

        assertEquals(0, dispatchCount, "tick after dispose must not dispatch")
        assertTrue(presented.isEmpty())
        assertTrue(frameRequested, "the pending request must not be consumed after dispose")
        assertFalse(pump.isFrameInFlight, "the disposed skip must not take the latch")
        assertInvariant(presentedInPath = 0)
    }

    @Test
    fun concurrentTickWhileInFlightIsSkipped() {
        frameRequested = true
        pump.onDisplayLinkTick()
        assertEquals(1, dispatchCount)
        assertEquals(1, presented.size)
        assertTrue(pump.isFrameInFlight)

        // A new request arrives while the first frame is still in flight: the latch CAS fails
        // and the tick must not dispatch, must not present, and must not consume the request.
        frameRequested = true
        pump.onDisplayLinkTick()
        pump.onDisplayLinkTick()

        assertEquals(1, dispatchCount, "ticks while in flight must not dispatch")
        assertEquals(1, presented.size, "ticks while in flight must not present")
        assertTrue(frameRequested, "the pending request must survive the skipped ticks")
        assertTrue(pump.isFrameInFlight, "the latch must still be held by the first tick")
        assertInvariant(presentedInPath = 1)
    }
}
