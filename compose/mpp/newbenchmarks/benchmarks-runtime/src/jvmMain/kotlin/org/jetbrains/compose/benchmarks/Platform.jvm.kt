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

import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.graphicapi.DirectXOffscreenContext
import org.jetbrains.skiko.hostOs

internal actual object Platform {
    actual fun getGraphicsContext(): GraphicsContext = when {
        hostOs.isWindows -> {
            DirectXGraphicsContext()
        }
        else -> {
            println("Unsupported desktop host OS: $hostOs. Using non-GPU graphic context")
            EmptyGraphicsContext
        }
    }

    actual fun runGC() {
        System.gc()
    }
}

@OptIn(ExperimentalSkikoApi::class)
private class DirectXGraphicsContext : GraphicsContext {
    // Note: we don't close `context` and `texture` after using,
    // because it is created once in the main function
    private val context = DirectXOffscreenContext()
    private var texture: DirectXOffscreenContext.Texture? = null

    override fun createSurface(width: Int, height: Int): Surface {
        texture?.close()
        texture = context.Texture(width, height)
        return Surface.makeFromBackendRenderTarget(
            context.directContext,
            texture!!.backendRenderTarget,
            SurfaceOrigin.TOP_LEFT,
            SurfaceColorFormat.BGRA_8888,
            ColorSpace.sRGB,
            SurfaceProps(pixelGeometry = PixelGeometry.UNKNOWN)
        ) ?: throw IllegalStateException("Can't create Surface")
    }

    override suspend fun awaitGPUCompletion() {
        texture?.waitForCompletion()
    }
}