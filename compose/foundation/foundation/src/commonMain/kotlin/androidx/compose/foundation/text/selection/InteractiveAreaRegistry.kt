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

package androidx.compose.foundation.text.selection

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.util.fastForEach

/**
 * Registry that tracks the layout bounds of interactive (clickable) components in the tree.
 *
 * Used by [SelectionManager] to exclude text inside interactive components from selection,
 * so that e.g. button labels are not selectable even when a root [SelectionContainer] is active.
 *
 * Only active when provided via [LocalInteractiveAreaRegistry]. On platforms that don't set this
 * (Android, Desktop), the local is null and no exclusion logic runs.
 */
internal interface InteractiveAreaRegistry {
    /**
     * Register a lambda that returns the current [LayoutCoordinates] of an interactive component.
     * Returns an unregister function that must be called when the component is detached.
     */
    fun register(getCoords: () -> LayoutCoordinates?): () -> Unit

    /**
     * Returns true if the given [LayoutCoordinates] (of a selectable text node) fall inside
     * any registered interactive area.
     */
    fun isInsideInteractiveArea(selectableCoords: LayoutCoordinates): Boolean
}

internal class InteractiveAreaRegistryImpl : InteractiveAreaRegistry {
    private val entries = mutableListOf<() -> LayoutCoordinates?>()

    override fun register(getCoords: () -> LayoutCoordinates?): () -> Unit {
        entries.add(getCoords)
        return { entries.remove(getCoords) }
    }

    override fun isInsideInteractiveArea(selectableCoords: LayoutCoordinates): Boolean {
        if (!selectableCoords.isAttached) return false
        entries.fastForEach { getCoords ->
            val interactiveCoords = getCoords() ?: return@fastForEach
            if (!interactiveCoords.isAttached) return@fastForEach
            // Transform the selectable's origin into the interactive component's local space.
            // If it lands within [0..width] x [0..height], the selectable is inside the
            // interactive area.
            val originInInteractive =
                interactiveCoords.localPositionOf(selectableCoords, Offset.Zero)
            if (
                originInInteractive.x >= 0f &&
                    originInInteractive.y >= 0f &&
                    originInInteractive.x <= interactiveCoords.size.width &&
                    originInInteractive.y <= interactiveCoords.size.height
            ) {
                return true
            }
        }
        return false
    }
}

/**
 * CompositionLocal providing an [InteractiveAreaRegistry].
 *
 * Null by default — no interactive-area exclusion runs unless explicitly provided.
 * Provided by the web platform's root selection wrapper.
 *
 * Cleared (set to null) by the public [SelectionContainer] overload so that explicitly
 * opted-in containers restore the standard "everything is selectable" behavior.
 */
internal val LocalInteractiveAreaRegistry = compositionLocalOf<InteractiveAreaRegistry?> { null }
