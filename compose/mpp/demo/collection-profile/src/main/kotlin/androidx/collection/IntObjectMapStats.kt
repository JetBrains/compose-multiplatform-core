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

package androidx.collection

/** Records one lifetime peak per instance without retaining the maps or their contents. */
internal object IntObjectMapStats {
    private val peaks = mutableListOf<Int>()

    /** A checkpoint for measuring only instances constructed by a subsequent workload. */
    val instanceCount: Int
        get() = peaks.size

    fun register(): Int = peaks.size.also { peaks.add(0) }

    fun record(id: Int, size: Int) {
        if (size > peaks[id]) peaks[id] = size
    }

    fun snapshot(firstInstance: Int = 0): Snapshot {
        require(firstInstance in 0..peaks.size)
        return Snapshot(peaks.subList(firstInstance, peaks.size).sorted())
    }

    internal data class Snapshot(val peakSizes: List<Int>) {
        val count: Int
            get() = peakSizes.size

        val max: Int?
            get() = peakSizes.lastOrNull()

        val median: Double?
            get() = percentile(0.5)

        val p75: Double?
            get() = percentile(0.75)

        private fun percentile(fraction: Double): Double? {
            if (peakSizes.isEmpty()) return null
            val position = peakSizes.lastIndex * fraction
            val low = position.toInt()
            val high = minOf(low + 1, peakSizes.lastIndex)
            return peakSizes[low] + (peakSizes[high] - peakSizes[low]) * (position - low)
        }

        override fun toString(): String =
            "instances=$count, max=$max, median=$median, p75=$p75"
    }
}
