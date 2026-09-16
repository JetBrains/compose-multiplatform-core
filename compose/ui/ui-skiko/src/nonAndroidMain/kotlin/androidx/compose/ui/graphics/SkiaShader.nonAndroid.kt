/*
 * Copyright 2020 The Android Open Source Project
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

@file:OptIn(InternalComposeUiApi::class)
@file:JvmName("SkiaShader_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.platform.PlatformShader
import kotlin.jvm.JvmName
import org.jetbrains.skia.Shader as SkShader

internal class SkikoShader(
    val skiaShader: SkShader,
) : PlatformShader

/** Convert the [org.jetbrains.skia.Shader] instance into a Compose-compatible Shader */
fun SkShader.asComposeShader(): Shader = Shader(SkikoShader(this))

/** Provides access to the underlying [org.jetbrains.skia.Shader] instance. */
val Shader.skiaShader: SkShader
    get() {
        val platform = platformShader
        require(platform is SkikoShader) {
            "Extracting the Skia shader reference is only supported from Shaders created by the " +
                "registered Skiko implementation (registerSkikoComposeImplementation()), but the " +
                "binding was ${platform::class}"
        }
        return platform.skiaShader
    }

internal fun transformSkikoShader(shader: Shader, matrix: Matrix): Shader =
    shader.skiaShader
        .makeWithLocalMatrix(identityMatrix33().apply { setFrom(matrix) })
        .asComposeShader()
