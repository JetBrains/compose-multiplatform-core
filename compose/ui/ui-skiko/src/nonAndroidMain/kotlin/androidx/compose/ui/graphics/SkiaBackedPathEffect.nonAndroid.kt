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

@file:JvmName("SkiaBackedPathEffect_skikoKt")

package androidx.compose.ui.graphics

import kotlin.jvm.JvmName
import org.jetbrains.skia.PathEffect as SkPathEffect

internal class SkiaBackedPathEffect(
    internal val internalSkiaPathEffect: SkPathEffect,
) : PathEffect

/**
 * Convert the [org.jetbrains.skia.PathEffect] instance into a Compose-compatible PathEffect
 */
fun SkPathEffect.asComposePathEffect(): PathEffect = SkiaBackedPathEffect(this)

/**
 * Obtain a reference the underlying [org.jetbrains.skia.PathEffect] instance.
 *
 * It throws an exception if accessed on unsupported types.
 */
fun PathEffect.asSkiaPathEffect(): SkPathEffect {
    require(this is SkiaBackedPathEffect) {
        "Extracting skia path effect reference is only supported from androidx.compose.ui.graphics.SkiaBackedPathEffect instances but received ${this::class}"
    }
    return internalSkiaPathEffect
}

internal fun StampedPathEffectStyle.toSkiaStampedPathEffectStyle(): SkPathEffect.Style =
    when (this) {
        StampedPathEffectStyle.Morph -> SkPathEffect.Style.MORPH
        StampedPathEffectStyle.Rotate -> SkPathEffect.Style.ROTATE
        StampedPathEffectStyle.Translate -> SkPathEffect.Style.TRANSLATE
        else -> SkPathEffect.Style.TRANSLATE
    }
