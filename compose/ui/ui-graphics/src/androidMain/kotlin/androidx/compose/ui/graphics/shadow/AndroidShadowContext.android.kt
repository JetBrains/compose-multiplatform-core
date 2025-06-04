/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.graphics.shadow

import androidx.collection.MutableScatterMap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private typealias DropShadowCache =
    MutableScatterMap<AndroidShadowContext.DropShadowKey, DropShadowRenderer>

private typealias InnerShadowCache =
    MutableScatterMap<AndroidShadowContext.InnerShadowKey, InnerShadowRenderer>

/** Create a new [ShadowContext] */
fun ShadowContext(): ShadowContext = AndroidShadowContext()

private class AndroidShadowContext :
    ShadowContext, DropShadowRendererProvider, InnerShadowRendererProvider {

    private var dropShadowCache: DropShadowCache? = null
    private var innerShadowCache: InnerShadowCache? = null
    private var dropShadowKey: DropShadowKey? = null
    private var innerShadowKey: InnerShadowKey? = null

    private fun obtainDropShadowCache(): DropShadowCache =
        dropShadowCache ?: DropShadowCache().also { dropShadowCache = it }

    private fun obtainDropShadowKey(): DropShadowKey =
        dropShadowKey ?: DropShadowKey().also { dropShadowKey = it }

    private fun obtainInnerShadowCache(): InnerShadowCache =
        innerShadowCache ?: InnerShadowCache().also { innerShadowCache = it }

    private fun obtainInnerShadowKey(): InnerShadowKey =
        innerShadowKey ?: InnerShadowKey().also { innerShadowKey = it }

    // Class to represent a lookup key for cached ShadowRenderer implementations
    // Note: Take care not to mutate a key which is used in the cache maps as the hash code depends
    // on its values.
    data class ShadowKey(
        var shape: Shape = RectangleShape,
        var size: Size = Size.Zero,
        var layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        var density: Float = 1f,
    )

    /**
     * Lookup key for DropShadow based ShadowRenderer implementations var to support avoiding
     * allocations for local lookups
     */
    data class DropShadowKey(
        var shadowKey: ShadowKey = ShadowKey(),
        var dropShadow: DropShadow? = null,
    )

    /**
     * Lookup key for InnerShadow based ShadowRenderer implementations var to support avoiding
     * allocations for local lookups
     */
    data class InnerShadowKey(
        var shadowKey: ShadowKey = ShadowKey(),
        var innerShadow: InnerShadow? = null,
    )

    override fun obtainDropShadowRenderer(
        shape: Shape,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
        dropShadow: DropShadow,
    ): DropShadowRenderer {
        synchronized(this) {
            val key =
                obtainDropShadowKey().apply {
                    this.shadowKey.apply {
                        this.shape = shape
                        this.size = size
                        this.layoutDirection = layoutDirection
                        this.density = density.density
                    }
                    this.dropShadow = dropShadow
                }
            var renderer = obtainDropShadowCache()[key]
            if (renderer == null) {
                val outline = shape.createOutline(size, layoutDirection, density)
                renderer = DropShadowRenderer(dropShadow, outline)
                // Note it is important to deep copy the key here as we use a mutable tmp
                // key to save on allocations for lookups of previously created entries
                obtainDropShadowCache()[key.copy(shadowKey = key.shadowKey.copy())] = renderer
            }
            return renderer
        }
    }

    override fun obtainInnerShadowRenderer(
        shape: Shape,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
        innerShadow: InnerShadow,
    ): InnerShadowRenderer {
        synchronized(this) {
            val key =
                obtainInnerShadowKey().apply {
                    this.shadowKey.apply {
                        this.shape = shape
                        this.size = size
                        this.layoutDirection = layoutDirection
                        this.density = density.density
                    }
                    this.innerShadow = innerShadow
                }
            var renderer = obtainInnerShadowCache()[key]
            if (renderer == null) {
                val outline = shape.createOutline(size, layoutDirection, density)
                renderer = InnerShadowRenderer(innerShadow, outline)
                // Note it is important to deep copy the key here as we use a mutable tmp
                // key to save on allocations for lookups of previously created entries
                obtainInnerShadowCache()[key.copy(shadowKey = key.shadowKey.copy())] = renderer
            }
            return renderer
        }
    }

    override fun createDropShadowPainter(shape: Shape, dropShadow: DropShadow): DropShadowPainter =
        DropShadowPainter(shape, dropShadow, this)

    override fun createInnerShadowPainter(
        shape: Shape,
        innerShadow: InnerShadow,
    ): InnerShadowPainter = InnerShadowPainter(shape, innerShadow, this)

    override fun clearCache() {
        synchronized(this) {
            dropShadowCache?.clear()
            dropShadowKey = null
            innerShadowCache?.clear()
            innerShadowKey = null
        }
    }
}
