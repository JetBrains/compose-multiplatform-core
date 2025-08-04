/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.input.pointer.util

import androidx.compose.ui.input.pointer.util.VelocityTracker1D.Strategy
import kotlin.math.abs

private const val MinimumGestureDurationMilliseconds: Int = 50

internal actual fun platformVelocityDataPointsBuilder(): VelocityTracker1D.DataPointsBuilder =
    UIKitVelocityDataPointsBuilder()

private class UIKitVelocityDataPointsBuilder: VelocityTracker1D.DataPointsBuilder {
    override val historySize: Int = 40 // Extended for 120 FPS devices

    override fun buildDataPoints(
        samples: Array<DataPointAtTime?>,
        index: Int,
        strategy: Strategy,
        isDataDifferential: Boolean,
        dataPoints: FloatArray,
        time: FloatArray
    ): Int {
        var sampleCount = 0
        var index: Int = index

        // The sample at index is our newest sample.  If it is null, we have no samples so return.
        val newestSample: DataPointAtTime = samples[index] ?: return 0

        var previousSample: DataPointAtTime = newestSample

        // Starting with the most recent PointAtTime sample, iterate backwards while
        // the samples represent continuous motion.
        do {
            val sample: DataPointAtTime = samples[index] ?: break

            val age: Float = (newestSample.time - sample.time).toFloat()
            val delta: Float = abs(sample.time - previousSample.time).toFloat()
            previousSample =
                if (strategy == Strategy.Lsq2 || isDataDifferential) {
                    sample
                } else {
                    newestSample
                }
            if (delta > DefaultVelocityDataPointsBuilder.AssumePointerMoveStoppedMilliseconds) {
                if (age < MinimumGestureDurationMilliseconds) {
                    // Short gestures made after a pointer stops are considered unintentional.
                    return 0
                }
            }
            if (age > DefaultVelocityDataPointsBuilder.HorizonMilliseconds) {
                break
            }

            dataPoints[sampleCount] = sample.dataPoint
            time[sampleCount] = -age
            index = (if (index == 0) historySize else index) - 1

            sampleCount += 1
        } while (sampleCount < historySize)

        return sampleCount
    }
}
