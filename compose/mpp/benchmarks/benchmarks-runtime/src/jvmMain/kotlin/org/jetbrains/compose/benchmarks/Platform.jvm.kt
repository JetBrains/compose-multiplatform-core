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

import kotlin.coroutines.ContinuationInterceptor
import kotlin.use
import kotlinx.coroutines.currentCoroutineContext
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
import org.jetbrains.skiko.graphicapi.MetalOffscreenContext
import org.jetbrains.skiko.graphicapi.OpenGLOffscreenContext
import org.jetbrains.skiko.hostOs

internal actual object Platform {
    private val cachedGraphicsContext: GraphicsContext by lazy {
        val context = when {
            hostOs.isWindows -> DirectXGraphicsContext()
            hostOs.isMacOS -> MetalGraphicsContext()
            hostOs.isLinux -> OpenGLGraphicsContext()
            else -> EmptyGraphicsContext
        }
        try {
            // A device can be created successfully while texture or surface allocation still fails.
            runBlocking { context.withSurface(1, 1) {} }
            context
        } catch (e: Exception) {
            error("Cannot initialize ${context::class.simpleName}: $e")
        } catch (e: LinkageError) {
            // Older Skiko versions don't provide all offscreen contexts.
            error("Cannot initialize ${context::class.simpleName}: $e")
        }
    }

    actual fun getGraphicsContext(): GraphicsContext = cachedGraphicsContext

    actual fun runGC() {
        System.gc()
    }
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

@OptIn(ExperimentalSkikoApi::class)
private class MetalGraphicsContext : GraphicsContext {
    private val context = MetalOffscreenContext()

    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T =
        context.Texture(width, height).use { texture ->
            context.directContext
                .makeSurface(texture.backendRenderTarget, SurfaceOrigin.TOP_LEFT, SurfaceColorFormat.BGRA_8888)
                .use { action(it) }
        }

    override suspend fun awaitGPUCompletion() {
        context.directContext.submit(syncCpu = true)
    }
}

@OptIn(ExperimentalSkikoApi::class)
private class OpenGLGraphicsContext : GraphicsContext {
    private val context = OpenGLOffscreenContext()

    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T {
        // Skiko keeps the GL context current only inside the synchronous `withTexture` callback, so the
        // action has to complete on this thread. The dispatcher is dropped so that continuations
        // aren't dispatched to the caller's thread, which is blocked here.
        val callerContext = currentCoroutineContext().minusKey(ContinuationInterceptor)
        return context.withTexture(width, height) { texture ->
            context.directContext
                .makeSurface(texture.backendRenderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888)
                .use { surface -> runBlocking(callerContext) { action(surface) } }
        }
    }

    override suspend fun awaitGPUCompletion() {
        context.directContext.submit(syncCpu = true)
    }
}

private fun DirectContext.makeSurface(
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