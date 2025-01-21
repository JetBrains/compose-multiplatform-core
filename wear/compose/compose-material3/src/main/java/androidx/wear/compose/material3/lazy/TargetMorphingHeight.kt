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

package androidx.wear.compose.material3.lazy

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import org.jetbrains.annotations.TestOnly

/**
 * This modifier provides the height of the target composable to the [scrollTransform] during a
 * morph transition and represents minimum height of the item when morphed.
 *
 * Should be applied to a single child element or none at all (in which case, the morph effect is
 * disabled). When applied to multiple child elements, the last placed child's height we be used for
 * morphing.
 *
 * @sample androidx.wear.compose.material3.samples.TransformingLazyColumnTargetMorphingHeightSample
 * @param scope The [TransformingLazyColumnItemScope] provides access to the item's index and key.
 */
fun Modifier.targetMorphingHeight(
    @Suppress("UNUSED_PARAMETER") scope: TransformingLazyColumnItemScope,
): Modifier = this then TargetMorphingHeightProviderModifierElement()

@TestOnly
internal fun Modifier.minMorphingHeightConsumer(
    onMinMorphingHeightChanged: (Int?) -> Unit
): Modifier = this then TargetMorphingHeightConsumerModifierElement(onMinMorphingHeightChanged)

internal class TargetMorphingHeightConsumerModifierElement(
    val onMinMorphingHeightChanged: (Int?) -> Unit
) : ModifierNodeElement<TargetMorphingHeightConsumerModifierNode>() {
    override fun create() = TargetMorphingHeightConsumerModifierNode(onMinMorphingHeightChanged)

    override fun update(node: TargetMorphingHeightConsumerModifierNode) {
        node.onMinMorphingHeightChanged = onMinMorphingHeightChanged
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "TargetMorphingHeightConsumerModifierElement"
        properties["onMinMorphingHeightChanged"] = onMinMorphingHeightChanged
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetMorphingHeightConsumerModifierElement) return false
        return onMinMorphingHeightChanged === other.onMinMorphingHeightChanged
    }

    override fun hashCode(): Int {
        return onMinMorphingHeightChanged.hashCode()
    }
}

internal class TargetMorphingHeightConsumerModifierNode(
    var onMinMorphingHeightChanged: (Int?) -> Unit
) : Modifier.Node(), TraversableNode {
    override val traverseKey = TargetMorphingHeightTraversalKey
}

private const val TargetMorphingHeightTraversalKey = "TargetMorphingHeight"

private class TargetMorphingHeightProviderModifierElement :
    ModifierNodeElement<TargetMorphingHeightProviderModifierNode>() {
    override fun create(): TargetMorphingHeightProviderModifierNode =
        TargetMorphingHeightProviderModifierNode()

    override fun update(node: TargetMorphingHeightProviderModifierNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "TargetMorphingHeightProviderModifierElement"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetMorphingHeightConsumerModifierElement) return false
        return true
    }

    override fun hashCode(): Int {
        return 42
    }
}

private class TargetMorphingHeightProviderModifierNode :
    Modifier.Node(), TraversableNode, LayoutAwareModifierNode {
    override val traverseKey = TargetMorphingHeightTraversalKey

    private fun reportMinMorphingHeight(height: Int) {
        traverseAncestors(traverseKey) {
            if (it is TargetMorphingHeightConsumerModifierNode) {
                it.onMinMorphingHeightChanged(height)
                false
            } else {
                true
            }
        }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        reportMinMorphingHeight(coordinates.size.height)
    }

    override fun onRemeasured(size: IntSize) {
        reportMinMorphingHeight(size.height)
    }
}
