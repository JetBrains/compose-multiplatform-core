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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.mpp.demo.webgl

import androidx.compose.ui.unit.IntSize
import kotlin.js.ExperimentalWasmJsInterop
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.impl.use
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.khronos.webgl.WebGLTexture
import org.w3c.dom.HTMLCanvasElement

// See https://registry.khronos.org/OpenGL/api/GL/glcorearb.h
// #define GL_RGBA8 0x8058
internal const val GL_RGBA8 = 0x8058

/**
 * A helper wrapper for values associated with the WebGL texture.
 *
 * @param texture - the WebGL texture in the same WebGL context as Skiko
 * @param textureId - the id that Emscripten associates with the [texture]
 * @param image - Skiko Image which "adopted" the [texture]
 */
internal class AdoptedGlTexture(
    val texture: WebGLTexture,
    val textureId: Int,
    val image: Image,
)

/**
 * @param context - the rendering context of Skiko canvas
 * @param size - the size of the texture
 * @return - a wrapper [AdoptedGlTexture]
 */
internal fun WebGLRenderingContext.adoptNewTexture(
    context: DirectContext,
    size: IntSize,
): AdoptedGlTexture {
    val texture = createTexture() ?: error("gl.createTexture() returned null")
    bindTexture(TEXTURE_2D, texture)
    texImage2D(TEXTURE_2D, 0, RGBA, size.width, size.height, 0, RGBA, UNSIGNED_BYTE, null)
    texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
    texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
    texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
    texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
    bindTexture(TEXTURE_2D, null)

    val textureId = pushTexture(texture)
    var ownershipTransferred = false
    try {
        val image = BackendTexture.makeGL(
            width = size.width,
            height = size.height,
            isMipmapped = false,
            textureId = textureId,
            textureTarget = TEXTURE_2D,
            textureFormat = GL_RGBA8,
        ).use { backendTexture ->
            Image.adoptTextureFrom(
                context,
                backendTexture,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorAlphaType.PREMUL,
            )
        }
        ownershipTransferred = true
        return AdoptedGlTexture(texture, textureId, image)
    } finally {
        if (!ownershipTransferred) {
            unregisterTexture(textureId)
            deleteTexture(texture)
        }
    }
}

internal fun webGl2ContextOf(canvas: HTMLCanvasElement): WebGLRenderingContext? =
    js("canvas.getContext('webgl2')")
