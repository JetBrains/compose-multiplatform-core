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

package androidx.compose.ui.desktop

import androidx.compose.ui.HeadlessTest
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.experimental.categories.Category

@OptIn(ExperimentalCoroutinesApi::class)
@Category(HeadlessTest::class)
class FrameDispatcherTest {

    // An own-Job scope, NOT backgroundScope: advanceUntilIdle() doesn't run
    // background-scope coroutines.
    private fun TestScope.loopScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    @Test
    fun `scheduleFrame runs exactly one frame`() = runTest {
        var frames = 0
        val dispatcher = FrameDispatcher(loopScope()) { frames++ }
        try {
            dispatcher.scheduleFrame()
            advanceUntilIdle()
            assertEquals(1, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `no frame runs without a request`() = runTest {
        var frames = 0
        val dispatcher = FrameDispatcher(loopScope()) { frames++ }
        try {
            advanceUntilIdle()
            assertEquals(0, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `requests before the frame coalesce into one frame`() = runTest {
        var frames = 0
        val dispatcher = FrameDispatcher(loopScope()) { frames++ }
        try {
            repeat(5) { dispatcher.scheduleFrame() }
            advanceUntilIdle()
            assertEquals(1, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `a request during a frame runs exactly one follow-up frame`() = runTest {
        var frames = 0
        lateinit var dispatcher: FrameDispatcher
        dispatcher = FrameDispatcher(loopScope()) {
            frames++
            if (frames == 1) {
                dispatcher.scheduleFrame()
            }
        }
        try {
            dispatcher.scheduleFrame()
            advanceUntilIdle()
            assertEquals(2, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `requests during a frame coalesce into one follow-up frame`() = runTest {
        var frames = 0
        lateinit var dispatcher: FrameDispatcher
        dispatcher = FrameDispatcher(loopScope()) {
            frames++
            if (frames == 1) {
                repeat(3) { dispatcher.scheduleFrame() }
            }
        }
        try {
            dispatcher.scheduleFrame()
            advanceUntilIdle()
            assertEquals(2, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `every request is honored across many frames`() = runTest {
        var frames = 0
        val dispatcher = FrameDispatcher(loopScope()) { frames++ }
        try {
            repeat(10) {
                dispatcher.scheduleFrame()
                advanceUntilIdle()
            }
            assertEquals(10, frames)
        } finally {
            dispatcher.cancel()
        }
    }

    @Test
    fun `cancel stops the frame loop`() = runTest {
        var frames = 0
        val dispatcher = FrameDispatcher(loopScope()) { frames++ }
        dispatcher.cancel()
        dispatcher.scheduleFrame()
        advanceUntilIdle()
        assertEquals(0, frames)
    }
}
