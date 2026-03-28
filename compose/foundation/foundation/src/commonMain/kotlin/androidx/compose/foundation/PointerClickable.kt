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

package androidx.compose.foundation

import androidx.compose.foundation.gestures.ScrollableContainerNode
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A data structure representing a pointer click with hardware metadata.
 *
 * @property position The pointer position relative to the containing element.
 * @property buttons The pointer buttons that were active during the click (e.g., Primary, Secondary).
 * This is `null` if the click was synthesized by accessibility services or keyboard actions.
 * @property keyboardModifiers The keyboard modifiers that were active during the click (e.g., Shift, Ctrl).
 */
@ExperimentalFoundationApi
class PointerClickEvent(
    val position: Offset,
    val buttons: PointerButtons?,
    val keyboardModifiers: PointerKeyboardModifiers
)

/**
 * Configures a component to receive pointer clicks (mouse, touch, stylus) alongside hardware metadata.
 *
 * Unlike standard [clickable], this modifier exposes the specific [PointerButtons] and
 * [PointerKeyboardModifiers] present at the time of the click, making it suitable for complex
 * desktop-style interactions (e.g., Shift+Click to multi-select, Right-Click for context menus).
 *
 * ***Note:*** Any removal operations on Android Views from [onPointerClick] should wrap the
 * [onClick] lambda in a `post { }` block to guarantee the event dispatch completes before
 * executing the removal.
 *
 * @param enabled Controls the enabled state. When `false`, [onClick] will not be invoked, and
 * this modifier will appear disabled to accessibility services.
 * @param onClickLabel Semantic / accessibility label for the [onClick] action.
 * @param role The type of user interface element. Accessibility services might use this to describe
 * the element or do customizations.
 * @param interactionSource [MutableInteractionSource] that will be used to dispatch
 * [PressInteraction.Press] when this element is pressed.
 * @param onClick Will be called when the user clicks on the element, providing hardware metadata.
 */
@ExperimentalFoundationApi
fun Modifier.onPointerClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (PointerClickEvent) -> Unit
): Modifier = this.then(
    PointerClickElement(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        onClick = onClick
    )
)

@OptIn(ExperimentalFoundationApi::class)
private class PointerClickElement(
    private val enabled: Boolean,
    private val onClickLabel: String?,
    private val role: Role?,
    private val interactionSource: MutableInteractionSource?,
    private val onClick: (PointerClickEvent) -> Unit
) : ModifierNodeElement<PointerClickNode>() {

    override fun create() = PointerClickNode(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        onClick = onClick
    )

    override fun update(node: PointerClickNode) {
        node.update(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = interactionSource,
            onClick = onClick
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onPointerClick"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["interactionSource"] = interactionSource
        properties["onClick"] = onClick
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointerClickElement) return false
        if (enabled != other.enabled) return false
        if (onClickLabel != other.onClickLabel) return false
        if (role != other.role) return false
        if (interactionSource != other.interactionSource) return false
        if (onClick !== other.onClick) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + (onClickLabel?.hashCode() ?: 0)
        result = 31 * result + (role?.hashCode() ?: 0)
        result = 31 * result + (interactionSource?.hashCode() ?: 0)
        result = 31 * result + onClick.hashCode()
        return result
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class PointerClickNode(
    private var enabled: Boolean,
    private var onClickLabel: String?,
    private var role: Role?,
    private var interactionSource: MutableInteractionSource?,
    private var onClick: (PointerClickEvent) -> Unit
) : DelegatingNode(),
    PointerInputModifierNode,
    SemanticsModifierNode,
    FocusRequesterModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private var downEvent: PointerInputChange? = null
    private var downButtons: PointerButtons? = null
    private var downKeyboardModifiers: PointerKeyboardModifiers? = null

    private var pressInteraction: PressInteraction.Press? = null
    private var delayJob: Job? = null

    private var componentSize: IntSize = IntSize.Zero
    private var centerOffset: Offset = Offset.Zero

    private val focusableNode = delegate(
        FocusableNode(interactionSource)
    )

    override fun onRemeasured(size: IntSize) {
        componentSize = size
        centerOffset = size.center.toOffset()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass == PointerEventPass.Main) {
            if (downEvent == null) {
                // Listen for ANY button pressing down, not just primary
                val downChange =
                    pointerEvent.changes.firstOrNull { it.changedToDown() && !it.isConsumed }
                if (downChange != null) {
                    handleDownEvent(downChange, pointerEvent)
                }
            } else {
                // To support multitouch accurately, a click is only resolved
                // when all active pointers are lifted from the component.
                if (pointerEvent.changes.fastAll { it.changedToUp() }) {
                    handleUpEvent(pointerEvent)
                } else {
                    handleNonUpEventIfNeeded(pointerEvent)
                }
            }
        } else if (pass == PointerEventPass.Final) {
            checkForCancellation(pointerEvent)
        }
    }

    private fun handleDownEvent(down: PointerInputChange, pointerEvent: PointerEvent) {
        down.consume()
        this.downEvent = down
        this.downButtons = pointerEvent.buttons
        this.downKeyboardModifiers = pointerEvent.keyboardModifiers

        if (enabled) {
            requestFocusWhenInMouseInputMode()
            handlePressInteractionStart(down.position)
        }
    }

    private fun handleUpEvent(pointerEvent: PointerEvent) {
        val upChange = pointerEvent.changes[0]
        upChange.consume()

        if (enabled) {
            handlePressInteractionRelease(downEvent!!.position)
            val event = PointerClickEvent(
                position = upChange.position,
                buttons = downButtons,
                keyboardModifiers = downKeyboardModifiers ?: pointerEvent.keyboardModifiers
            )
            onClick(event)
        }
        cancelInput()
    }

    private fun handleNonUpEventIfNeeded(pointerEvent: PointerEvent) {
        val minimumTouchTargetSizeDp = currentValueOf(LocalViewConfiguration).minimumTouchTargetSize
        val minimumTouchTargetSize = with(requireDensity()) { minimumTouchTargetSizeDp.toSize() }
        val horizontal = max(0f, minimumTouchTargetSize.width - componentSize.width) / 2f
        val vertical = max(0f, minimumTouchTargetSize.height - componentSize.height) / 2f
        val touchPadding = Size(horizontal, vertical)

        if (pointerEvent.changes.fastAny { it.isConsumed || it.isOutOfBounds(componentSize, touchPadding) }) {
            cancelInput()
        }
    }

    private fun checkForCancellation(pointerEvent: PointerEvent) {
        if (downEvent != null) {
            // Relies on identical object references ( !== ) within the same pointer frame
            // to differentiate between self-consumption and foreign consumption (e.g. scroll parents).
            if (pointerEvent.changes.fastAny { it.isConsumed && it !== downEvent }) {
                cancelInput()
            }
        }
    }

    override fun onCancelPointerInput() {
        cancelInput()
    }

    private fun cancelInput() {
        downEvent = null
        downButtons = null
        downKeyboardModifiers = null
        handlePressInteractionCancel()
    }

    private fun handlePressInteractionStart(offset: Offset) {
        interactionSource?.let { source ->
            val press = PressInteraction.Press(offset)
            val shouldDelayPress = hasScrollableContainer()

            if (shouldDelayPress) {
                delayJob = coroutineScope.launch {
                    delay(TapIndicationDelay)
                    source.emit(press)
                    pressInteraction = press
                }
            } else {
                pressInteraction = press
                coroutineScope.launch { source.emit(press) }
            }
        }
    }

    private fun handlePressInteractionRelease(offset: Offset) {
        interactionSource?.let { source ->
            val job = delayJob
            if (job?.isActive == true) {
                job.cancel()
                coroutineScope.launch {
                    // Prevents interaction emission race conditions (b/414319919)
                    // where rapid clicks resolve before the cancellation completes.
                    job.join()
                    val press = PressInteraction.Press(offset)
                    val release = PressInteraction.Release(press)
                    source.emit(press)
                    source.emit(release)
                }
            } else {
                pressInteraction?.let {
                    coroutineScope.launch { source.emit(PressInteraction.Release(it)) }
                }
            }
            pressInteraction = null
        }
    }

    private fun handlePressInteractionCancel() {
        interactionSource?.let { source ->
            if (delayJob?.isActive == true) {
                delayJob?.cancel()
            } else {
                pressInteraction?.let {
                    coroutineScope.launch { source.emit(PressInteraction.Cancel(it)) }
                }
            }
            pressInteraction = null
        }
    }

    private fun requestFocusWhenInMouseInputMode() {
        if (isRequestFocusOnClickEnabled()) {
            requestFocus()
        }
    }

    private fun hasScrollableContainer(): Boolean {
        var hasScrollable = false
        traverseAncestors(ScrollableContainerNode.TraverseKey) { node ->
            hasScrollable = hasScrollable || (node as ScrollableContainerNode).enabled
            !hasScrollable
        }
        return hasScrollable
    }

    fun update(
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        interactionSource: MutableInteractionSource?,
        onClick: (PointerClickEvent) -> Unit
    ) {
        if (this.enabled != enabled) {
            if (!enabled) cancelInput()
            this.enabled = enabled
            invalidateSemantics()
        }
        if (this.onClickLabel != onClickLabel || this.role != role) {
            this.onClickLabel = onClickLabel
            this.role = role
            invalidateSemantics()
        }
        if (this.interactionSource != interactionSource) {
            cancelInput()
            this.interactionSource = interactionSource
            focusableNode.update(interactionSource)
        }
        this.onClick = onClick
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (this@PointerClickNode.role != null) {
            role = this@PointerClickNode.role!!
        }
        onClick(
            action = {
                val currentModifiers = currentValueOf(LocalWindowInfo).keyboardModifiers
                val synthesizedEvent = PointerClickEvent(
                    position = centerOffset,
                    buttons = null,
                    keyboardModifiers = currentModifiers
                )
                onClick(synthesizedEvent)
                true
            },
            label = onClickLabel
        )
        if (!enabled) {
            disabled()
        }
    }
}