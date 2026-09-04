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
import androidx.compose.ui.graphics.layer.SkikoGraphicsLayer
import androidx.compose.ui.graphics.platform.PlatformGraphicsContext
import androidx.compose.ui.graphics.platform.PlatformGraphicsLayer
import androidx.compose.ui.graphics.platform.PlatformGraphicsRegistry
import org.jetbrains.skiko.node.RenderNode
import org.jetbrains.skiko.node.RenderNodeContext

@InternalComposeUiApi
class SkiaGraphicsContext(
    measureDrawBounds: Boolean = false,
    snapshotCache: Boolean = true,
) : PlatformGraphicsContext() {
    init {
        PlatformGraphicsRegistry.checkIfRegistered(SkikoGraphics)
    }

    private val renderNodeContext = RenderNodeContext(measureDrawBounds, snapshotCache)

    override fun close() {
        super.close()
        renderNodeContext.close()
    }

    override fun setLightingInfo(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        ambientShadowAlpha: Float,
        spotShadowAlpha: Float,
    ) {
        super.setLightingInfo(centerX, centerY, centerZ, radius, ambientShadowAlpha, spotShadowAlpha)
        renderNodeContext.setLightingInfo(
            centerX,
            centerY,
            centerZ,
            radius,
            ambientShadowAlpha,
            spotShadowAlpha,
        )
    }

    override fun createPlatformGraphicsLayer(): PlatformGraphicsLayer {
        val renderNode = RenderNode(renderNodeContext)
        return SkikoGraphicsLayer(renderNode)
    }
}
