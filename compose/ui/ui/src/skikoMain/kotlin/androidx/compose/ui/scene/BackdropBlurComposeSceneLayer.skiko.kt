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

package androidx.compose.ui.scene

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaImageFilter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import org.jetbrains.skia.Canvas.SaveLayerRec

/**
 * A [ComposeSceneLayer] that can blur the content drawn behind it.
 *
 * Only layers that share a canvas with the content behind them implement it. Layers backed by a
 * separate component or window, like the desktop Swing and window layers, can't read that content
 * and draw without the blur, as documented for
 * [androidx.compose.ui.window.DialogProperties.blurBehindRadius].
 */
internal interface BackdropBlurComposeSceneLayer : ComposeSceneLayer {
    /** Blur radius for the content drawn behind this layer. Zero disables the blur. */
    var backdropBlurRadius: Dp
}

internal val Dp.isBackdropBlurEnabled: Boolean
    get() = isSpecified && value > 0f

/**
 * Blurs everything already drawn on this canvas, in place, by [radius].
 *
 * Nothing is allocated when [radius] doesn't enable the blur, so it's safe to call every frame.
 */
internal fun Canvas.blurBackdrop(radius: Dp, density: Density) {
    if (!radius.isBackdropBlurEnabled) return
    val radiusPx = with(density) { radius.toPx() }
    val blur = BlurEffect(radiusPx, radiusPx, TileMode.Clamp).skiaImageFilter
    skiaCanvas.saveLayer(SaveLayerRec(backdrop = blur))
    skiaCanvas.restore()
}
