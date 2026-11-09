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

package org.jetbrains.compose.benchmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic

internal data class StartupResult(
    val timeOfFirstFrame: Monotonic.ValueTimeMark,
    val timeOfNthFrame: Monotonic.ValueTimeMark,
)

@Composable
internal fun MeasureStartup(
    frameCount: Int,
    onComplete: (StartupResult) -> Unit,
    content: @Composable () -> Unit,
) {
    require(frameCount > 0) { "frameCount must be positive" }
    val currentOnComplete by rememberUpdatedState(onComplete)

    content()

    LaunchedEffect(frameCount) {
        withFrameNanos { }
        val timeOfFirstFrame = Monotonic.markNow()
        repeat(frameCount) { withFrameNanos { } }
        val timeOfNthFrame = Monotonic.markNow()
        currentOnComplete(StartupResult(timeOfFirstFrame, timeOfNthFrame))
    }
}

internal data class RealFramesResult(
    val frameTimes: List<Duration>,
    val totalDuration: Duration,
)

@Composable
internal fun MeasureRealFrames(
    frameCount: Int,
    onComplete: (RealFramesResult) -> Unit,
    content: @Composable () -> Unit,
) {
    require(frameCount > 0) { "frameCount must be positive" }
    val currentOnComplete by rememberUpdatedState(onComplete)
    val frames = remember { arrayOfNulls<Duration>(frameCount) }

    content()

    LaunchedEffect(frameCount) {
        // Skip the initial partial frame interval.
        withFrameNanos { }
        val start = Monotonic.markNow()
        repeat(frameCount) { index ->
            val frameStart = Monotonic.markNow()
            withFrameNanos { }
            frames[index] = frameStart.elapsedNow()
        }
        val totalDuration = start.elapsedNow()
        currentOnComplete(RealFramesResult(frames.filterNotNull(), totalDuration))
    }
}
