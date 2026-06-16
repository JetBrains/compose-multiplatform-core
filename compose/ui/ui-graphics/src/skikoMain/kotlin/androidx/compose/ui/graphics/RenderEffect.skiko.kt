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

package androidx.compose.ui.graphics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.platform.PlatformGraphicsRegistry
import androidx.compose.ui.graphics.platform.PlatformRenderEffect
import org.jetbrains.skia.ImageFilter

/**
 * Intermediate rendering step used to render drawing commands with a corresponding
 * visual effect. A [RenderEffect] can be configured on a [GraphicsLayerScope]
 * and will be applied when drawn.
 */
@Immutable
actual sealed class RenderEffect actual constructor() {

    @InternalComposeUiApi
    abstract val platformRenderEffect: PlatformRenderEffect

    @Deprecated(
        message = "Use [RenderEffect.skiaImageFilter] extension instead",
        replaceWith = ReplaceWith("skiaImageFilter", "androidx.compose.ui.graphics.skiaImageFilter"),
        level = DeprecationLevel.ERROR,
    )
    @Suppress("DEPRECATION")
    @OptIn(InternalComposeUiApi::class)
    fun asSkiaImageFilter(): ImageFilter =
        SkikoGraphicsCompatRegistry.requireCurrent().imageFilter(platformRenderEffect)

    /**
     * Capability query to determine if the particular platform supports the [RenderEffect]. Not
     * all platforms support all render effects
     */
    actual open fun isSupported(): Boolean = true
}

@Immutable
@OptIn(InternalComposeUiApi::class)
internal class CustomPlatformRenderEffect(
    override val platformRenderEffect: PlatformRenderEffect,
) : RenderEffect()

/** Wraps a platform render-effect binding into a [RenderEffect]. */
@InternalComposeUiApi
fun PlatformRenderEffect.asComposeRenderEffect(): RenderEffect = CustomPlatformRenderEffect(this)

@Immutable
actual class BlurEffect actual constructor(
    private val renderEffect: RenderEffect?,
    private val radiusX: Float,
    private val radiusY: Float,
    private val edgeTreatment: TileMode
) : RenderEffect() {

    @InternalComposeUiApi
    override val platformRenderEffect: PlatformRenderEffect by lazy {
        PlatformGraphicsRegistry.requireCurrent()
            .createBlurRenderEffect(renderEffect, radiusX, radiusY, edgeTreatment)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlurEffect) return false

        if (radiusX != other.radiusX) return false
        if (radiusY != other.radiusY) return false
        if (edgeTreatment != other.edgeTreatment) return false
        if (renderEffect != other.renderEffect) return false

        return true
    }

    override fun hashCode(): Int {
        var result = renderEffect?.hashCode() ?: 0
        result = 31 * result + radiusX.hashCode()
        result = 31 * result + radiusY.hashCode()
        result = 31 * result + edgeTreatment.hashCode()
        return result
    }

    override fun toString(): String {
        return "BlurEffect(renderEffect=$renderEffect, radiusX=$radiusX, radiusY=$radiusY, " +
            "edgeTreatment=$edgeTreatment)"
    }

    companion object
}

@Immutable
actual class OffsetEffect actual constructor(
    private val renderEffect: RenderEffect?,
    private val offset: Offset
) : RenderEffect() {

    @InternalComposeUiApi
    override val platformRenderEffect: PlatformRenderEffect by lazy {
        PlatformGraphicsRegistry.requireCurrent()
            .createOffsetRenderEffect(renderEffect, offset)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OffsetEffect) return false

        if (renderEffect != other.renderEffect) return false
        if (offset != other.offset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = renderEffect?.hashCode() ?: 0
        result = 31 * result + offset.hashCode()
        return result
    }

    override fun toString(): String {
        return "OffsetEffect(renderEffect=$renderEffect, offset=$offset)"
    }
}
