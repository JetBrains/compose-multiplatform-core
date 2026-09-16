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

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.platform.PlatformRenderEffect
import org.jetbrains.skia.ImageFilter

/**
 * TEMPORARY compatibility bridge for deprecated Skia-returning **members** that cannot be expressed
 * as relocatable extension functions (and therefore cannot be moved to `:compose:ui:ui-skiko`):
 * currently [RenderEffect.asSkiaImageFilter].
 *
 * The implementation is registered by `:compose:ui:ui-skiko` so the real Skia work still lives there.
 * This interface exists only to keep those already-deprecated members working and is expected to be
 * removed when they are dropped — prefer the [skiaImageFilter] extension instead.
 */
@Deprecated("It's used only for deprecated compatibility support and will be removed in the future.")
@InternalComposeUiApi
interface SkikoGraphicsCompat {
    fun imageFilter(renderEffect: PlatformRenderEffect): ImageFilter
}

@Deprecated("It's used only for deprecated compatibility support and will be removed in the future.")
@Suppress("DEPRECATION")
@InternalComposeUiApi
object SkikoGraphicsCompatRegistry {
    private var current: SkikoGraphicsCompat? = null

    fun register(value: SkikoGraphicsCompat) {
        current = value
    }

    fun requireCurrent(): SkikoGraphicsCompat =
        current ?: error("No Skiko graphics compat is registered.")

    fun clear() {
        current = null
    }
}
