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

package androidx.compose.foundation.text

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.text.input.setSelectionCoerced
import androidx.compose.foundation.text.input.internal.TransformedTextFieldState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.uikit.LocalTextInputContainer
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal actual fun Modifier.textFieldOverlay(
    transformedState: TransformedTextFieldState,
    keyboardOptions: KeyboardOptions,
    interactionSource: InteractionSource
): Modifier = this then BasicTextFieldImeOverlayElement(transformedState, keyboardOptions, interactionSource)

private data class BasicTextFieldImeOverlayElement(
    private val transformedState: TransformedTextFieldState,
    private val keyboardOptions: KeyboardOptions,
    private val interactionSource: InteractionSource,
) : ModifierNodeElement<BasicTextFieldImeOverlayNode>() {

    override fun create() = BasicTextFieldImeOverlayNode(transformedState, keyboardOptions, interactionSource)

    override fun update(node: BasicTextFieldImeOverlayNode) {
        node.update(transformedState, keyboardOptions, interactionSource)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "basicTextFieldImeOverlay"
        properties["transformedState"] = transformedState
        properties["keyboardOptions"] = keyboardOptions
        properties["interactionSource"] = interactionSource
    }
}

@OptIn(InternalComposeUiApi::class)
private class BasicTextFieldImeOverlayNode(
    private var transformedState: TransformedTextFieldState,
    keyboardOptions: KeyboardOptions,
    private var interactionSource: InteractionSource,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode {

    private val delegate = BasicTextFieldInputDelegate(
        transformedState = transformedState,
        imeOptions = keyboardOptions.toImeOptions()
    )

    private var container: TextInputContainer? = null

    private var holder: TextInputContainer.Holder? = null
        set(value) {
            field = value
            transformedState.holder = value
        }

    private var bounds: Rect? = null

    private var density: Density? = null

    private var focusObserverJob: Job? = null

    private var stateObserverJob: Job? = null

    override fun onAttach() {
        onObservedReadsChanged()
        observeFocus()
        observeMirroredState()
    }

    override fun onDetach() {
        removeTextInput()
        container = null
        bounds = null
        density = null
    }

    fun update(
        transformedState: TransformedTextFieldState,
        keyboardOptions: KeyboardOptions,
        interactionSource: InteractionSource,
    ) {
        if (this.transformedState !== transformedState) {
            // The platform text input belongs to the text field rather than to the state object it
            // happens to be backed by, so hand the holder over instead of recreating it.
            this.transformedState.holder = null
            this.transformedState = transformedState
            transformedState.holder = holder
            delegate.transformedState = transformedState
            observeMirroredState()
        }
        delegate.imeOptions = keyboardOptions.toImeOptions()

        if (this.interactionSource != interactionSource) {
            this.interactionSource = interactionSource
            observeFocus()
        }
    }

    override fun onObservedReadsChanged() {
        var container: TextInputContainer? = null
        var density: Density? = null
        observeReads {
            container = currentValueOf(LocalTextInputContainer)
            density = currentValueOf(LocalDensity)
        }

        if (container != this.container) {
            removeTextInput()
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
        holder = container?.createTextInput(delegate)
        updateRect()
    }

    private fun removeTextInput() {
        holder?.remove()
        holder = null
    }

    private fun updateRect() {
        val holder = holder ?: return
        val bounds = bounds ?: return
        holder.setRect(bounds)
    }

    private fun observeMirroredState() {
        stateObserverJob?.cancel()
        stateObserverJob = coroutineScope.launch {
            snapshotFlow { transformedState.visualText.let { it.toString() to it.selection } }
                .collect {
                    delegate.refreshValue()
                }
        }
    }

    private fun observeFocus() {
        focusObserverJob?.cancel()
        focusObserverJob = coroutineScope.launch {
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                    else -> return@collect
                }
                delegate.isFocused = focusCount > 0
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private class BasicTextFieldInputDelegate(
    transformedState: TransformedTextFieldState,
    override var imeOptions: ImeOptions
) : TextInputContainer.Delegate {

    var transformedState: TransformedTextFieldState = transformedState
        set(value) {
            field = value
            refreshValue()
        }

    override val editorToken: Any
        get() = transformedState

    override var isFocused: Boolean = false

    private val visualText
        get() = transformedState.visualText

    /**
     * The last known state of the text field.
     */
    override var text: String = visualText.toString()
        private set

    override var selectionTextRange: TextRange = visualText.selection
        private set

    override var markedTextRange: TextRange? = visualText.composition
        private set

    fun refreshValue() {
        val visualText = visualText
        text = visualText.toString()
        selectionTextRange = visualText.selection
        markedTextRange = visualText.composition
    }

    private inline fun edit(block: () -> Unit) {
        block()
        refreshValue()
    }

    override fun insertText(text: String) = edit {
        transformedState.replaceSelectedText(text)
    }

    override fun replaceRange(range: TextRange, text: String) = edit {
        transformedState.replaceText(text, range)
    }

    override fun deleteBackward() = edit {
        val selection = visualText.selection
        if (!selection.collapsed) {
            transformedState.deleteSelectedText()
        } else if (selection.min > 0) {
            transformedState.replaceText("", TextRange(selection.min - 1, selection.max))
        }
    }

    override fun setSelectedText(range: TextRange?) = edit {
        transformedState.selectCharsIn(range ?: TextRange(visualText.length))
    }

    override fun setMarkedText(markedText: String?, selectedRange: TextRange) {
        if (markedText == null) {
            unmarkText()
            return
        }
        edit {
            transformedState.editUntransformedTextAsUser {
                val marked = composition ?: selection
                replace(marked.min, marked.max, markedText)
                setComposition(marked.min, marked.min + markedText.length)
                val cursor = marked.min + selectedRange.min
                setSelectionCoerced(cursor, cursor + selectedRange.length)
            }
        }
    }

    override fun unmarkText() = edit {
        transformedState.editUntransformedTextAsUser { commitComposition() }
    }
}
