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

package androidx.compose.ui.kdt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import kotlinx.atomicfu.atomic
import noria.NoriaContext
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect


@Composable
fun WindowKDT(content: @Composable NoriaContext.() -> Unit) {
    val window = remember { ComposeKDTWindow() }
    window.setContent(content)
}

val desktopGpuContext by lazy { DesktopGpuContext() }

var lastWindow: ComposeKDTWindow? = null

class ComposeKDTWindow {
    val window = org.jetbrains.desktop.macos.Window.create()
    val viewContext = desktopGpuContext.createMetalViewContext()

    init {
        window.attachView(viewContext.view)
    }

    val pictureRecorder = PictureRecorder()

    internal val isFrameScheduled = atomic(false)

    val scene = CanvasLayersComposeScene(
        density = Density(2f),
        size = window.size.toIntSize(),
        coroutineContext = ComposeUIDispatcher,
        invalidate = {
            isFrameScheduled.compareAndSet(expect = false, update = true)
        }
    )

    var displayLink: DisplayLink? = null

    fun setupDisplayLink() {
        displayLink?.close()
        displayLink = DisplayLink.create(window.screenId()) {
            if (isFrameScheduled.compareAndSet(expect = true, update = false)) {
                GrandCentralDispatch.dispatchOnMain(highPriority = true) {
                    val size = viewContext.view.size()
                    val bounds = Rect.makeWH(size.width.toFloat(), size.height.toFloat())
                    val canvas = pictureRecorder.beginRecording(bounds)
                    canvas.clear(Color.White.toArgb())
                    scene.render(canvas.asComposeCanvas(), System.nanoTime())
                    val presentablePicture = PresentablePicture(pictureRecorder.finishRecordingAsPicture(), size)
                    viewContext.presentAsync(presentablePicture, waitForCATransaction = false, onComplete = {
                        presentablePicture.close()
                    })
                }
            }
        }
        displayLink!!.setRunning(true)
    }

    init {
        setupDisplayLink()
        lastWindow = this
    }

    fun setContent(content: @Composable NoriaContext.() -> Unit) {
        scene.setContent(content)
    }
}

fun LogicalSize.toIntSize(density: Density = Density(2f)): IntSize {
    return with(density) {
        DpSize(width.dp, height.dp).toSize().toIntSize()
    }
}