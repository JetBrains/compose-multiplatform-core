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

/** `GL_RGBA8`, the sized format Skia expects for an `RGBA` / `UNSIGNED_BYTE` texture. */
internal const val GL_RGBA8 = 0x8058

/**
 * A WebGL texture that belongs to Skia now.
 *
 * [texture] is kept only so that it can be re-attached to a framebuffer; it must not be deleted, and
 * [textureId] must not be unregistered. Closing [image] does both.
 */
internal class AdoptedGlTexture(
    val texture: WebGLTexture,
    val textureId: Int,
    val image: Image,
)

/**
 * Allocates an `RGBA8` texture of [size], publishes it in Emscripten's texture table and hands it to
 * Skia. Once [Image.adoptTextureFrom] returns, the GL texture belongs to [context].
 *
 * This is the whole trick behind both texture adoption demos: whoever renders into
 * [AdoptedGlTexture.texture] afterwards — hand-written shaders or a third-party engine — is drawing
 * straight into an image Skia can sample, with no pixel copies in between.
 */
internal fun WebGLRenderingContext.adoptNewTexture(
    context: DirectContext,
    size: IntSize,
): AdoptedGlTexture {
    val texture = createTexture() ?: error("gl.createTexture() returned null")
    bindTexture(TEXTURE_2D, texture)
    texImage2D(TEXTURE_2D, 0, RGBA, size.width, size.height, 0, RGBA, UNSIGNED_BYTE, null)
    // No mipmaps and plain LINEAR filtering keep Skia on the "just sample the texture" path, so that
    // re-rendering into the texture shows up immediately instead of serving a cached copy.
    texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
    texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
    texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
    texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
    bindTexture(TEXTURE_2D, null)

    val textureId = pushTexture(texture)
    var ownershipTransferred = false
    try {
        // The descriptor is closed as soon as the image exists; the texture it described is Skia's
        // from that point on.
        val image = BackendTexture.makeGL(
            size.width,
            size.height,
            /* isMipmapped = */ false,
            textureId,
            /* textureTarget = */ TEXTURE_2D,
            /* textureFormat = */ GL_RGBA8,
        ).use { backendTexture ->
            // BOTTOM_LEFT because the scene is rendered into a framebuffer, and PREMUL because the
            // producers write premultiplied colors. Together they are what makes the texture blend
            // correctly with the Compose content behind and in front of it.
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
            // Skia never took the texture, so both the table entry and the texture are ours.
            unregisterTexture(textureId)
            deleteTexture(texture)
        }
    }
}

/**
 * Skiko already created a `"webgl2"` context for this canvas, and a canvas never hands out a second
 * context — so this returns the exact context Skia renders with. WebGL has no share groups, which
 * makes this the only context whose textures Skia is allowed to touch.
 */
internal fun webGl2ContextOf(canvas: HTMLCanvasElement): WebGLRenderingContext? =
    js("canvas.getContext('webgl2')")
