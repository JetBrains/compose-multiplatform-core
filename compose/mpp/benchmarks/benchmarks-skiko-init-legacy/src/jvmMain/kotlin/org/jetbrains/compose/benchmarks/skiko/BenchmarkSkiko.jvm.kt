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
package org.jetbrains.compose.benchmarks.skiko

import kotlin.use
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.graphicapi.DirectXOffscreenContext
import org.jetbrains.skiko.hostOs

import org.jetbrains.compose.benchmarks.GraphicsContext
import org.jetbrains.compose.benchmarks.JvmGraphicsContextConfiguration

internal actual fun initGraphicsContext() {
    val fallback = JvmGraphicsContextConfiguration.getJvmGraphicsContext
    val graphicsContext: GraphicsContext by lazy {
        val context = when {
            hostOs.isWindows -> DirectXGraphicsContext()
            hostOs.isMacOS -> MetalGraphicsContext()
            hostOs.isLinux -> OpenGLGraphicsContext()
            else -> fallback()
        }
        try {
            // A device can be created successfully while texture or surface allocation still fails.
            runBlocking { context.withSurface(1, 1) {} }
            context
        } catch (e: Exception) {
            error("Cannot initialize ${context::class.simpleName}: $e")
        } catch (e: LinkageError) {
            error("Cannot initialize ${context::class.simpleName}: $e")
        }
    }

    JvmGraphicsContextConfiguration.getJvmGraphicsContext = { graphicsContext }
}

@OptIn(ExperimentalSkikoApi::class)
private class DirectXGraphicsContext : GraphicsContext {
    private val context = DirectXOffscreenContext()
    private var texture: DirectXOffscreenContext.Texture? = null

    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T =
        context.Texture(width, height).use { texture ->
            this.texture = texture
            try {
                context.directContext
                    .makeSurface(texture.backendRenderTarget, SurfaceOrigin.TOP_LEFT, SurfaceColorFormat.BGRA_8888)
                    .use { action(it) }
            } finally {
                this.texture = null
            }
        }

    override suspend fun awaitGPUCompletion() {
        texture?.waitForCompletion()
    }
}

internal fun DirectContext.makeSurface(
    renderTarget: BackendRenderTarget,
    origin: SurfaceOrigin,
    format: SurfaceColorFormat,
): Surface = Surface.makeFromBackendRenderTarget(
    context = this,
    rt = renderTarget,
    origin = origin,
    colorFormat = format,
    colorSpace = ColorSpace.sRGB,
    surfaceProps = SurfaceProps(pixelGeometry = PixelGeometry.UNKNOWN)
) ?: error("Can't create Surface")
