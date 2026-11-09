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
import androidx.compose.ui.graphics.asComposeCanvas
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.measureTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.jetbrains.skia.Color
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

internal class ContentMeasurement(
    private val width: Int,
    private val height: Int,
    private val targetFps: Int,
    private val warmupCount: Int,
    private val content: @Composable () -> Unit
) {
    class FrameInfo(val cpu: Duration, val gpu: Duration, val fps: Double)

    private val graphicsContext = Platform.getGraphicsContext()
    private val pictureRecorder = PictureRecorder()
    private val nanosPerFrame = (1.0 / targetFps.toDouble() * 1E9.toLong()).toLong()

    suspend fun measureAverageFrame(frameCount: Int): FrameInfo = measure { scene, surface ->
        var gpuTime = Duration.ZERO
        val totalTime = measureTime {
            eventLoop(frameCount) { frameIndex ->
                gpuTime += scene.renderOnSurface(surface, frameIndex * nanosPerFrame)
            }
        }
        val cpuTime = totalTime - gpuTime
        FrameInfo(
            cpu = cpuTime / frameCount,
            gpu = gpuTime / frameCount,
            fps = frameCount.toDouble() / totalTime.toDouble(DurationUnit.SECONDS)
        )
    }

    suspend fun measureRenderFps(frameCount: Int) = measure { scene, surface ->
        var nextVSync = Duration.ZERO
        var missedFrames = 0
        val start = markNow()

        val frameInfos = arrayOfNulls<FrameInfo?>(frameCount)

        eventLoop(frameCount) { frameIndex ->
            val frameStart = start + nextVSync
            val gpuTime = scene.renderOnSurface(surface, frameIndex * nextVSync.inWholeNanoseconds)
            val frameTime = frameStart.elapsedNow()
            val cpuTime = frameTime - gpuTime
            frameInfos[frameIndex] = FrameInfo(cpuTime, gpuTime, 1 / frameTime.toDouble(DurationUnit.SECONDS))

            missedFrames += (frameTime.inWholeNanoseconds / nanosPerFrame).toInt()
            nextVSync = ((frameIndex + 1 + missedFrames) * nanosPerFrame).nanoseconds
            val timeUntilNextVSync = nextVSync - start.elapsedNow()
            if (timeUntilNextVSync > Duration.ZERO) {
                // Emulate waiting for next vsync
                preciseDelay(timeUntilNextVSync)
            }
        }

        frameInfos.filterNotNull()
    }

    private suspend fun <T> measure(action: suspend (BenchmarkComposeScene, Surface) -> T): T {
        val scene = BenchmarkComposeScene(width, height).apply { setContent(content) }
        val surface = graphicsContext.createSurface(width, height)

        try {
            warmup(scene, surface)
            return action(scene, surface)
        } finally {
            scene.close()
            surface.close()
            Platform.runGC()
        }
    }

    private suspend fun warmup(scene: BenchmarkComposeScene, surface: Surface) {
        eventLoop(warmupCount) { frameIndex ->
            scene.renderOnSurface(surface, frameIndex * nanosPerFrame)
        }
        Platform.runGC()
    }

    /**
     * Renders the scene on the given surface at the specified time.
     * @return The duration of the GPU rendering operation.
     */
    private suspend fun BenchmarkComposeScene.renderOnSurface(
        surface: Surface,
        time: Long
    ): Duration {
        val bounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
        val canvas = pictureRecorder.beginRecording(bounds).asComposeCanvas()
        render(canvas, time)
        val picture = pictureRecorder.finishRecordingAsPicture()
        surface.canvas.clear(Color.TRANSPARENT)
        surface.canvas.drawPicture(picture)
        picture.close()
        surface.flushAndSubmit()
        return measureTime { graphicsContext.awaitGPUCompletion() }
    }

    private suspend inline fun eventLoop(count: Int, action: (frameIndex: Int) -> Unit) {
        repeat(count) {
            action(it)
            yield()
        }
    }

    private suspend inline fun preciseDelay(duration: Duration) {
        val liveDelay: Duration
        if (duration.inWholeMilliseconds > 2) {
            //experiments have shown that for precise delay we should do live delay at least 2 ms
            val delayMillis = duration.inWholeMilliseconds - 2
            val delayStart = markNow()
            delay(delayMillis)
            liveDelay = duration - delayStart.elapsedNow()
        } else {
            liveDelay = duration
        }
        val liveDelayStart = markNow()
        while (liveDelayStart.elapsedNow() < liveDelay) {
            // block a current thread
        }
    }
}