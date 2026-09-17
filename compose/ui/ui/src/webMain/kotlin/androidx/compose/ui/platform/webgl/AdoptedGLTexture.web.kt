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

package androidx.compose.ui.platform.webgl

import androidx.compose.ui.unit.IntSize
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
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
private const val GL_RGBA8 = 0x8058

/**
 * A WebGL texture that a Skia [Image] has adopted: the image samples the texture directly, so
 * whatever is rendered into the texture is what Compose draws, with no copies in between.
 *
 * @param texture the WebGL texture, living in the same WebGL context as Skia
 * @param textureId the id Emscripten associates with [texture]
 * @param image the Skia image which adopted [texture]
 * @param size the size of [texture], in pixels
 */
internal class AdoptedGLTexture(
    val texture: WebGLTexture,
    val textureId: Int,
    val image: Image,
    val size: IntSize,
) {
    fun dispose() {
        image.close()
        unregisterTexture(textureId)
    }
}

/**
 * Hands [texture] over to Skia as an RGBA8 image, deleting it again if adoption fails.
 *
 * @param context the [DirectContext] Skia renders this canvas with
 * @param size the size of [texture], in pixels
 * @param texture the texture to adopt, already allocated in this context
 */
internal fun WebGLRenderingContext.adoptNewTexture(
    context: DirectContext,
    size: IntSize,
    texture: WebGLTexture,
): AdoptedGLTexture {
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
                context = context,
                backendTexture = backendTexture,
                origin = SurfaceOrigin.BOTTOM_LEFT,
                colorType = ColorType.RGBA_8888,
                alphaType = ColorAlphaType.PREMUL,
            )
        }
        ownershipTransferred = true
        return AdoptedGLTexture(texture, textureId, image, size)
    } finally {
        if (!ownershipTransferred) {
            unregisterTexture(textureId)
            deleteTexture(texture)
        }
    }
}

internal fun webGl2ContextOrNull(canvas: HTMLCanvasElement): WebGLRenderingContext? =
    js("canvas.getContext('webgl2')")

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
// TODO: delete the two helpers below once Skiko exposes them.
//  See https://github.com/JetBrains/skiko/pull/1270
private fun pushTexture(texture: JsAny): Int = pushTexture(org.jetbrains.skiko.GL, texture)

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun unregisterTexture(textureId: Int): Unit =
    unregisterTexture(org.jetbrains.skiko.GL, textureId)

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
// language=js
private fun pushTexture(gl: org.jetbrains.skiko.GLInterface, texture: JsAny): Int =
    js(
        """(function() {
        const textureHandle = gl.getNewId(gl.textures);
        gl.textures[textureHandle] = texture;
        return textureHandle;
    })()"""
    )

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
// language=js
private fun unregisterTexture(gl: org.jetbrains.skiko.GLInterface, textureId: Int): Unit =
    js("(gl.textures[textureId] = null)")
