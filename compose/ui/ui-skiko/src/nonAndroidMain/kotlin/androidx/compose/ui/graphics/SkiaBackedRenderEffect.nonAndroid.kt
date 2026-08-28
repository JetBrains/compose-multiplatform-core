/*
 * Copyright 2021 The Android Open Source Project
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
@file:JvmName("SkiaBackedRenderEffect_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.platform.PlatformRenderEffect
import kotlin.jvm.JvmName
import org.jetbrains.skia.ImageFilter

internal class SkikoRenderEffect(
    val imageFilter: ImageFilter,
) : PlatformRenderEffect

/** Convert the [org.jetbrains.skia.ImageFilter] instance into a Compose-compatible [RenderEffect] */
fun ImageFilter.asComposeRenderEffect(): RenderEffect =
    SkikoRenderEffect(this).asComposeRenderEffect()

/**
 * Provides access to the underlying [org.jetbrains.skia.ImageFilter] instance.
 *
 * It throws an exception if accessed on unsupported types.
 */
val RenderEffect.skiaImageFilter: ImageFilter
    get() = platformRenderEffect.skiaImageFilter

/** Provides access to the underlying [org.jetbrains.skia.ImageFilter] of a platform binding. */
internal val PlatformRenderEffect.skiaImageFilter: ImageFilter
    get() {
        require(this is SkikoRenderEffect) {
            "Extracting the Skia image filter reference is only supported from RenderEffects " +
                "created by the registered Skiko implementation " +
                "(registerSkikoComposeImplementation()), but the binding was " +
                "${this::class}"
        }
        return imageFilter
    }
