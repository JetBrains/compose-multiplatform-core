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

package androidx.compose.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.AppEntityDescriptorNode
import androidx.compose.ui.platform.AppEntityDescriptorStore
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.uikit.LocalUIView

@Immutable
internal data class AppEntityReference(
    val typeName: String,
    val id: String,
    val selected: Boolean,
)

/**
 * Associates the modified composable with an app-defined Swift `AppEntity` type.
 *
 * @param typeName The fully qualified Swift AppEntity type name emitted in `Metadata.appintents`.
 * @param id The serialized entity ID accepted by the Swift AppEntity type.
 * @param selected Whether the element represents the currently selected entity.
 */
@Stable
@ExperimentalComposeUiApi
fun Modifier.appEntity(
    typeName: String,
    id: String,
    selected: Boolean = false,
): Modifier = this then AppEntityElement(AppEntityReference(typeName, id, selected))

private data class AppEntityElement(
    private val entity: AppEntityReference,
) : ModifierNodeElement<AppEntityModifierNode>() {
    override fun create() = AppEntityModifierNode(entity)

    override fun update(node: AppEntityModifierNode) {
        node.update(entity)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "appEntity"
        properties["typeName"] = entity.typeName
        properties["id"] = entity.id
        properties["selected"] = entity.selected
    }
}

private class AppEntityModifierNode(
    override var entity: AppEntityReference,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutAwareModifierNode,
    UnplacedAwareModifierNode,
    AppEntityDescriptorNode {

    private lateinit var store: AppEntityDescriptorStore
    private var isPlaced = false

    override fun onAttach() {
        store = AppEntityDescriptorStore.forView(currentValueOf(LocalUIView))
        store.onAttach(this)
    }

    override fun onDetach() {
        isPlaced = false
        store.onDetach(this)
    }

    fun update(entity: AppEntityReference) {
        this.entity = entity
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        isPlaced = coordinates.isAttached
    }

    override fun onUnplaced() {
        isPlaced = false
    }

    override fun boundsInRootOrNull(): Rect? = if (isPlaced) {
        requireLayoutCoordinates()
            .takeIf { it.isAttached }
            ?.boundsInRoot()
            ?.takeIf { it.width > 0f && it.height > 0f }
    } else {
        null
    }
}
