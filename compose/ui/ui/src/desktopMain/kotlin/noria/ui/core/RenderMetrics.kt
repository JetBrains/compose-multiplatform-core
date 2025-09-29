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

package noria.ui.core

import kotlin.time.TimeSource

class FrameBar {
  companion object {
    const val MAX_WINDOWS_PER_BAR = 12
  }

  var startReconcile: Long = 0
  var endReconcile: Long = 0

  val startLayout = Array(MAX_WINDOWS_PER_BAR) { 0L }
  val startLayoutWindowId = Array(MAX_WINDOWS_PER_BAR) { 0L }
  var startLayoutUsed = 0

  val endLayout = Array(MAX_WINDOWS_PER_BAR) { 0L }
  val endLayoutWindowId = Array(MAX_WINDOWS_PER_BAR) { 0L }
  var endLayoutUsed = 0

  val startPainting = Array(MAX_WINDOWS_PER_BAR) { 0L }
  val startPaintingWindowId = Array(MAX_WINDOWS_PER_BAR) { 0L }
  var startPaintingUsed = 0

  val endPainting = Array(MAX_WINDOWS_PER_BAR) { 0L }
  val endPaintingWindowId = Array(MAX_WINDOWS_PER_BAR) { 0L }
  var endPaintingUsed = 0


  fun reset() {
    startReconcile = 0
    endReconcile = 0
    startLayoutUsed = 0
    endLayoutUsed = 0
    startPaintingUsed = 0
    endPaintingUsed = 0
  }

  fun startReconcile(timeNs: Long) {
    startReconcile = timeNs
  }

  fun endReconcile(timeNs: Long) {
    endReconcile = timeNs
  }

  fun startLayout(windowId: Long, timeNs: Long) {
    if (startLayoutUsed < MAX_WINDOWS_PER_BAR) {
      startLayoutWindowId[startLayoutUsed] = windowId
      startLayout[startLayoutUsed] = timeNs
      startLayoutUsed += 1
    }
  }

  fun endLayout(windowId: Long, timeNs: Long) {
    if (endLayoutUsed < MAX_WINDOWS_PER_BAR) {
      endLayoutWindowId[endLayoutUsed] = windowId
      endLayout[endLayoutUsed] = timeNs
      endLayoutUsed += 1
    }
  }

  fun startPainting(windowId: Long, timeNs: Long) {
    if (startPaintingUsed < MAX_WINDOWS_PER_BAR) {
      startPaintingWindowId[startPaintingUsed] = windowId
      startPainting[startPaintingUsed] = timeNs
      startPaintingUsed += 1
    }
  }

  fun endPainting(windowId: Long, timeNs: Long) {
    if (endPaintingUsed < MAX_WINDOWS_PER_BAR) {
      endPaintingWindowId[endPaintingUsed] = windowId
      endPainting[endPaintingUsed] = timeNs
      endPaintingUsed += 1
    }
  }

  fun paintedWindowId(): Long? {
    return if (endPaintingUsed == 1 &&
               startPaintingUsed == 1 &&
               startPaintingWindowId[0] == endPaintingWindowId[0]) {
      startPaintingWindowId[0]
    }
    else {
      null
    }
  }

  inline fun forEachPaintedWindow(f: (Long, Long, Long) -> Unit) {
    val endPaintingByWindowId = endPaintingWindowId.take(endPaintingUsed)
      .zip(endPainting.take(endPaintingUsed))
      .toMap()
    for (i in 0 until startPaintingUsed) {
      val windowId = startPaintingWindowId[i]
      val startPaintingNs = startPainting[i]
      endPaintingByWindowId[windowId]?.let { endPaintingNs ->
        f(windowId, startPaintingNs, endPaintingNs)
      }
    }
  }

  inline fun forEachLayoutedWindow(f: (Long, Long, Long) -> Unit) {
    val endLayoutByWindowId = endLayoutWindowId.take(endLayoutUsed)
      .zip(endLayout.take(endLayoutUsed))
      .toMap()
    for (i in 0 until startLayoutUsed) {
      val windowId = startLayoutWindowId[i]
      val startLayoutNs = startLayout[i]
      endLayoutByWindowId[windowId]?.let { endLayoutNs ->
        f(windowId, startLayoutNs, endLayoutNs)
      }
    }
  }
}

class RenderPerfMetrics {
  private val bars: Array<FrameBar> = Array(defaultBufferSize) { FrameBar() }
  private var lastBarIdx: Int = 0
  private val timeReference = TimeSource.Monotonic.markNow()

  private fun currentBar(): FrameBar {
    return bars[lastBarIdx]
  }

  private fun shiftBars() {
    lastBarIdx = (lastBarIdx + 1).mod(bars.size)
  }

  fun startReconcile() {
    shiftBars()
    currentBar().reset()
    currentBar().startReconcile(timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun startLayout(windowId: Long) {
    currentBar().startLayout(windowId, timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun endLayout(windowId: Long) {
    currentBar().endLayout(windowId, timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun endReconcile() {
    currentBar().endReconcile(timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun startPainting(windowId: Long) {
    currentBar().startPainting(windowId, timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun endPainting(windowId: Long) {
    currentBar().endPainting(windowId, timeReference.elapsedNow().inWholeNanoseconds)
  }

  fun averageFpsPerWindow(): Map<Long, Double> {
    val nsPerSec = 1_000_000_000.0
    return frameBars()
      .groupBy {
        it.paintedWindowId()
      }
      .mapNotNull { barsPerPaintedWindow ->
        if (barsPerPaintedWindow.key != null) {
          val averageDuration = barsPerPaintedWindow.value
            .windowed(2)
            .map { it[1].startReconcile - it[0].startReconcile }
            .filter { it > 0 }
            .average()
          val fps = nsPerSec / averageDuration
          Pair(barsPerPaintedWindow.key!!, fps)
        }
        else null
      }.toMap()
  }

  fun frameBars(): Iterable<FrameBar> {
    return object : Iterable<FrameBar> {
      override fun iterator(): Iterator<FrameBar> {
        var i = 0
        return object : Iterator<FrameBar> {
          override fun hasNext(): Boolean {
            return i < bars.size - 1
          }

          override fun next(): FrameBar {
            val bar = bars[(lastBarIdx + i).mod(bars.size)]
            i += 1
            return bar
          }
        }
      }
    }
  }

  companion object {
    const val nsPerMs = 1000000.0
    const val defaultBufferSize = 180
  }
}
