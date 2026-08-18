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

// Skiko ships `pushTexture`/`unregisterTexture` in its Kotlin/Wasm source set only, while the
// Emscripten `GL` handle those functions need is declared in Skiko's shared web source set (as an
// internal API, hence the suppression). Re-implementing the two helpers here on top of that handle
// is what lets this demo live in `webMain` and run on both Kotlin/JS and Kotlin/Wasm.
// TODO: delete this file and use org.jetbrains.skiko.pushTexture/unregisterTexture once Skiko
//  exposes them for both web targets.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.mpp.demo.webgl

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import org.jetbrains.skiko.GL
import org.jetbrains.skiko.GLInterface

/**
 * Registers an externally-created `WebGLTexture` in Emscripten's GL texture table.
 *
 * The returned id can be passed to Skia GL APIs that expect a numeric texture id, such as
 * [org.jetbrains.skia.BackendTexture.makeGL]. The texture must belong to the same WebGL context
 * that Skiko is using.
 *
 * This function only creates the Emscripten table entry. If the returned id is passed to a Skia API
 * that takes ownership of the texture, Skia will delete the GL texture through Emscripten and the
 * table entry will be cleared there. If ownership is not transferred to Skia, call
 * [unregisterTexture] when the id is no longer needed to avoid leaking the table entry.
 */
internal fun pushTexture(texture: JsAny): Int = pushTexture(GL, texture)

/**
 * Removes a texture table entry previously created with [pushTexture].
 *
 * This does not delete the underlying `WebGLTexture`; it only releases Skiko/Emscripten's numeric id
 * mapping. Use it only when the id was not handed to a Skia API that takes ownership of the texture.
 */
internal fun unregisterTexture(textureId: Int): Unit = unregisterTexture(GL, textureId)

/**
 * `GL.textures` is the array Emscripten's GL layer indexes with the ids Skia's GL backend speaks,
 * and `getNewId` is how Emscripten itself allocates a free slot in it.
 */
// language=js
private fun pushTexture(gl: GLInterface, texture: JsAny): Int = js(
    """(function() {
        const textureHandle = gl.getNewId(gl.textures);
        gl.textures[textureHandle] = texture;
        return textureHandle;
    })()"""
)

// language=js
private fun unregisterTexture(gl: GLInterface, textureId: Int): Unit =
    js("(gl.textures[textureId] = null)")
