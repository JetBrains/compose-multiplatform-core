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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.node.RootNodeOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
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
import org.jetbrains.skia.Surface

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
open class RootNodeOwnerLayersBenchmark {
    @Param("16", "64", "256") var size = 0

    private lateinit var root: RootNodeOwner
    private lateinit var layers: List<OwnedLayer>
    private lateinit var surface: Surface
    private lateinit var canvas: Canvas
    private var drawCount = 0

    @Setup
    fun setup() {
        root = createBenchmarkOwner()
        surface = Surface.makeRasterN32Premul(16, 16)
        canvas = surface.canvas.asComposeCanvas()
        layers =
            List(size) {
                root.owner
                    .createLayer(
                        drawBlock = { _, _ -> drawCount++ },
                        invalidateParentLayer = {},
                        explicitLayer = null,
                    )
                    .apply {
                        resize(IntSize(16, 16))
                        updateDisplayList()
                    }
            }
        root.measureAndLayout()
        root.draw(canvas)
    }

    @Benchmark
    open fun invalidateAndDraw(): Int {
        drawCount = 0
        layers.fastForEach { layer ->
            layer.invalidate()
        }
        root.draw(canvas)
        return drawCount
    }

    @Benchmark
    open fun invalidateAndUpdateIndividually(): Int {
        drawCount = 0
        layers.fastForEach { layer ->
            layer.invalidate()
        }
        // Unlike the draw pass's bulk clear, this removes each layer from the dirty collection
        layers.fastForEach { layer ->
            layer.updateDisplayList()
        }
        return drawCount
    }

    @TearDown
    fun tearDown() {
        for (layer in layers) layer.destroy()
        root.dispose()
        surface.close()
    }
}