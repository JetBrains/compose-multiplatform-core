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

package androidx.compose.ui.desktop.windows

import org.jetbrains.desktop.win32.AngleRenderer
import org.jetbrains.desktop.win32.PhysicalSize
import org.jetbrains.desktop.win32.SurfaceParams
import org.jetbrains.desktop.win32.Application as Win32Application
import org.jetbrains.desktop.win32.Window as Win32Window
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.GLAssembledInterface
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skia.makeGLWithInterface

class AngleViewContext(
    val angleRenderer: AngleRenderer,
    val directContext: DirectContext,
) : AutoCloseable {
    private var currentSize = PhysicalSize(0, 0)
    private var surfaceParams: SurfaceParams? = null

    fun makeCurrent() {
        angleRenderer.makeCurrent()
    }

    fun renderFrame(physicalSize: PhysicalSize, pixelGeometry: PixelGeometry, drawContent: Canvas.() -> Unit) {
        makeCurrent()
        if (physicalSize.width != currentSize.width || physicalSize.height != currentSize.height) {
            currentSize = physicalSize
            surfaceParams = angleRenderer.resizeSurface(physicalSize.width, physicalSize.height)
        }
        BackendRenderTarget.makeGL(
            width = physicalSize.width,
            height = physicalSize.height,
            sampleCnt = 1,
            stencilBits = 8,
            fbId = surfaceParams!!.framebufferBinding,
            fbFormat = FramebufferFormat.GR_GL_RGBA8,
        ).use { renderTarget ->
            Surface.makeFromBackendRenderTarget(
                context = directContext,
                rt = renderTarget,
                origin = SurfaceOrigin.BOTTOM_LEFT,
                colorFormat = SurfaceColorFormat.RGBA_8888,
                colorSpace = ColorSpace.sRGB,
                surfaceProps = SurfaceProps(pixelGeometry = pixelGeometry),
            )!!.use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                try {
                    surface.canvas.drawContent()
                } finally {
                    // Reliably present the frame even if drawContent() threw, then let the exception propagate.
                    // Otherwise the window's update region is never validated, and Windows keeps re-posting WM_PAINT in a tight, unthrottled loop that effectively freezes the UI thread (AIR-5859).
                    // Might present a partially drawn (or cleared) frame which is still better than a freeze.
                    surface.flushAndSubmit()
                    angleRenderer.swapBuffers()
                }
            }
        }
    }

    override fun close() {
        makeCurrent()
        directContext.close()
    }

    companion object {
        fun create(application: Win32Application, nativeWindow: Win32Window): AngleViewContext {
            val angleRenderer = application.createAngleRenderer(nativeWindow)
            val eglFunc = angleRenderer.getEglGetProcFunc()
            angleRenderer.makeCurrent()
            val glInterface = GLAssembledInterface.createFromNativePointers(
                ctxPtr = eglFunc.ctxPtr,
                fPtr = eglFunc.fPtr,
            )
            val directContext = DirectContext.makeGLWithInterface(glInterface)
            return AngleViewContext(angleRenderer, directContext)
        }
    }
}
