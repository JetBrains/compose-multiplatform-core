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

import kotlinx.coroutines.delay

/**
 * Paces frame starts to at most one per [minFrameIntervalNs]. Skiko's frame loop is paced by
 * blocking in the GPU swap on vsync; the KDT swap does not block on vsync, so the driver awaits
 * the next slot explicitly before each frame instead. [nanoTime] is injectable for tests.
 */
internal class FramePacer(
    private val minFrameIntervalNs: Long,
    private val nanoTime: () -> Long,
) {
    private var nextFrameDeadlineNs = nanoTime()

    suspend fun awaitNextFrameSlot() {
        val waitNs = nextFrameDeadlineNs - nanoTime()
        if (waitNs > 0) {
            delay((waitNs + 999_999) / 1_000_000)
        }
        nextFrameDeadlineNs = nanoTime() + minFrameIntervalNs
    }
}
