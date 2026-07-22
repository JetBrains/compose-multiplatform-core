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

package androidx.compose.ui.graphics.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.atomicfu.atomic

/**
 * Base for a [GraphicsContext] supplied by the registered graphics backend. It owns the shared
 * lighting state and the active-layer bookkeeping, and defers creation of the platform layer to
 * [createPlatformGraphicsLayer]. ui-graphics depends only on this abstraction, never on a concrete
 * backend.
 */
@InternalComposeUiApi
abstract class PlatformGraphicsContext : GraphicsContext, AutoCloseable {

    private var isClosed = false

    private val activeGraphicsLayersCountState = atomic(0)

    /** Number of [GraphicsLayer]s created by this context that have not yet been released. */
    val activeGraphicsLayersCount: Int
        get() = activeGraphicsLayersCountState.value

    /** Closes the context. Subsequent layer operations are not allowed. */
    override fun close() {
        require(!isClosed) { "GraphicsContext is already closed" }
        isClosed = true
    }

    /**
     * Configures the light source used for shadow rendering. The default implementation is a no-op;
     * backends that support elevation shadows override it.
     */
    open fun setLightingInfo(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        ambientShadowAlpha: Float,
        spotShadowAlpha: Float,
    ) {
        require(!isClosed) { "GraphicsContext is already closed" }
    }

    /** Creates a new [GraphicsLayer] backed by [createPlatformGraphicsLayer] and tracks it. */
    override fun createGraphicsLayer(): GraphicsLayer {
        require(!isClosed) { "GraphicsContext is already closed" }
        activeGraphicsLayersCountState.incrementAndGet()
        return GraphicsLayer(createPlatformGraphicsLayer())
    }

    /** Releases [layer] and stops tracking it against [activeGraphicsLayersCount]. */
    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
        if (!layer.isReleased) {
            activeGraphicsLayersCountState.decrementAndGet()
        }
        layer.release()
    }

    /** Creates the backend-specific [PlatformGraphicsLayer] that backs a [GraphicsLayer]. */
    abstract fun createPlatformGraphicsLayer(): PlatformGraphicsLayer
}
