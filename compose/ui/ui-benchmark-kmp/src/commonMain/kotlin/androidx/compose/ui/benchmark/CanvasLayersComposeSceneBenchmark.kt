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

package androidx.compose.ui.benchmark

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Surface


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
open class CanvasLayersComposeSceneBenchmark {
    @Param("1", "16", "64") var size = 0

    private lateinit var surface: Surface
    private lateinit var canvas: Canvas
    private lateinit var recomposer: FrameRecomposer
    private lateinit var scene: androidx.compose.ui.scene.ComposeScene
    private lateinit var layers: Array<ComposeSceneLayer>
    private var outsideClickCount = 0
    private  var pressTopPos: Offset = Offset.Zero

    @Setup
    fun setup() {
        registerSkikoComposeImplementation()
        surface = Surface.makeRasterN32Premul(16, 16)
        canvas = surface.canvas.asComposeCanvas()
        recomposer = FrameRecomposer(Dispatchers.Unconfined)

        scene = CanvasLayersComposeScene(
            frameRecomposer = recomposer,
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            size = IntSize(16, 16),
        )

        // Create layers tiled across the scene bounds so pointer routing exercises bounds checks
        val context = scene as ComposeSceneContext
        layers = Array(size) { index ->
            context.createLayer(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                focusable = false,
                consumePointerInputOutside = false,
            ).apply {
                // Tile layers in a 4x4 grid pattern so they overlap the scene area
                val row = (index / 4) % 4
                val col = index % 4
                boundsInWindow = IntRect(
                    left = col * 4,
                    top = row * 4,
                    right = col * 4 + 4,
                    bottom = row * 4 + 4,
                )
            }
        }

        // Cache press position for top layer hit
        val lastLayer = layers[size - 1]
        val bounds = lastLayer.boundsInWindow
        val cx = (bounds.left + bounds.right) / 2f
        val cy = (bounds.top + bounds.bottom) / 2f
        pressTopPos = Offset(cx, cy)

        // Set up outside click tracking once to avoid per-iteration lambda allocation
        layers[0].setOutsidePointerEventListener { _, _ ->
            outsideClickCount++
        }

        // Settle initial state
        scene.measureAndLayout()
        scene.draw(canvas)
    }

    @Benchmark
    open fun measureAndLayout(): Int {
        var count = 0
        scene.measureAndLayout()
        // Count owners via the pending check path to prevent DCE
        count = if (scene.hasPendingMeasureOrLayout) 1 else 0
        return count
    }

    @Benchmark
    open fun draw(): Int {
        scene.draw(canvas)
        return 1
    }

    @Benchmark
    open fun hasPendingChecks(): Int {
        var count = 0
        repeat(256) {
            if (scene.hasPendingMeasureOrLayout) count++
            if (scene.hasPendingDraw) count++
        }
        return count
    }

    @Benchmark
    open fun pointerPressMiss(): Int {
        outsideClickCount = 0
        // Press at a position that misses all tiled layers (outside the 16x16 grid)
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Press,
            position = Offset(100f, 100f),
            type = PointerType.Mouse,
        )
        // Release to clear gestureOwner for the next iteration
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Release,
            position = Offset(100f, 100f),
            type = PointerType.Mouse,
        )
        return outsideClickCount
    }

    @Benchmark
    open fun pointerPressTopLayer(): Int {
        // Press at cached position inside the last layer's bounds
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Press,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        // Release to clear gestureOwner for the next iteration
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Release,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        return 1
    }

    @Benchmark
    open fun pointerRelease(): Int {
        // Press at cached position to set gestureOwner
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Press,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        // Release routes to gesture owner
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Release,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        return 1
    }

    @Benchmark
    open fun attachAndDetach(): Int {
        val context = scene as ComposeSceneContext
        var count = 0
        for (i in 0 until size) {
            val layer = context.createLayer(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                focusable = false,
                consumePointerInputOutside = false,
            )
            layer.boundsInWindow = IntRect(i * 2, 0, i * 2 + 2, 2)
            layer.close()
            count++
        }
        return count
    }

    @Benchmark
    open fun cancelPointerInput(): Int {
        var count = 0
        repeat(256) {
            scene.cancelPointerInput()
            count++
        }
        return count
    }

    @Benchmark
    open fun pointerMoveOverLayer(): Int {
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Move,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        return 1
    }

    @Benchmark
    open fun pointerMoveMiss(): Int {
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Move,
            position = Offset(100f, 100f),
            type = PointerType.Mouse,
        )
        return 1
    }

    @Benchmark
    open fun pointerScroll(): Int {
        scene.sendPointerEvent(
            androidx.compose.ui.input.pointer.PointerEventType.Scroll,
            position = pressTopPos,
            type = PointerType.Mouse,
        )
        return 1
    }

    @Benchmark
    open fun focusableLayerToggle(): Int {
        var count = 0
        for (i in 0 until size) {
            layers[i].focusable = true
            layers[i].focusable = false
            count++
        }
        return count
    }

    @TearDown
    fun tearDown() {
        for (layer in layers) {
            layer.close()
        }
        scene.close()
        recomposer.close()
        surface.close()
    }
}
