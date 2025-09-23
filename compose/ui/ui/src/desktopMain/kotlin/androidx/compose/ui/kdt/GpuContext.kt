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

import org.jetbrains.desktop.macos.MetalCommandQueue
import org.jetbrains.desktop.macos.MetalDevice
import org.jetbrains.desktop.macos.MetalView
import org.jetbrains.desktop.macos.PhysicalSize
import org.jetbrains.desktop.macos.QualityOfService
import org.jetbrains.desktop.macos.setQualityOfServiceForCurrentThread
import org.jetbrains.desktop.macos.withAutoReleasePool
import org.jetbrains.skia.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock


data class MetalViewContext(
  val view: MetalView,
  private val onDisplayLayerCallbackReference: CallbackReference,
  val directContext: DirectContext,
) {

  var onDisplayLayer: () -> Unit
    get() = onDisplayLayerCallbackReference.callback
    set(value) {
      onDisplayLayerCallbackReference.callback = value
    }

  private val renderTaskLock = ReentrantLock()
  private val hasPendingTask = renderTaskLock.newCondition()

  data class RenderTask(val picture: PresentablePicture,
                        val waitForCATransaction: Boolean,
                        val onComplete: () -> Unit)

  var pendingTask: RenderTask? = null

  private val renderThread = thread(start = true, isDaemon = true) {
    setQualityOfServiceForCurrentThread(QualityOfService.UserInteractive)
    while (!Thread.currentThread().isInterrupted) {
      try {
        val task = renderTaskLock.withLock {
          if (pendingTask == null) {
            hasPendingTask.await()
          }
          pendingTask!!
        }
        presentSync(task.picture, task.waitForCATransaction)
      }
      catch (_: InterruptedException) {
        break
      }
      catch (e: Throwable) {
          println("Error during rendering")
      } finally {
        renderTaskLock.withLock {
          pendingTask?.onComplete?.invoke()
          pendingTask = null
        }
      }
    }
  }

  fun stopRenderThread() {
    renderThread.interrupt()
    renderThread.join()
  }

  fun presentAsync(picture: PresentablePicture,
                   waitForCATransaction: Boolean,
                   onComplete: () -> Unit = {}) {
    renderTaskLock.withLock {
      assert(pendingTask == null, lazyMessage = { "The previous task isn't finished yet" })
      pendingTask = RenderTask(picture,
                               waitForCATransaction,
                               onComplete)
      hasPendingTask.signalAll()
    }
  }

  private val drawingLock = Any()

  fun presentSync(picture: PresentablePicture, waitForCATransaction: Boolean) {
    synchronized(drawingLock) {
      val size = view.size()
      if (size == picture.size) {
        // if view size is different, we skip the presenting
        // will wait for sync draw on resize event
        withAutoReleasePool {
          view.nextTexture().use { texture ->
            BackendRenderTarget.makeMetal(size.width.toInt(), size.height.toInt(), texture.pointerAddress).use { renderTarget ->
              Surface.makeFromBackendRenderTarget(
                context = directContext,
                origin = SurfaceOrigin.TOP_LEFT,
                colorFormat = SurfaceColorFormat.BGRA_8888,
                colorSpace = ColorSpace.sRGB,
                surfaceProps = null,
                rt = renderTarget
              )!!.use { surface ->
                surface.canvas.drawPicture(picture.picture)
                surface.flushAndSubmit()
              }
            }
            view.present(desktopGpuContext.metalCommandQueue,
                         waitForCATransaction = waitForCATransaction)
          }
        }
      }
    }
  }
}

data class PresentablePicture(val picture: Picture, val size: PhysicalSize): AutoCloseable {
  override fun close() {
    picture.close()
  }
}

class CallbackReference(var callback: () -> Unit)
data class DesktopGpuContext(
  val metalDevice: MetalDevice = MetalDevice.create(),
  val metalCommandQueue: MetalCommandQueue = MetalCommandQueue.create(metalDevice),
  val hostedViews: MutableSet<MetalViewContext> = mutableSetOf(),
) : AutoCloseable {
  override fun close() {
    check(hostedViews.isEmpty()) {
      "Can't destroy GpuContext, some hostedViews is still alive: ${hostedViews.count()}"
    }
    metalCommandQueue.close()
    metalDevice.close()
  }

  fun createMetalViewContext(onDisplayLayer: () -> Unit = {}): MetalViewContext {
    val onDisplayLayerCallbackReference = CallbackReference(onDisplayLayer)
    val view = MetalView.create(metalDevice, onDisplayLayer = {
      onDisplayLayerCallbackReference.callback()
    })
    val directContext = DirectContext.makeMetal(metalDevice.pointerAddress,
                                                metalCommandQueue.pointerAddress)
    val metalViewContext = MetalViewContext(view, onDisplayLayerCallbackReference, directContext)
    check(hostedViews.add(metalViewContext)) { "View already exists" }
    return metalViewContext
  }

  fun destroyMetalViewContext(metalViewContext: MetalViewContext) {
    check(hostedViews.remove(metalViewContext)) { "No such view" }
    metalViewContext.stopRenderThread()
    metalViewContext.directContext.close()
    metalViewContext.view.close()
  }
}
