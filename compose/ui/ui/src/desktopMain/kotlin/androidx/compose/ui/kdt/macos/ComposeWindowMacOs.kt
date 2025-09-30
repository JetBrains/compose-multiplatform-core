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

package androidx.compose.ui.kdt.macos

import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.kdt.ComposeWindow
import androidx.compose.ui.kdt.ComposeWindowScope
import androidx.compose.ui.kdt.toDpSize
import androidx.compose.ui.kdt.toIntSize
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.atomicfu.atomic
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.Window
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

class ComposeWindowMacOs(application: ComposeApplicationMacOs): ComposeWindow {
    val window = Window.create()
    val viewContext = application.desktopGpuContext.createMetalViewContext()

    init {
        window.attachView(viewContext.view)
    }

    val pictureRecorder = PictureRecorder()

    internal val isFrameScheduled = atomic(false)

    val scene = CanvasLayersComposeScene(
        density = Density(window.scaleFactor().toFloat()),
        size = window.contentSize.toIntSize(),
        coroutineContext = ComposeUIDispatcher,
        invalidate = {
            isFrameScheduled.compareAndSet(expect = false, update = true)
        }
    )

    var displayLink: DisplayLink? = null

    fun preparePicture(): PresentablePicture {
        val size = viewContext.view.size()
        val bounds = Rect.makeWH(size.width.toFloat(), size.height.toFloat())
        val canvas = pictureRecorder.beginRecording(bounds)
        canvas.clear(Color.White.toArgb())
        scene.render(canvas.asComposeCanvas(), System.nanoTime())
        return PresentablePicture(pictureRecorder.finishRecordingAsPicture(), size)
    }

    fun setupDisplayLink() {
        displayLink?.close()
        displayLink = DisplayLink.create(window.screenId()) {
            if (isFrameScheduled.compareAndSet(expect = true, update = false)) {
                GrandCentralDispatch.dispatchOnMain(highPriority = true) {
                    val presentablePicture = preparePicture()
                    viewContext.presentAsync(presentablePicture, waitForCATransaction = false, onComplete = {
                        presentablePicture.close()
                    })
                }
            }
        }
        displayLink!!.setRunning(true)
    }

    fun repaintSynchronously() {
        displayLink?.setRunning(false)
        isFrameScheduled.value = false
        preparePicture().use { picture ->
            viewContext.presentSync(picture, waitForCATransaction = true)
        }
        displayLink?.setRunning(true)
    }

    fun setupDisplayLayerCallback() {
        viewContext.onDisplayLayer = {
            repaintSynchronously()
        }
    }

    init {
        setupDisplayLink()
        setupDisplayLayerCallback()
    }

    fun handleEvent(event: WindowEvent) {
        when(event) {
            is Event.WindowScreenChange -> {
                setupDisplayLink()
            }
            is Event.WindowResize -> {
                scene.size = window.contentSize.toIntSize()
            }
        }
    }

    override fun setContent(content: @Composable () -> Unit) {
        scene.setContent(content)
    }

    override val size: DpSize
        get() = window.size.toDpSize()
    override val contentSize: DpSize
        get() = window.contentSize.toDpSize()
    override val isActive: Boolean
        get() = window.isMain
    override val isKey: Boolean
        get() = window.isKey
}