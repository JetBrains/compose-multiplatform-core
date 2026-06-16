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

package androidx.compose.ui.platform

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.SkikoGraphicsCompat
import androidx.compose.ui.graphics.SkikoGraphicsCompatRegistry
import androidx.compose.ui.graphics.SkikoGraphics
import androidx.compose.ui.graphics.platform.PlatformGraphicsRegistry
import androidx.compose.ui.graphics.platform.PlatformRenderEffect
import androidx.compose.ui.graphics.skiaImageFilter
import androidx.compose.ui.text.platform.PlatformTextRegistry
import androidx.compose.ui.text.SkikoText
import org.jetbrains.skia.ImageFilter

@InternalComposeUiApi
fun registerSkikoComposeImplementation() {
    PlatformGraphicsRegistry.register(SkikoGraphics)
    PlatformTextRegistry.register(SkikoText)
    @Suppress("DEPRECATION")
    SkikoGraphicsCompatRegistry.register(SkikoGraphicsCompatImpl)
}

/**
 * Unregisters the Skiko implementation. Intended for tests to avoid leaking the registered
 * implementation as global state across test cases.
 */
@VisibleForTesting
@InternalComposeUiApi
fun clearSkikoComposeImplementation() {
    PlatformGraphicsRegistry.clear()
    PlatformTextRegistry.clear()
    @Suppress("DEPRECATION")
    SkikoGraphicsCompatRegistry.clear()
}

/**
 * Temporary bridge backing the deprecated Skia-returning members that cannot be relocated to this
 * module (see [SkikoGraphicsCompat]). Delegates to the proper `skiaImageFilter` extension.
 */
@OptIn(InternalComposeUiApi::class)
@Suppress("DEPRECATION")
private object SkikoGraphicsCompatImpl : SkikoGraphicsCompat {
    override fun imageFilter(renderEffect: PlatformRenderEffect): ImageFilter =
        renderEffect.skiaImageFilter
}
