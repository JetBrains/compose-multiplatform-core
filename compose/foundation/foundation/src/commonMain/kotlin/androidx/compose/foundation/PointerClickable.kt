/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.input.pointer.isPrimaryPressed
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.debugInspectorInfo
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
 * @property position The exact coordinate of the click relative to the component's bounds.
 * @property buttons The pointer buttons active during the click (e.g., Primary/Left, Secondary/Right).
 * This is `null` if the click was synthesized via keyboard or accessibility services.
 * @property keyboardModifiers The keyboard modifiers active during the click (e.g., Shift, Ctrl, Alt).
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
 * Unlike the standard [clickable] modifier, `onPointerClick` routes raw [PointerButtons] and
 * [PointerKeyboardModifiers] into the callback. This is essential for building complex, desktop-grade
 * multiplatform interactions such as Shift+Clicking to multi-select, or Right-Clicking for context menus.
 *
 * By default, this modifier provides Material Design UX: it listens to all mouse buttons, but only
 * emits visual ripples on Primary actions (Touch or Left-Click). This behavior can be overridden
 * via the [triggerPressIndication] parameter.
 *
 * @param enabled Controls the enabled state. When `false`, [onClick] will not be invoked, and
 * the element will appear disabled to accessibility services.
 * @param onClickLabel Semantic/accessibility label for the click action.
 * @param role The type of user interface element (e.g., [Role.Button]).
 * @param interactionSource The [MutableInteractionSource] used to dispatch [PressInteraction]s.
 * If `null`, a default source is remembered automatically.
 * @param indication The visual effect to draw when the element is pressed. Defaults to [LocalIndication].
 * Set this to `null` to entirely disable visual feedback.
 * @param triggerPressIndication A lambda that evaluates a raw [PointerEvent] and returns `true` if
 * the event should transition the component into a "Pressed" state (triggering ripples). By default,
 * only Primary clicks trigger this state.
 * @param onClick Invoked when the element is successfully clicked, providing hardware metadata.
 */
@ExperimentalFoundationApi
fun Modifier.onPointerClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    triggerPressIndication: (PointerEvent) -> Boolean = { it.buttons.isPrimaryPressed },
    onClick: (PointerClickEvent) -> Unit
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "onPointerClick"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["interactionSource"] = interactionSource
        properties["indication"] = indication
        properties["triggerPressIndication"] = triggerPressIndication
        properties["onClick"] = onClick
    }
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val resolvedIndication = indication ?: LocalIndication.current

    this.then(
        PointerClickElement(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = resolvedInteractionSource,
            triggerPressIndication = triggerPressIndication,
            onClick = onClick
        )
    ).indication(
        interactionSource = resolvedInteractionSource,
        indication = resolvedIndication
    )
}

@OptIn(ExperimentalFoundationApi::class)
private class PointerClickElement(
    private val enabled: Boolean,
    private val onClickLabel: String?,
    private val role: Role?,
    private val interactionSource: MutableInteractionSource,
    private val triggerPressIndication: (PointerEvent) -> Boolean,
    private val onClick: (PointerClickEvent) -> Unit
) : ModifierNodeElement<PointerClickNode>() {

    override fun create() = PointerClickNode(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        triggerPressIndication = triggerPressIndication,
        onClick = onClick
    )

    override fun update(node: PointerClickNode) {
        node.update(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = interactionSource,
            triggerPressIndication = triggerPressIndication,
            onClick = onClick
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointerClickElement) return false
        if (enabled != other.enabled) return false
        if (onClickLabel != other.onClickLabel) return false
        if (role != other.role) return false
        if (interactionSource != other.interactionSource) return false
        if (triggerPressIndication !== other.triggerPressIndication) return false
        if (onClick !== other.onClick) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + (onClickLabel?.hashCode() ?: 0)
        result = 31 * result + (role?.hashCode() ?: 0)
        result = 31 * result + interactionSource.hashCode()
        result = 31 * result + triggerPressIndication.hashCode()
        result = 31 * result + onClick.hashCode()
        return result
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class PointerClickNode(
    private var enabled: Boolean,
    private var onClickLabel: String?,
    private var role: Role?,
    private var interactionSource: MutableInteractionSource,
    private var triggerPressIndication: (PointerEvent) -> Boolean,
    private var onClick: (PointerClickEvent) -> Unit
) : DelegatingNode(),
    PointerInputModifierNode,
    SemanticsModifierNode,
    FocusRequesterModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    // Hardware State Caching
    private var downEvent: PointerInputChange? = null
    private var downButtons: PointerButtons? = null
    private var downKeyboardModifiers: PointerKeyboardModifiers? = null

    // Interaction Lifecycle
    private var pressInteraction: PressInteraction.Press? = null
    private var delayJob: Job? = null

    // Layout Metrics
    private var componentSize: IntSize = IntSize.Zero
    private var centerOffset: Offset = Offset.Zero

    private var focusableNode: FocusableNode? = null

    private fun updateFocusableNode() {
        if (enabled && focusableNode == null) {
            focusableNode = delegate(FocusableNode(interactionSource))
        } else if (!enabled && focusableNode != null) {
            focusableNode?.let { undelegate(it) }
            focusableNode = null
        }
    }

    override fun onAttach() {
        super.onAttach()
        updateFocusableNode()
    }

    override fun onDetach() {
        focusableNode?.let { undelegate(it) }
        focusableNode = null
        super.onDetach()
    }
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
                val downChange = pointerEvent.changes.firstOrNull { it.changedToDown() && !it.isConsumed }
                if (downChange != null) {
                    handleDownEvent(downChange, pointerEvent)
                }
            } else {
                // Multi-touch constraint: A click resolves only when ALL active pointers lift.
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

        // Cache the hardware state exactly as it was during the initial 'Down' frame
        this.downButtons = pointerEvent.buttons
        this.downKeyboardModifiers = pointerEvent.keyboardModifiers

        if (enabled) {
            requestFocusWhenInMouseInputMode()

            // Consult the developer's logic to see if this raw event warrants a visual ripple
            if (triggerPressIndication(pointerEvent)) {
                handlePressInteractionStart(down.position)
            }
        }
    }

    private fun handleUpEvent(pointerEvent: PointerEvent) {
        // Map the up event back to the original pointer to handle multitouch displacement safely
        val upChange = pointerEvent.changes.firstOrNull { it.id == downEvent?.id }
            ?: pointerEvent.changes.first()

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
        val press = PressInteraction.Press(offset)

        // Parent scroll containers intercept touches. Delay the visual ripple
        // to prevent UI flickering if the user is just scrolling past this component.
        if (hasScrollableContainer()) {
            delayJob = coroutineScope.launch {
                delay(TapIndicationDelay)
                interactionSource.emit(press)
                pressInteraction = press
            }
        } else {
            pressInteraction = press
            coroutineScope.launch { interactionSource.emit(press) }
        }
    }

    private fun handlePressInteractionRelease(offset: Offset) {
        val job = delayJob
        if (job?.isActive == true) {
            job.cancel()
            coroutineScope.launch {
                // Prevents interaction emission race conditions (b/414319919)
                // Ensures a rapid "lightning click" still briefly flashes the ripple.
                job.join()
                val press = PressInteraction.Press(offset)
                val release = PressInteraction.Release(press)
                interactionSource.emit(press)
                interactionSource.emit(release)
            }
        } else {
            pressInteraction?.let {
                coroutineScope.launch { interactionSource.emit(PressInteraction.Release(it)) }
            }
        }
        pressInteraction = null
    }

    private fun handlePressInteractionCancel() {
        if (delayJob?.isActive == true) {
            delayJob?.cancel()
        } else {
            pressInteraction?.let {
                coroutineScope.launch { interactionSource.emit(PressInteraction.Cancel(it)) }
            }
        }
        pressInteraction = null
    }

    private fun requestFocusWhenInMouseInputMode() {
        // Implementation delegates to Compose internals to avoid focusing on touch devices
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
        interactionSource: MutableInteractionSource,
        triggerPressIndication: (PointerEvent) -> Boolean,
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
        this.triggerPressIndication = triggerPressIndication
        this.onClick = onClick
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (this@PointerClickNode.role != null) {
            role = this@PointerClickNode.role!!
        }
        if (enabled) {
            // When enabled, expose focus semantics from the delegated focusableNode
            with(focusableNode) {
                applySemantics()
            }
            onClick(
                action = {
                    // Fetch window modifiers dynamically to support screen-readers triggering
                    // clicks while the user holds a physical key (e.g. Switch Access).
                    val currentModifiers = currentValueOf(LocalWindowInfo).keyboardModifiers
                    val synthesizedEvent = PointerClickEvent(
                        position = centerOffset,
                        buttons = null, // Synthesized clicks lack hardware pointers
                        keyboardModifiers = currentModifiers
                    )
                    onClick(synthesizedEvent)
                    true
                },
                label = onClickLabel
            )
        } else {
            // When disabled, suppress focus and click semantics and mark as disabled
            disabled()
        }
    }
}