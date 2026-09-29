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

package androidx.compose.ui.util

/**
 * Collects the [trace] sections of one frame on the thread that draws it.
 *
 * The desktop [trace] does nothing until you set the `compose.trace.frames` system property to
 * `true`, or set [enabled]. The frame pump then calls [beginFrame] before it draws, and [endFrame]
 * after. Each [trace] section between the two calls adds its time to a total for its name.
 * [endFrame] returns the totals as a [Frame].
 *
 * This is the desktop counterpart of reading trace sections from a system trace on Android, which
 * is how a macrobenchmark `TraceSectionMetric` measures a phase. The section names are the ones
 * Compose already emits: `FrameRecomposer:performFrame` for composition,
 * `RootNodeOwner:measureAndLayout` for measure and layout, and `RootNodeOwner:draw` for draw.
 *
 * The collector keeps its state in a thread local. A section on a different thread records
 * nothing. This keeps the measurement free of locks. It also limits the report to the thread
 * that the frame pump drives.
 *
 * Sections nest. `FrameRecomposer:performFrame` contains `Compose:recompose`, so the totals
 * overlap. Read a total as the time in the section and everything below it.
 */
object ComposeFrameTrace {

    /** The totals of one section name within a frame. */
    class Section(
        /** How many times a section with this name ended within the frame. */
        val count: Int,
        /** The sum of the durations of those sections, in nanoseconds. */
        val totalNanos: Long,
    )

    /** The sections of one frame, from [beginFrame] to [endFrame]. */
    class Frame(
        /** The time from [beginFrame] to [endFrame], in nanoseconds. */
        val durationNanos: Long,
        /** The totals by section name, in the order the names first ended. */
        val sections: Map<String, Section>,
    ) {
        /** The total for [sectionName], in nanoseconds, or zero when no such section ended. */
        fun totalNanos(sectionName: String): Long = sections[sectionName]?.totalNanos ?: 0L

        /** The totals as one line, sorted from the largest total to the smallest. */
        fun format(): String =
            sections.entries
                .sortedByDescending { it.value.totalNanos }
                .joinToString(" ") { (name, section) ->
                    "$name=${formatMillis(section.totalNanos)}/${section.count}"
                }
    }

    /** True when [trace] must measure a section. Reads the `compose.trace.frames` property. */
    @Volatile
    @JvmField
    var enabled: Boolean = System.getProperty("compose.trace.frames").toBoolean()

    private class OpenFrame(val startNanos: Long) {
        /** Index 0 of the value holds the call count. Index 1 holds the total time in nanoseconds. */
        val totals = LinkedHashMap<String, LongArray>()
    }

    private val openFrame = ThreadLocal<OpenFrame?>()

    /** Starts a frame on the current thread. A second call discards the earlier totals. */
    fun beginFrame() {
        if (enabled) {
            openFrame.set(OpenFrame(System.nanoTime()))
        }
    }

    /**
     * Ends the frame on the current thread.
     *
     * @return the frame, or null when no frame was begun on this thread. A frame that recorded no
     *   section has empty [Frame.sections].
     */
    fun endFrame(): Frame? {
        val frame = openFrame.get() ?: return null
        openFrame.remove()
        return Frame(
            durationNanos = System.nanoTime() - frame.startNanos,
            sections = frame.totals.mapValuesTo(LinkedHashMap()) { (_, counters) ->
                Section(count = counters[0].toInt(), totalNanos = counters[1])
            },
        )
    }

    /**
     * Adds the time of one section to the total for [sectionName].
     *
     * [trace] calls this from a `finally` block. It does nothing when the current thread is
     * outside a frame.
     *
     * @param startNanos the `System.nanoTime` value from the start of the section.
     */
    fun stop(sectionName: String, startNanos: Long) {
        val endNanos = System.nanoTime()
        val frame = openFrame.get() ?: return
        val counters = frame.totals.getOrPut(sectionName) { LongArray(2) }
        counters[0]++
        counters[1] += endNanos - startNanos
    }
}

private fun formatMillis(nanos: Long): String = "%.3fms".format(nanos / 1_000_000.0)
