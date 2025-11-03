/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.xr.glimmer.stack

import androidx.collection.MutableScatterMap
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.xr.glimmer.DepthNode
import androidx.xr.glimmer.GlimmerTheme.Companion.LocalGlimmerTheme

/** Receiver scope used by item content in [VerticalStack]. */
@Stable
public sealed interface StackItemScope {

    /**
     * Adds a decoration shape for this item, which is used in transition animations (to clip items
     * behind) and depth effect. Each item must have its decoration shape set, as otherwise the
     * clipping and depth will not be applied.
     *
     * @param shape The shape of this stack item.
     */
    public fun Modifier.itemDecoration(shape: Shape): Modifier =
        this then ItemDecorationElement(this@StackItemScope as StackItemScopeImpl, shape)
}

internal class ItemDecorationElement
internal constructor(private val stackItemScope: StackItemScopeImpl, private val shape: Shape) :
    ModifierNodeElement<ItemDecorationNode>() {

    override fun create(): ItemDecorationNode = ItemDecorationNode(stackItemScope, shape)

    override fun update(node: ItemDecorationNode) {
        node.update(stackItemScope, shape)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemDecorationElement) return false

        if (stackItemScope != other.stackItemScope) return false
        if (shape != other.shape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stackItemScope.hashCode()
        result = 31 * result + shape.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "stackItemDecoration"
        properties["shape"] = shape
    }
}

// TODO(b/413429531): add support for shape bounds.
internal class StackItemDecoration(internal var shape: Shape, internal var size: Size)

internal class StackItemScopeImpl : StackItemScope {

    internal val decorations = MutableScatterMap<Any, StackItemDecoration>()

    // TODO(b/413429531): remove the first decoration getter once multiple shapes are supported.
    internal fun firstDecoration(): StackItemDecoration? {
        var decoration: StackItemDecoration? = null
        decorations.forEachValue {
            decoration = it
            return@forEachValue
        }
        return decoration
    }
}

internal class ItemDecorationNode(
    private var stackItemScope: StackItemScopeImpl,
    private var shape: Shape,
) : DelegatingNode(), LayoutAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var depthNode: DepthNode? = null
    private var size = Size.Zero

    override fun onAttach() {
        depthNode = delegate(DepthNode(currentValueOfDepth(), shape))
    }

    override fun onRemeasured(size: IntSize) {
        // TODO(b/413429531): add support for shape bounds.
        this.size = size.toSize()
        updateShapeInItemScope()
    }

    override fun onDetach() {
        stackItemScope.decorations.remove(this)
        depthNode?.let { undelegate(it) }
    }

    fun update(stackItemScope: StackItemScopeImpl, shape: Shape) {
        depthNode?.update(currentValueOfDepth(), shape)
        if (this.stackItemScope != stackItemScope || this.shape != shape) {
            this.stackItemScope = stackItemScope
            this.shape = shape
            updateShapeInItemScope()
        }
    }

    private fun updateShapeInItemScope() {
        val decoration = stackItemScope.decorations[this]
        if (decoration != null) {
            decoration.shape = shape
            decoration.size = size
            // TODO(b/413429531): make sure a shape change invalidates layout and updates clipping.
        } else {
            stackItemScope.decorations.put(this, StackItemDecoration(shape, size))
        }
    }

    private fun currentValueOfDepth() = currentValueOf(LocalGlimmerTheme).depthLevels.level2
}
