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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.text.holder
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextInputContainer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.uikit.LocalTextInputContainer
import androidx.compose.ui.unit.Density

internal data class SelectionContainerInputElement(
    private val manager: SelectionManager
) : ModifierNodeElement<SelectionContainerInputNode>() {

    override fun create() = SelectionContainerInputNode(manager)

    override fun update(node: SelectionContainerInputNode) {
        node.update(manager)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "selectionContainerInput"
        properties["manager"] = manager
    }
}

@OptIn(InternalComposeUiApi::class)
internal class SelectionContainerInputNode(
    private var manager: SelectionManager
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode {

    private var delegate = SelectionContainerInputDelegate(manager)

    private var container: TextInputContainer? = null

    private var bounds: Rect? = null

    private var density: Density? = null

    override fun onAttach() {
        onObservedReadsChanged()
    }

    override fun onDetach() {
        removeTextInput()
        container = null
        bounds = null
        density = null
    }

    fun update(manager: SelectionManager) {
        if (this.manager === manager) return
        removeTextInput()
        this.manager.holder = null
        this.manager = manager
        delegate = SelectionContainerInputDelegate(manager)
        createTextInput()
    }

    @OptIn(InternalComposeUiApi::class)
    override fun onObservedReadsChanged() {
        var container: TextInputContainer? = null
        var density: Density? = null
        observeReads {
            container = currentValueOf(LocalTextInputContainer)
            density = currentValueOf(LocalDensity)
        }

        if (container != this.container) {
            this.container = container
            this.density = density
            createTextInput()
        } else if (density != this.density) {
            this.density = density
            updateRect()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        bounds = coordinates.boundsInRoot()
        updateRect()
    }

    private fun createTextInput() {
        removeTextInput()
        manager.holder = container?.createSelectionContainer(delegate)
        updateRect()
    }

    private fun removeTextInput() {
        manager.holder?.remove()
        manager.holder = null
    }

    private fun updateRect() {
        val holder = manager.holder ?: return
        val bounds = bounds ?: return
        holder.setRect(bounds)
    }
}

@OptIn(InternalComposeUiApi::class)
private class SelectionContainerInputDelegate(
    private val manager: SelectionManager
) : TextInputContainer.Delegate {
    private val contextTextAndSelection by derivedStateOf { manager.contextTextAndSelection() }

    /** A selection container never starts an input session of its own. */
    override val editorToken: Any? = null

    override val isFocused: Boolean = false

    override val imeOptions: ImeOptions = ImeOptions.Default

    override val markedTextRange: TextRange? = null

    override val text: String
        get() = contextTextAndSelection?.first?.text.orEmpty()

    override val selectionTextRange: TextRange
        get() = contextTextAndSelection?.second ?: TextRange.Zero

    override fun insertText(text: String) = Unit

    override fun replaceRange(range: TextRange, text: String) = Unit

    override fun deleteBackward() = Unit

    override fun setSelectedText(range: TextRange?) = Unit

    override fun setMarkedText(markedText: String?, selectedRange: TextRange) = Unit

    override fun unmarkText() = Unit
}

private fun SelectionManager.contextTextAndSelection(): Pair<AnnotatedString, TextRange>? {
    if (!isNonEmptySelection()) return null
    if (containerLayoutCoordinates?.isAttached != true) return null
    return getContextTextAndSelection()
}
