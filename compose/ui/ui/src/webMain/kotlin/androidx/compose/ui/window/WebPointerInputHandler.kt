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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.window

import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableIntSetOf
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.composeButton
import androidx.compose.ui.input.pointer.composeButtons
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

/**
 * Wires the handlers of browser pointer, touch and wheel events to the [scene].
 * The [scene] may consume the events or leave them unconsumed.
 * For that reason, the handlers contain the logic for conditional 'preventDefault' to co-operate with
 * browser gestures (e.g. pull-to-refresh, outer scroll, etc) and other behaviors (e.g. focus)
 *
 * @param canvas - the canvas on which we observe the events.
 * @param scene - the target for the events.
 */
internal abstract class WebPointerInputHandler(
    private val canvas: HTMLCanvasElement,
    private val scene: ComposeScene
) {
    protected abstract val density: Density
    protected abstract val rootScrollObserver: RootScrollObserver

    // let the host react to the incoming pointer events before they get processed by the scene
    protected abstract fun beforePointerEvent(pointerType: PointerType)
    // let the host react tp touch start events
    protected abstract fun onTouchStart()
    // ask host to hit-test at the position and check if it hits an active text input
    protected abstract fun hasActiveTextInputAt(position: Offset): Boolean

    private var actualActivePointerButtons: PointerButtons = PointerButtons()

    private var keyboardModeState: KeyboardModeState = KeyboardModeState.Hardware

    // Used in WebTextInputService. Also see https://youtrack.jetbrains.com/issue/CMP-8611
    var activeTouchOffset: Offset = Offset.Unspecified
        private set

    fun initEvents(canvasEvents: EventTargetListener, globalEvents: EventTargetListener) {
        val onPointerCallback: (PointerEvent) -> Unit = { onPointerEvent(it) }
        listOf(
            "pointerenter",
            "pointerdown",
            "pointermove",
            "pointerup",
            "pointerleave",
            "pointercancel"
        ).fastForEach { name ->
            canvasEvents.addTypedEvent<PointerEvent>(name, passive = false, handler = onPointerCallback)
        }

        globalEvents.addDisposableEvent("dragend") {
            // in Safari pointerup event is not firing when we drop or cancel drop
            // see https://youtrack.jetbrains.com/issue/CMP-10102
            actualActivePointerButtons = PointerButtons()
        }

        canvasEvents.addTypedEvent<TouchEvent>("touchstart", passive = false) { evt ->
            onTouchStart()
            // preventDefault the touchstart if the corresponding pointerdown hits the active text input.
            // Pros: a long press (touchstart + ~500ms delay after it) triggers focus changes in iOS Safari,
            // and this is the only chance (the only event we have) to prevent that behavior,
            // since we want the focus to remain in the active text input.
            // Cons: the browser's gestures (e.g., pull-to-refresh) become suppressed while
            // the pointer is in the active text input.
            val position = activeTouchPointers[lastPointerDownId]?.composePointer?.position
            if (hasActiveTextInputAt(position ?: Offset.Unspecified)) {
                evt.preventDefault()
            }
            lastPointerDownId = -1

            // In other cases, we never preventDefault() touchstart, even though Compose consumes
            // the corresponding pointerdown on interactive areas. Per the Touch Events spec,
            // canceling touchstart suppresses the browser's default actions for the entire
            // touch sequence: scrolling, back gesture, pull-to-refresh, and the compatibility
            // mouse events. At touchstart time we can't yet know whether Compose will
            // actually handle the gesture. The decision is deferred to where it can be made
            // correctly: touchmove (move-based gestures) and touchend (tap-based defaults).
        }

        canvasEvents.addTypedEvent<TouchEvent>("touchend", passive = false) { evt ->
            // Browsers dispatch pointerup before the corresponding touchend (de facto true in
            // all engines, though the specs don't mandate the relative order), so at this
            // point we already know whether Compose consumed the release - see onPointerEvent.
            //
            // If it did, we cancel touchend to suppress the tap's remaining default actions,
            // primarily the "compatibility mouse events" - https://w3c.github.io/touch-events/#mouse-events
            //
            // Suppressing the synthetic mouse events prevents:
            // - a duplicate click reaching the page for a tap Compose already handled;
            // - the synthetic mousedown moving DOM focus, e.g. blurring the backing input of
            //   a focused Compose text field and hiding the virtual keyboard
            //   (https://youtrack.jetbrains.com/issue/CMP-10079).
            //
            // Move-based gestures (scroll, back gesture, pull-to-refresh) are NOT affected:
            // those are decided earlier, in the touchmove handler and by the canvas
            // touch-action style.
            if (lastPointerReleaseConsumed && evt.cancelable) {
                evt.preventDefault()
            }
            lastPointerReleaseConsumed = false // reset
        }

        // While we don't pass touchmove(s) to Compose (it processes pointermove instead), only
        // touchmove's preventDefault() can stop the browser from taking over a move-based
        // gesture (scroll, back gesture, pull-to-refresh): per the Pointer Events spec,
        // canceling pointer events has no effect on the browser's direct manipulation
        // behaviors - only touch-action and canceling touch events do.
        // https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/touch-action
        // > Applications using Touch events disable the browser handling of gestures by calling preventDefault()
        canvasEvents.addTypedEvent<TouchEvent>("touchmove", passive = false) { evt ->
            // This event happens after the corresponding pointermove, so Compose has already
            // processed the move. Here we decide if the browser should take over the gesture.
            val shouldPreventDefault = when {
                // First case: Scrolling happened in Compose, so the browser shouldn't take over the gesture.
                rootScrollObserver.consumedAnyScroll() -> true
                // Second case: No scrolling in Compose, but still some component (e.g., drag) consumed at least one pointermove event.
                !rootScrollObserver.hadAnyScroll() && !activeTouchPointersConsumedMoves.isEmpty() -> true
                // Third case: usually it's when scroll gestures happened at the edge (nowhere to scroll anymore),
                // so they were not consumed by Compose. We let the browser handle this gesture.
                else -> false
            }
            if (shouldPreventDefault && evt.cancelable) {
                evt.preventDefault()
            }
        }

        canvasEvents.addTypedEvent<WheelEvent>("wheel", passive = false) { event ->
            onWheelEvent(event)
        }
    }

    private inner class TouchEventWithContainerOffset(
        val event: PointerEvent,
        val containerOffset: Offset
    ) {
        val composePointer = event.toScenePointerEvent(containerOffset, density)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as TouchEventWithContainerOffset

            if (event != other.event) return false
            if (containerOffset != other.containerOffset) return false

            return true
        }

        override fun hashCode(): Int {
            var result = event.hashCode()
            result = 31 * result + containerOffset.hashCode()
            return result
        }
    }

    private val activeTouchPointers = mutableIntObjectMapOf<TouchEventWithContainerOffset>()

    private var lastPointerDownId: Int = -1

    // Whether Compose consumed the most recent touch pointerup. Read (and then reset) by the
    // touchend handler, which the browser dispatches right after pointerup, to decide whether
    // to suppress the compatibility mouse events. A single flag suffices because every
    // touchend is immediately preceded by its own pointerup; in the rare case where several
    // simultaneously lifted fingers are coalesced into one touchend, the flag reflects only
    // the last release.
    private var lastPointerReleaseConsumed = false

    // Pointer IDs whose move events were consumed during the active touch sequence.
    // It's a part of touch events preventDefault logic.
    private val activeTouchPointersConsumedMoves = mutableIntSetOf()
    private val reusableTouchPointerList = mutableListOf<ComposeScenePointer>()
    private fun getActivePointers(): MutableList<ComposeScenePointer> {
        reusableTouchPointerList.clear()
        activeTouchPointers.forEachValue {
            reusableTouchPointerList.add(it.composePointer)
        }
        return reusableTouchPointerList
    }

    private fun onPointerEvent(event: PointerEvent) {
        if (event.type == "pointercancel") {
            if (isTouchEvent(event)) {
                activeTouchPointers.clear()
                activeTouchPointersConsumedMoves.remove(event.pointerId)
                activeTouchOffset = Offset.Unspecified
                lastPointerReleaseConsumed = false
            } else {
                actualActivePointerButtons = PointerButtons()
            }

            event.target?.let { releasePointerCapture(it, event.pointerId) }

            scene.cancelPointerInput()
            return
        }

        val eventType = event.getPointerEventType()
        var result: PointerEventResult? = null
        var anyChangeConsumed = false

        if (isMouseEvent(event)) {
            beforePointerEvent(PointerType.Mouse)

            keyboardModeState = KeyboardModeState.Hardware

            // Track active mouse buttons. Used as a fallback for unreliable
            // `buttons` in wheel events (see CMP-9900) and to reset state on
            // `dragend` in Safari (see CMP-10102).
            when (eventType) {
                PointerEventType.Press -> {
                    actualActivePointerButtons = event.composeButtons
                }

                PointerEventType.Release -> {
                    actualActivePointerButtons = PointerButtons()
                }
            }

            scene.sendPointerEvent(
                eventType = eventType,
                position = event.offset,
                timeMillis = event.timeStamp.toInt().toLong(),
                buttons = event.composeButtons,
                keyboardModifiers = PointerKeyboardModifiers(
                    isCtrlPressed = event.ctrlKey,
                    isMetaPressed = event.metaKey,
                    isAltPressed = event.altKey,
                    isShiftPressed = event.shiftKey,
                ),
                nativeEvent = event,
                button = event.composeButton,
            )
        } else {
            if (eventType == PointerEventType.Enter || eventType == PointerEventType.Exit) {
                //Enter and Exit events have no sense for touches (Firefox and Safari send them)
                return
            }

            if (activeTouchPointers.isEmpty()) {
                require(activeTouchPointersConsumedMoves.isEmpty())
                rootScrollObserver.reset()
            }

            val pointerType = when {
                isTouchEvent(event) -> PointerType.Touch
                isPenEvent(event) -> PointerType.Stylus
                else -> PointerType.Unknown
            }
            beforePointerEvent(pointerType)

            keyboardModeState = KeyboardModeState.Virtual

            val current: TouchEventWithContainerOffset
            val active = activeTouchPointers[event.pointerId]
            if (active == null) {
                event.target?.let { setPointerCapture(it, event.pointerId) }
                val containerOffset = canvas.getBoundingClientRect().let {
                    Offset(it.left.toFloat(), it.top.toFloat())
                }
                current = TouchEventWithContainerOffset(event, containerOffset)
            } else {
                current = TouchEventWithContainerOffset(event, active.containerOffset)
            }
            activeTouchPointers[event.pointerId] = current

            activeTouchOffset = current.composePointer.position

            val pointers = getActivePointers()
            val buttons = PointerButtons()
            val keyboardModifiers = PointerKeyboardModifiers()

            var coalescedEvents: List<PointerEvent>? = null
            if (eventType == PointerEventType.Move) {
                coalescedEvents = getCoalescedEvents(event).toList()
            }

            if (coalescedEvents != null && coalescedEvents.size > 1) {
                var indexOfCurrentPointer = -1
                for (index in pointers.indices) {
                    if (pointers[index] == current.composePointer) {
                        indexOfCurrentPointer = index
                        break
                    }
                }

                coalescedEvents.fastForEach { coalescedEvent ->
                    val coalescedEventType = coalescedEvent.getPointerEventType()
                    val sceneEvent =
                        coalescedEvent.toScenePointerEvent(current.containerOffset, density)
                    pointers[indexOfCurrentPointer] = sceneEvent
                    result = scene.sendPointerEvent(
                        eventType = coalescedEventType,
                        pointers = pointers,
                        buttons = buttons,
                        keyboardModifiers = keyboardModifiers,
                        scrollDelta = Offset.Zero,
                        timeMillis = coalescedEvent.timeStamp.toInt().toLong(),
                        nativeEvent = coalescedEvent,
                        button = null
                    )
                    anyChangeConsumed = anyChangeConsumed || result.anyChangeConsumed
                }
            } else {
                result = scene.sendPointerEvent(
                    eventType = eventType,
                    pointers = pointers,
                    buttons = buttons,
                    keyboardModifiers = keyboardModifiers,
                    scrollDelta = Offset.Zero,
                    timeMillis = event.timeStamp.toInt().toLong(),
                    nativeEvent = event,
                    button = null
                )
                anyChangeConsumed = result.anyChangeConsumed
            }

            activeTouchOffset = Offset.Unspecified

            if (eventType == PointerEventType.Release) {
                lastPointerReleaseConsumed = anyChangeConsumed
                activeTouchPointers.remove(event.pointerId)
                activeTouchPointersConsumedMoves.remove(event.pointerId)
            }

            // Canceling a pointer event does not block scrolling or other native gestures
            // (per the Pointer Events spec, only touch-action / canceling touch events do);
            if (anyChangeConsumed && event.cancelable) {
                event.preventDefault()
                if (eventType == PointerEventType.Move) {
                    activeTouchPointersConsumedMoves.add(event.pointerId)
                } else if (eventType == PointerEventType.Press) {
                    lastPointerDownId = event.pointerId
                }
            }
        }
    }

    private fun onWheelEvent(
        event: WheelEvent,
    ) {
        keyboardModeState = KeyboardModeState.Hardware

        // Shift + mouse wheel means horizontal scroll. Some browsers swap the axes
        // for us (report deltaX instead of deltaY), some don't.
        val horizontalScroll: Double
        val verticalScroll: Double
        val isShifting = event.shiftKey
        val deltaX = event.deltaX

        if (isShifting && deltaX == 0.0) {
            horizontalScroll = event.deltaY
            verticalScroll = 0.0
        } else {
            horizontalScroll = deltaX
            verticalScroll = event.deltaY
        }

        // wheels event own buttons property is unreliable in Safari and Firefox
        // see CMP-9900 [web] Wheel event resolves buttons state incorrectly in Safari and Firefox
        val buttons =
            if (actualActivePointerButtons != PointerButtons()) actualActivePointerButtons else event.composeButtons

        val result = scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = event.offset,
            scrollDelta = Offset(
                x = horizontalScroll.toFloat(),
                y = verticalScroll.toFloat()
            ),
            buttons = buttons,
            keyboardModifiers = PointerKeyboardModifiers(
                isCtrlPressed = event.ctrlKey,
                isMetaPressed = event.metaKey,
                isAltPressed = event.altKey,
                isShiftPressed = isShifting,
            ),
            nativeEvent = event,
            button = event.composeButton,
        )

        if (result.anyChangeConsumed && event.cancelable) {
            event.preventDefault()
        }
    }

    private val MouseEvent.offset
        get() = Offset(
            x = offsetX.toFloat() * density.density,
            y = offsetY.toFloat() * density.density
        )
}

private sealed interface KeyboardModeState {
    object Virtual : KeyboardModeState
    object Hardware : KeyboardModeState
}

private fun <T : Event> EventTargetListener.addTypedEvent(
    type: String,
    passive: Boolean,
    handler: (event: T) -> Unit
) {
    addDisposableEvent(type, passive) { event -> handler(event as T) }
}

private fun releasePointerCapture(target: EventTarget, pointerId: Int) {
    js("try { target.releasePointerCapture(pointerId) } catch (e) {}")
}

private fun setPointerCapture(target: EventTarget, pointerId: Int) {
    js("try { target.setPointerCapture(pointerId) } catch (e) {}")
}

private fun getCoalescedEvents(pointerEvent: PointerEvent): JsArray<PointerEvent> =
    js("pointerEvent.getCoalescedEvents ? pointerEvent.getCoalescedEvents() : []")

private fun PointerEvent.toScenePointerEvent(
    containerOffset: Offset,
    density: Density,
    pointerType: PointerType = PointerType.Touch
): ComposeScenePointer {
    val event = this
    val type = event.getPointerEventType()
    val position = Offset(
        x = (clientXOf(event).toFloat() - containerOffset.x) * density.density,
        y = (clientYOf(event).toFloat() - containerOffset.y) * density.density
    )
    return ComposeScenePointer(
        id = PointerId(event.pointerId.toLong()),
        position = position,
        pressed = type == PointerEventType.Press || type == PointerEventType.Move,
        type = pointerType,
        pressure = event.pressure
    )
}

// `MouseEvent.clientX`/`clientY` are declared as `Int` in the Kotlin DOM bindings, while the CSSOM
// View spec defines them as `double`. Going through the typed accessors truncates the coordinates to
// whole CSS pixels, which on a high-DPI screen quantizes every pointer position to `devicePixelRatio`
// Compose pixels and turns slow, sub-pixel movement into a stream of zero-delta moves. Compose reads
// a move with a zero position delta as "no one is handling this gesture", so an outer scrollable can
// take an ongoing drag away from an inner one - see https://youtrack.jetbrains.com/issue/CMP-10575.
// Read the raw values instead to keep the fractional part.
// language=js
private fun clientXOf(event: PointerEvent): Double = js("event.clientX")

// language=js
private fun clientYOf(event: PointerEvent): Double = js("event.clientY")

// strings checks are faster on a JS side
// language=js
private fun isTouchEvent(event: PointerEvent): Boolean = js("event.pointerType === 'touch'")

// strings checks are faster on a JS side
// language=js
private fun isMouseEvent(event: PointerEvent): Boolean = js("event.pointerType === 'mouse'")

// strings checks are faster on a JS side
// language=js
private fun isPenEvent(event: PointerEvent): Boolean = js("event.pointerType === 'pen'")

// strings checks are faster on a JS side
// language=js
private fun getPointerEventCode(event: PointerEvent): Int = js(
    """{
        switch (event.type) {
          case 'pointerdown':
            return 1; // PointerEventType.Press
          case 'pointerup':
            return 2; // PointerEventType.Release
          case 'pointermove':
            return 3; // PointerEventType.Move
          case 'pointerenter':
            return 4; //PointerEventType.Enter
          case 'pointerleave':
            return 5; //PointerEventType.Exit
          default:
            return 0; // PointerEventType.Unknown
        } 
    }"""
)

private fun PointerEvent.getPointerEventType(): PointerEventType =
    when (getPointerEventCode(this)) {
        PointerEventType.Press.value -> PointerEventType.Press
        PointerEventType.Release.value -> PointerEventType.Release
        PointerEventType.Move.value -> PointerEventType.Move
        PointerEventType.Enter.value -> PointerEventType.Enter
        PointerEventType.Exit.value -> PointerEventType.Exit
        else -> PointerEventType.Unknown
    }

