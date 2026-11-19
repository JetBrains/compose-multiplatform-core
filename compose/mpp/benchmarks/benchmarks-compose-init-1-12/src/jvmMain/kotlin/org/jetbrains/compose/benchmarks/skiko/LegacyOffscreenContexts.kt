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

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.benchmarks.GraphicsContext
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skiko.GpuPriority
import org.jetbrains.skiko.SkiaLayerAnalytics
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Skiko 0.150.2 has no standalone Metal offscreen API. Its Swing renderer already owns a Metal
 * device and command queue, so reuse those native resources without invoking Swing painting or
 * reading pixels back to the CPU. These private APIs are isolated to the module pinned to CMP 1.12.2.
 */
internal class MetalGraphicsContext : GraphicsContext {
    private val api = LegacySwingOffscreenApi("MetalSwingRedrawer")
    private val directContext = api.field("context").get(api.renderer) as DirectContext
    private val adapter = api.field("adapter").get(api.renderer)
    private val adapterPointer = adapter.javaClass.getDeclaredField("ptr")
        .apply { isAccessible = true }.getLong(adapter)
    private val texture = api.field("texturePtr")
    private val makeTexture = api.nativeMethod("makeMetalTexture")

    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T {
        // The old renderer reuses the texture or releases it when its size changes.
        val pointer = api.call(makeTexture, adapterPointer, texture.getLong(api.renderer), width, height) as Long
        check(pointer != 0L) { "Cannot allocate offscreen Metal texture" }
        texture.setLong(api.renderer, pointer)
        return BackendRenderTarget.makeMetal(width, height, pointer).use { target ->
            directContext.makeSurface(target, SurfaceOrigin.TOP_LEFT, SurfaceColorFormat.BGRA_8888)
                .use { action(it) }
        }
    }

    override suspend fun awaitGPUCompletion() {
        directContext.submit(syncCpu = true)
    }
}

/** Reuses the GLX pbuffer and framebuffer API behind Skiko 0.150.2's Swing renderer. */
internal class OpenGLGraphicsContext : GraphicsContext {
    private val api = LegacySwingOffscreenApi("LinuxOpenGLSwingRedrawer")
    private val contextPointer = api.field("offScreenContextPtr").getLong(api.renderer)
    private val buffer = api.field("offScreenBufferPtr")
    private val makeBuffer = api.nativeMethod("makeOffScreenBuffer")
    private val startRendering = api.nativeMethod("startRendering")
    private val finishRendering = api.nativeMethod("finishRendering")
    private val createTexture = api.nativeMethod("createAndBindTexture")
    private val getFramebuffer = api.nativeMethod("getFboId")
    private val disposeTexture = api.nativeMethod("unbindAndDisposeTexture")
    private var directContext: DirectContext? = null

    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T {
        val bufferPointer = api.call(makeBuffer, contextPointer, buffer.getLong(api.renderer), width, height) as Long
        check(bufferPointer != 0L) { "Cannot allocate offscreen OpenGL buffer" }
        buffer.setLong(api.renderer, bufferPointer)
        api.call(startRendering, contextPointer, bufferPointer)
        try {
            val context = directContext ?: DirectContext.makeGL().also { directContext = it }
            val texturePointer = api.call(createTexture, width, height) as Long
            check(texturePointer != 0L) { "Cannot allocate offscreen OpenGL texture" }
            try {
                val framebuffer = api.call(getFramebuffer, texturePointer) as Int
                // As in current's OpenGLOffscreenContext, keep the GL context current on this thread
                // until the entire suspend action finishes, including all GPU-completion waits.
                val callerContext = currentCoroutineContext().minusKey(ContinuationInterceptor)
                return BackendRenderTarget.makeGL(
                    width, height, 0, 8, framebuffer, FramebufferFormat.GR_GL_RGBA8
                ).use { target ->
                    context.makeSurface(target, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888)
                        .use { surface -> runBlocking(callerContext) { action(surface) } }
                }
            } finally {
                api.call(disposeTexture, texturePointer)
            }
        } finally {
            api.call(finishRendering, contextPointer)
        }
    }

    override suspend fun awaitGPUCompletion() {
        checkNotNull(directContext).submit(syncCpu = true)
    }
}

/**
 * Access only the resource allocation/native calls of the legacy redrawers, never redraw()/paint().
 * Construction allocates their native resources but does not create a window. Like current's
 * offscreen contexts, the renderer lives for the benchmark process lifetime.
 * Reflective members are preserved by the application's ProGuard rules.
 */
private class LegacySwingOffscreenApi(name: String) {
    private val type = Class.forName("org.jetbrains.skiko.swing.$name")
    val renderer: Any

    init {
        val propertiesType = Class.forName("org.jetbrains.skiko.swing.SwingLayerProperties")
        val properties = Proxy.newProxyInstance(propertiesType.classLoader, arrayOf(propertiesType)) { proxy, method, args ->
            when (method.name) {
                "getWidth", "getHeight" -> 1
                "getAdapterPriority" -> GpuPriority.Auto
                "getGpuResourceCacheLimit" -> -1L
                "toString" -> "BenchmarkOffscreenProperties"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> error("Offscreen initialization must not access ${method.name}")
            }
        }
        val delegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                error("Benchmarks render directly into an offscreen surface, not through Swing")
            }
        }
        renderer = try {
            type.getDeclaredConstructor(propertiesType, SkikoRenderDelegate::class.java, SkiaLayerAnalytics::class.java)
                .newInstance(properties, delegate, SkiaLayerAnalytics.Empty)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    fun nativeMethod(name: String): Method = type.declaredMethods
        .single { it.name == name && Modifier.isNative(it.modifiers) }
        .apply { isAccessible = true }

    fun call(method: Method, vararg args: Any): Any? = try {
        method.invoke(renderer, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
