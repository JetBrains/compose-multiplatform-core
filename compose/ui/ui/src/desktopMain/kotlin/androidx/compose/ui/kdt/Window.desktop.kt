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
import org.jetbrains.desktop.macos.Application
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.desktop.macos.Window
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

interface KdtWindowScope: NoriaContext {
    val window: KdtWindow
}

interface KdtWindow {
    val size: DpSize
    val contentSize: DpSize
    val isActive: Boolean
    val isKey: Boolean
//    var requestedConstraints: Constraints
//    val decoration: WindowDecoration
//    var preferredDecoration: WindowDecoration
//    suspend fun requestPlacement(placement: WindowPlacement): Boolean
//    fun showWindowMenu(position: DpOffset)
//    val hasActiveAppearance: Boolean
}

@Composable
fun NoriaContext.KdtWindow(content: @Composable KdtWindowScope.() -> Unit) {
    val application = LocalKdtApplication.current
    val composeWindow = remember { KdtComposeWindow(application) }
    val windowScope = object: KdtWindowScope {
        override val window: KdtWindow = composeWindow
    }
    composeWindow.setContent {
        windowScope.content()
    }
}

class KdtComposeWindow(application: KdtComposeApplication): KdtWindow {
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
        application.allWindows.put(window.windowId(), this)
        setupDisplayLink()
        setupDisplayLayerCallback()
    }

    fun setContent(content: @Composable NoriaContext.() -> Unit) {
        scene.setContent(content)
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

    override val size: DpSize
        get() = window.size.toDpSize()
    override val contentSize: DpSize
        get() = window.contentSize.toDpSize()
    override val isActive: Boolean
        get() = window.isMain
    override val isKey: Boolean
        get() = window.isKey
}