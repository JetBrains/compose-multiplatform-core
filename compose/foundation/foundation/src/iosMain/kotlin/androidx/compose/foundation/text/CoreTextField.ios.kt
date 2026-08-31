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

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextInputContainer
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.uikit.LocalTextInputContainer
import androidx.compose.ui.unit.Density

@OptIn(InternalComposeUiApi::class)
internal actual fun Modifier.textFieldCursor(
    state: LegacyTextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping,
    cursorBrush: Brush,
    showCursor: Boolean,
): Modifier = composed {
    val selectionColors = LocalTextSelectionColors.current
    LaunchedEffect(selectionColors) {
        // iOS uses one color to draw the cursor and selection handles
        // If it's not user set, use the system default one
        state.holder?.updateNativeTextInputTintColor(selectionColors.nativeTintColor)
    }

    if (state.holder.isNativeTextInput) {
        this
    } else {
        cursor(state, value, offsetMapping, cursorBrush, showCursor)
    }
}

@OptIn(InternalComposeUiApi::class)
internal actual fun Modifier.textFieldDraw(
    state: LegacyTextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping,
): Modifier = this then TextFieldDrawElement(state, value, offsetMapping)

private data class TextFieldDrawElement(
    private val state: LegacyTextFieldState,
    private val value: TextFieldValue,
    private val offsetMapping: OffsetMapping,
) : ModifierNodeElement<TextFieldDrawNode>() {

    override fun create() = TextFieldDrawNode(
        state = state,
        value = value,
        offsetMapping = offsetMapping,
    )

    override fun update(node: TextFieldDrawNode) {
        node.update(
            state = state,
            value = value,
            offsetMapping = offsetMapping
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "textFieldDraw"
        properties["state"] = state
        properties["value"] = value
        properties["offsetMapping"] = offsetMapping
    }
}

@OptIn(InternalComposeUiApi::class)
private class TextFieldDrawNode(
    private var state: LegacyTextFieldState,
    private var value: TextFieldValue,
    private var offsetMapping: OffsetMapping,
) : Modifier.Node(),
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode,
    DrawModifierNode {
    private var usingNativeTextInput: Boolean = false

    override fun onAttach() {
        super.onAttach()
        onObservedReadsChanged()
        invalidateDraw()
    }

    override fun onObservedReadsChanged() {
        observeReads {
            val nativeTintColor = currentValueOf(LocalTextSelectionColors).nativeTintColor
            state.holder?.updateNativeTextInputTintColor(nativeTintColor)
            val usingNativeTextInput = state.holder.isNativeTextInput
            if (usingNativeTextInput != this.usingNativeTextInput) {
                this.usingNativeTextInput = usingNativeTextInput
                invalidateDraw()
            }
        }
    }

    fun update(
        state: LegacyTextFieldState,
        value: TextFieldValue,
        offsetMapping: OffsetMapping,
    ) {
        this.state = state
        this.value = value
        this.offsetMapping = offsetMapping
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() = drawBehind()

    private fun ContentDrawScope.drawBehind() {
        onDraw()
        drawContent()
    }

    private fun DrawScope.onDraw() {
        state.layoutResult?.let { layoutResult ->
            drawIntoCanvas { canvas ->
                // iOS handles selection drawing itself in native text input mode
                // still needs this for text rendering
                if (usingNativeTextInput) {
                    TextPainter.paint(canvas, layoutResult.value)
                } else {
                    TextFieldDelegate.draw(
                        canvas,
                        value,
                        state.selectionPreviewHighlightRange,
                        state.deletionPreviewHighlightRange,
                        offsetMapping,
                        layoutResult.value,
                        state.highlightPaint,
                        state.selectionBackgroundColor,
                    )
                }
            }
        }
    }
}

internal actual fun Modifier.textFieldOverlay(
    state: LegacyTextFieldState,
    imeOptions: ImeOptions,
    interactionSource: InteractionSource?
): Modifier = this then
    CoreTextFieldImeOverlayElement(state, imeOptions, state.processor.toTextFieldValue(), interactionSource)

private data class CoreTextFieldImeOverlayElement(
    private val state: LegacyTextFieldState,
    private val imeOptions: ImeOptions,
    private val value: TextFieldValue,
    private val interactionSource: InteractionSource?,
) : ModifierNodeElement<CoreTextFieldImeOverlayNode>() {

    override fun create() = CoreTextFieldImeOverlayNode(state, imeOptions, interactionSource)

    override fun update(node: CoreTextFieldImeOverlayNode) {
        node.update(state, imeOptions, interactionSource)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "coreTextFieldImeOverlay"
        properties["state"] = state
        properties["imeOptions"] = imeOptions
        properties["value"] = value
        properties["interactionSource"] = interactionSource
    }
}

@OptIn(InternalComposeUiApi::class)
private class CoreTextFieldImeOverlayNode(
    private var state: LegacyTextFieldState,
    imeOptions: ImeOptions,
    interactionSource: InteractionSource?,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode {

    private val delegate = CoreTextFieldInputDelegate(state, interactionSource, imeOptions)

    private var container: TextInputContainer? = null

    private var holder: TextInputContainer.Holder? = null
        set(value) {
            field = value
            state.holder = value
        }

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

    fun update(state: LegacyTextFieldState, imeOptions: ImeOptions, interactionSource: InteractionSource?) {
        if (this.state !== state) {
            this.state.holder = null
            this.state = state
            state.holder = holder
            delegate.state = state
        }
        delegate.imeOptions = imeOptions
        delegate.interactionSource = interactionSource
    }

    override fun onObservedReadsChanged() {
        observeReads {
            val container = currentValueOf(LocalTextInputContainer)
            val density = currentValueOf(LocalDensity)

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
}

@OptIn(InternalComposeUiApi::class)
private class CoreTextFieldInputDelegate(
    var state: LegacyTextFieldState,
    var interactionSource: InteractionSource?,
    override var imeOptions: ImeOptions
) : TextInputContainer.Delegate {
    override val editorToken: Any
        get() = state

    private val value: TextFieldValue
        get() = state.processor.toTextFieldValue()

    override val text: String
        get() = value.text

    override val isFocused: Boolean
        get() = state.hasFocus

    override val selectionTextRange: TextRange
        get() = value.selection

    override val markedTextRange: TextRange?
        get() = value.composition

    override fun insertText(text: String) {
        sendEditCommands(CommitTextCommand(text, 1))
    }

    override fun replaceRange(range: TextRange, text: String) {
        sendEditCommands(
            SetComposingRegionCommand(range.min, range.max),
            SetComposingTextCommand(text, 1),
            FinishComposingTextCommand(),
        )
    }

    override fun deleteBackward() {
        sendEditCommands(
            if (value.selection.collapsed) {
                DeleteSurroundingTextCommand(lengthBeforeCursor = 1, lengthAfterCursor = 0)
            } else {
                CommitTextCommand("", 0)
            }
        )
    }

    override fun setSelectedText(range: TextRange?) {
        val selection = range ?: TextRange(text.length)
        sendEditCommands(SetSelectionCommand(selection.min, selection.max))
    }

    override fun setMarkedText(markedText: String?, selectedRange: TextRange) {
        if (markedText == null) {
            unmarkText()
        } else {
            sendEditCommands(SetComposingTextCommand(markedText, 1))
        }
    }

    override fun unmarkText() {
        sendEditCommands(FinishComposingTextCommand())
    }

    private fun sendEditCommands(vararg commands: EditCommand) {
        TextFieldDelegate.onEditCommand(
            ops = commands.toList(),
            editProcessor = state.processor,
            onValueChange = state.onValueChange,
            session = state.inputSession,
        )
    }
}
