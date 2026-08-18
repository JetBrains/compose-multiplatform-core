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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.mpp.demo.webgl

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import org.jetbrains.skiko.GL
import org.jetbrains.skiko.GLInterface

// TODO: delete this file when we have these declaration in Skiko webMain.
// see https://github.com/JetBrains/skiko/pull/1270

internal fun pushTexture(texture: JsAny): Int = pushTexture(GL, texture)
internal fun unregisterTexture(textureId: Int): Unit = unregisterTexture(GL, textureId)

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
