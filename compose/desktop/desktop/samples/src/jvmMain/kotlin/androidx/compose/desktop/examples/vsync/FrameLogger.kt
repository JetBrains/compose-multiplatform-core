/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.desktop.examples.vsync

class FrameLogger {
    var t1 = Long.MAX_VALUE
    val frameDeltas = ArrayList<Long>(10000)
    var heuristicExpectedFrameTime = -1L

    private fun List<Long>.median() = sorted()[size / 2]
    private val FrameLogCount = 1000

    fun logFrame() {
        val t2 = System.nanoTime()
        val dt = (t2 - t1).coerceAtLeast(0)
        frameDeltas.add(dt)
        t1 = t2

        if (heuristicExpectedFrameTime > 0 && dt > heuristicExpectedFrameTime * 1.5) {
            val dtMillis = dt / 1E6
            val expectedMillis = heuristicExpectedFrameTime / 1E6
            println("Too long frame %.2f (expected %.2f)".format(dtMillis, expectedMillis))
        }

        if (frameDeltas.size % FrameLogCount == 0) {
            val fps = 1E9 / frameDeltas.average()

            // it is more precise than
            // window.window.graphicsConfiguration.device.displayMode.refreshRate
            // if vsync is supported
            heuristicExpectedFrameTime = frameDeltas.median()

            val actualFrameCount = frameDeltas.sum() / heuristicExpectedFrameTime
            val missedFrames = (actualFrameCount - frameDeltas.size).coerceAtLeast(0)
            val missedFrameCountPercent = 100.0 * missedFrames / frameDeltas.size
            println("FPS %.2f, missed frames %.2f%%".format(fps, missedFrameCountPercent))
            frameDeltas.clear()
        }
    }
}
