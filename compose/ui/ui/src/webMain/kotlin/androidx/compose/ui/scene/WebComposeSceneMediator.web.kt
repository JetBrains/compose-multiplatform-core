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

package androidx.compose.ui.scene

import androidx.collection.mutableIntObjectMapOf
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.composeButton
import androidx.compose.ui.input.pointer.composeButtons
import androidx.compose.ui.internal.focusExt
import androidx.compose.ui.navigationevent.BackNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import kotlin.coroutines.CoroutineContext
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.hostOs
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.FocusEvent
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

/**
 * Owns the [SkiaLayer]/[FrameRecomposer] rendering setup and all DOM pointer/keyboard/wheel/touch
 * handling bound to a single `<canvas>` element, so the same setup can be reused for the main
 * window canvas and for per-[ComposeSceneLayer] canvases (see `CMP-8359-plan.md`).
 *
 * [scene] must be assigned before the first [resize] or DOM event is dispatched.
 */
internal class WebComposeSceneMediator(
    private val canvas: HTMLCanvasElement,
    coroutineContext: CoroutineContext,
    private val archComponentsOwner: DefaultArchitectureComponentsOwner,
    private val navigationEventInput: BackNavigationEventInput,
    private val globalEvents: EventTargetListener,
    private val clipTargetContainer: HTMLElement,
    private val density: Density,
    private val isBackingInputFocused: () -> Boolean,
    private val requestTouchInputMode: () -> Unit,
) {
    lateinit var scene: ComposeScene

    val frameRecomposer = FrameRecomposer(coroutineContext, invalidate = { skiaLayer.needRender() })

    private val renderingScope = SingleComposeSceneRenderingScope { skiaLayer.needRender() }

    val invalidateLayout: () -> Unit = renderingScope::onSceneInvalidation
    val invalidateDraw: () -> Unit = renderingScope::onSceneInvalidation

    private val skiaLayer: SkiaLayer = SkiaLayer().apply {
        renderDelegate = SkikoRenderDelegate { canvas, _, _, nanoTime ->
            with(renderingScope) {
                scene.render(frameRecomposer, canvas.asComposeCanvas(), nanoTime)
            }
        }
    }

    val canvasEvents = EventTargetListener(canvas)

    private var actualActivePointerButtons: PointerButtons? = null

    private var keyboardModeState: KeyboardModeState = KeyboardModeState.Hardware

    private val isMacOS = hostOs.isMacOS

    private var canvasFocused = false

    // Used in WebTextInputService. Also see https://youtrack.jetbrains.com/issue/CMP-8611
    var activeTouchOffset: Offset? = null
        private set

    val clipTarget: HTMLTextAreaElement = clipTargetElement(canvas)

    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    private var onKeyEvent: (KeyEvent) -> Boolean = { false }

    fun setKeyEventListeners(
        onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
        onKeyEvent: (KeyEvent) -> Boolean = { false },
    ) {
        this.onPreviewKeyEvent = onPreviewKeyEvent
        this.onKeyEvent = onKeyEvent
    }

    init {
        canvas.setAttribute("tabindex", "0")
        canvas.setAttribute("draggable", "true")
        initEvents(canvas)
    }

    private fun <T : Event> addTypedEvent(
        type: String,
        handler: (event: T) -> Unit
    ) {
        canvasEvents.addDisposableEvent(type) { event -> handler(event as T) }
    }

    private fun <T : Event> addTypedEvent(
        type: String,
        passive: Boolean,
        handler: (event: T) -> Unit
    ) {
        canvasEvents.addDisposableEvent(type, passive) { event -> handler(event as T) }
    }

    private fun processClipKeyDown(keyEvent: KeyEvent) {
        val mod = if (isMacOS) keyEvent.isMetaPressed else keyEvent.isCtrlPressed
        if (!mod) return
        if (keyEvent.key == Key.C || keyEvent.key == Key.V || keyEvent.key == Key.X) {
            // A browser is about to dispatch a Clipboard Event.
            // Some browsers do not dispatch Clipboard events to <canvas> despite it having focus,
            // so let it dispatch the event to clipTarget (text area).
            // By focusing on it, we let a browser dispatch the event to it.
            clipTargetContainer.appendChild(clipTarget)
            focusExt(clipTarget, true)
        }
    }

    private fun processKeyboardEvent(keyboardEvent: KeyboardEvent) {
        val keyEvent = keyboardEvent.toComposeEvent()
        val processed = onPreviewKeyEvent(keyEvent) ||
            scene.sendKeyEvent(keyEvent) ||
            onKeyEvent(keyEvent) ||
            navigationEventInput.onKeyEvent(keyEvent)

        if (processed) {
            keyboardEvent.preventDefault()
        } else if (keyEvent.type == KeyEventType.KeyDown) {
            processClipKeyDown(keyEvent)
        }
    }

    private fun initEvents(canvas: HTMLCanvasElement) {

        listOf(
            "pointerenter",
            "pointerdown",
            "pointermove",
            "pointerup",
            "pointerleave",
            "pointercancel"
        ).forEach { name ->
            addTypedEvent<PointerEvent>(name, passive = false) { onPointerEvent(it) }
        }

        globalEvents.addDisposableEvent("dragend") {
            // in Safari pointerup event is not firing when we drop or cancel drop
            // see https://youtrack.jetbrains.com/issue/CMP-10102
            actualActivePointerButtons = null
        }

        addTypedEvent<TouchEvent>("touchstart") { evt ->
            // in most cases we don't care about touches since in Compose we do not process them at all
            // there's one case however when we need to cancel them - it's when we are focussed in a DOM backing field
            // see https://youtrack.jetbrains.com/issue/CMP-10079

            if (isBackingInputFocused()) {
                evt.preventDefault()
            }
        }

        addTypedEvent<WheelEvent>("wheel", passive = false) { event ->
            onWheelEvent(event)
        }

        canvas.addEventListener("contextmenu", { event ->
            event.preventDefault()
        })

        addTypedEvent<KeyboardEvent>("keydown") { event ->
            processKeyboardEvent(event)
        }

        addTypedEvent<KeyboardEvent>("keyup") { event ->
            processKeyboardEvent(event)
        }

        addTypedEvent<FocusEvent>("focus") { event ->
            canvasFocused = true
        }

        addTypedEvent<FocusEvent>("blur") { event ->
            canvasFocused = false
        }
    }

    fun resize(sizeInPx: IntSize) {
        // we need to scale canvas both via CSS styling and HTML attributes
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI
        canvas.width = sizeInPx.width
        canvas.height = sizeInPx.height

        skiaLayer.attachTo(canvas)
        scene.size = sizeInPx
        skiaLayer.needRender()
    }

    fun dispose() {
        frameRecomposer.close()
        skiaLayer.detach()
        // modern browsers supposed to garbage collect all events on the element disposed
        // but actually we never can be sure dom element was collected in first place
        canvasEvents.dispose()
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
                activeTouchOffset = null
            } else {
                actualActivePointerButtons = null
            }

            event.target?.let { releasePointerCapture(it, event.pointerId) }

            scene.cancelPointerInput()
            return
        }

        val eventType = event.getPointerEventType()
        var result: PointerEventResult? = null

        if (isMouseEvent(event)) {
            keyboardModeState = KeyboardModeState.Hardware

            // Track active mouse buttons. Used as a fallback for unreliable
            // `buttons` in wheel events (see CMP-9900) and to reset state on
            // `dragend` in Safari (see CMP-10102).
            when (eventType) {
                PointerEventType.Press -> {
                    actualActivePointerButtons = event.composeButtons
                }
                PointerEventType.Release -> {
                    actualActivePointerButtons = null
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

            // iOS Safari doesn't request focus when the page is shown,
            // and the lifecycle doesn't trigger ON_RESUME.
            // so, we decided to handle every touch
            archComponentsOwner.lifecycle.currentState = Lifecycle.State.RESUMED

            requestTouchInputMode()
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
                    val sceneEvent = coalescedEvent.toScenePointerEvent(current.containerOffset, density)
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
            }

            activeTouchOffset = null

            if (eventType == PointerEventType.Release) {
                activeTouchPointers.remove(event.pointerId)
            }

            if (result != null && result.anyChangeConsumed && event.cancelable) {
                event.preventDefault()
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
        if (event.shiftKey && event.deltaX == 0.0) {
            horizontalScroll = event.deltaY
            verticalScroll = 0.0
        } else {
            horizontalScroll = event.deltaX
            verticalScroll = event.deltaY
        }

        // wheels event own buttons property is unreliable in Safari and Firefox
        // see CMP-9900 [web] Wheel event resolves buttons state incorrectly in Safari and Firefox
        val buttons = actualActivePointerButtons ?: event.composeButtons

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
                isShiftPressed = event.shiftKey,
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
        x = (event.clientX - containerOffset.x) * density.density,
        y = (event.clientY - containerOffset.y) * density.density
    )
    return ComposeScenePointer(
        id = PointerId(event.pointerId.toLong()),
        position = position,
        pressed = type == PointerEventType.Press || type == PointerEventType.Move,
        type = pointerType,
        pressure = event.pressure
    )
}

/**
 * The purpose of the clipTarget element is to briefly steal the focus to let the browser dispatch
 * ClipboardEvent to it. Then it returns the focus to the canvas.
 */
private fun clipTargetElement(canvas: HTMLCanvasElement): HTMLTextAreaElement {
    val clipTarget = (document.createElement("textarea") as HTMLTextAreaElement).apply {
        tabIndex = -1
        setAttribute("aria-hidden", "true")
        style.position = "fixed"
        style.left = "-1000px"
        style.top = "0"
        style.opacity = "0"
        style.width = "1px"
        style.height = "1px"
    }

    val clipEventListener: (Event) -> Unit = { _ ->
        window.requestAnimationFrame {
            focusExt(canvas, true)
            clipTarget.remove()
        }
    }

    // Here just return the focus to canvas.
    // For the actual event handling see rememberClipboardEventsHandler implementations.
    clipTarget.addEventListener("copy", clipEventListener)
    clipTarget.addEventListener("cut", clipEventListener)
    clipTarget.addEventListener("paste", clipEventListener)

    return clipTarget
}

// strings checks are faster on a JS side
// language=js
private fun isTouchEvent(event: PointerEvent): Boolean = js("event.pointerType === 'touch'")

// strings checks are faster on a JS side
// language=js
private fun isMouseEvent(event: PointerEvent): Boolean = js("event.pointerType === 'mouse'")

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
