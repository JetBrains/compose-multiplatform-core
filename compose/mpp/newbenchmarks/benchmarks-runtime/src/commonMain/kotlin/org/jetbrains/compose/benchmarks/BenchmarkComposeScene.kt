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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers

@OptIn(InternalComposeUiApi::class)
internal class BenchmarkComposeScene(width: Int, height: Int) {
    init {
        registerSkikoComposeImplementation()
    }

    private val frameRecomposer = FrameRecomposer(Dispatchers.Unconfined)
    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        size = IntSize(width, height)
    )

    fun setContent(content: @Composable () -> Unit) {
        scene.setContent(content = content)
    }

    fun render(canvas: Canvas, nanoTime: Long) {
        frameRecomposer.performFrame(nanoTime)
        scene.measureAndLayout()
        scene.draw(canvas)
    }

    fun close() {
        scene.close()
        frameRecomposer.close()
    }
}
