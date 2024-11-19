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

package androidx.compose.material3.adaptive.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

internal expect fun Modifier.systemGestureExclusion(): Modifier

internal data class MinTouchTargetSizeElement(val size: Dp) :
    ModifierNodeElement<MinTouchTargetSizeNode>() {
    private val inspectorInfo = debugInspectorInfo {
        name = "minTouchTargetSize"
        properties["size"] = size
    }

    override fun create(): MinTouchTargetSizeNode {
        return MinTouchTargetSizeNode(size)
    }

    override fun update(node: MinTouchTargetSizeNode) {
        node.size = size
    }

    override fun InspectorInfo.inspectableProperties() {
        inspectorInfo()
    }
}

internal class MinTouchTargetSizeNode(var size: Dp) : ParentDataModifierNode, Modifier.Node() {
    override fun Density.modifyParentData(parentData: Any?) =
        ((parentData as? PaneScaffoldParentData) ?: PaneScaffoldParentData()).also {
            it.minTouchTargetSize = size
        }
}
