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

package androidx.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import platform.Foundation.NSTimeInterval

class FrameChoreographerTest {

    @Test
    fun testIsIdleWhenContentIsIdle() = runUIKitInstrumentedTest {
        setContent { Box(Modifier.fillMaxSize()) }
        val choreographer = frameChoreographer
        assertNotNull(choreographer, "frameChoreographer is null")

        val listener = CountingListener()
        choreographer.addListener(listener)
        try {
            settleChoreographer()

            val ticksBefore = listener.displayLinkCount
            delay(100)
            assertEquals(
                ticksBefore,
                listener.displayLinkCount,
                "Choreographer should not tick while content is idle"
            )
        } finally {
            choreographer.removeListener(listener)
        }
    }

    @Test
    fun testOngoingInteractionEventsKeepChoreographerTicking() = runUIKitInstrumentedTest {
        setContent { Box(Modifier.fillMaxSize()) }
        val choreographer = frameChoreographer
        assertNotNull(choreographer, "frameChoreographer is null")

        val listener = CountingListener()
        choreographer.addListener(listener)
        val activitiesHandler = choreographer.createActivitiesHandler()
        try {
            settleChoreographer()

            val ticksBefore = listener.displayLinkCount
            activitiesHandler.onActivitiesStarted()
            waitUntil("Choreographer should keep ticking during interaction") {
                listener.displayLinkCount > ticksBefore + 3
            }

            activitiesHandler.onActivitiesEnded()
            settleChoreographer()

            val ticksAfterStop = listener.displayLinkCount
            delay(100)
            assertEquals(
                ticksAfterStop,
                listener.displayLinkCount,
                "Choreographer should pause after interaction ends"
            )
        } finally {
            activitiesHandler.dispose()
            choreographer.removeListener(listener)
        }
    }

    @Test
    fun testDisplayLinkAndOutOfFrameCallbacksArePaired() = runUIKitInstrumentedTest {
        setContent { Box(Modifier.fillMaxSize()) }
        val choreographer = frameChoreographer
        assertNotNull(choreographer, "frameChoreographer is null")

        val listener = CountingListener()
        choreographer.addListener(listener)
        val activitiesHandler = choreographer.createActivitiesHandler()
        try {
            activitiesHandler.onActivitiesStarted()
            waitUntil("Both callbacks should be delivered while ticking") {
                listener.displayLinkCount > 3 && listener.outOfFrameCount > 3
            }
            activitiesHandler.onActivitiesEnded()
            settleChoreographer()

            // Each display-link tick schedules exactly one out-of-frame callback, so the counts
            // must stay within a single in-flight frame of each other.
            assertTrue(
                listener.displayLinkCount - listener.outOfFrameCount <= 1,
                "out-of-frame callbacks (${listener.outOfFrameCount}) should track display-link " +
                    "callbacks (${listener.displayLinkCount})"
            )
        } finally {
            activitiesHandler.dispose()
            choreographer.removeListener(listener)
        }
    }

    @Test
    fun testRemovedListenerStopsReceivingCallbacks() = runUIKitInstrumentedTest {
        setContent { Box(Modifier.fillMaxSize()) }
        val choreographer = frameChoreographer
        assertNotNull(choreographer, "frameChoreographer is null")
        val activitiesHandler = choreographer.createActivitiesHandler()

        val listener = CountingListener()
        choreographer.addListener(listener)
        activitiesHandler.onActivitiesStarted()
        waitUntil("Listener should receive callbacks while registered") {
            listener.displayLinkCount > 3
        }

        choreographer.removeListener(listener)
        val ticksAfterRemoval = listener.displayLinkCount

        // Keep the choreographer ticking, but the removed listener must not observe more ticks.
        delay(100)
        activitiesHandler.onActivitiesEnded()
        assertEquals(
            ticksAfterRemoval,
            listener.displayLinkCount,
            "Removed listener should not receive further callbacks"
        )
    }

    private fun UIKitInstrumentedTest.settleChoreographer() {
        waitForIdle()
        delay(100)
    }
}

private class CountingListener : FrameChoreographer.Listener {
    var displayLinkCount = 0
        private set
    var outOfFrameCount = 0
        private set

    override fun onDisplayLinkTick() {
        displayLinkCount++
    }

    override fun onOutOfFrame(lastFrameTimestamp: NSTimeInterval, targetTimestamp: NSTimeInterval) {
        outOfFrameCount++
    }
}
