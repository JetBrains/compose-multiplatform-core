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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.experimental.categories.Category

@OptIn(ExperimentalCoroutinesApi::class)
@Category(HeadlessTest::class)
class FramePacerTest {

    private fun TestScope.pacer(intervalNs: Long = 16_000_000): FramePacer =
        FramePacer(minFrameIntervalNs = intervalNs) { currentTime * 1_000_000 }

    @Test
    fun `the first frame slot is immediate`() = runTest {
        val pacer = pacer()
        val before = currentTime
        pacer.awaitNextFrameSlot()
        assertEquals(before, currentTime)
    }

    @Test
    fun `back-to-back slots are paced to the minimum interval`() = runTest {
        val pacer = pacer()
        pacer.awaitNextFrameSlot()
        val afterFirst = currentTime
        pacer.awaitNextFrameSlot()
        assertEquals(afterFirst + 16, currentTime)
    }

    @Test
    fun `a frame slower than the interval is not paced further`() = runTest {
        val pacer = pacer()
        pacer.awaitNextFrameSlot()
        advanceTimeBy(20)
        val before = currentTime
        pacer.awaitNextFrameSlot()
        assertEquals(before, currentTime)
    }

    @Test
    fun `pacing is measured from the previous slot, not from the request`() = runTest {
        val pacer = pacer()
        pacer.awaitNextFrameSlot()
        advanceTimeBy(10)
        pacer.awaitNextFrameSlot()
        assertEquals(16, currentTime)
    }
}
