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

@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package androidx.compose.ui.desktop.wasm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.LocalTextInputSessionOwner
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.draganddrop.WebDragAndDropManager
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.composeButton
import androidx.compose.ui.input.pointer.composeButtons
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.DefaultInputModeManager
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.WebTextToolbar
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.InteropViewGroup
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.WebInteropContainer
import androidx.compose.ui.window.DefaultWindowState
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.getCoalescedEvents
import androidx.compose.ui.window.getPointerEventType
import androidx.compose.ui.window.getSystemThemeObserver
import androidx.compose.ui.window.isTouchEvent
import androidx.compose.ui.window.setPointerCapture
import androidx.compose.ui.window.toScenePointerEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlin.js.js
import kotlin.js.toList
import kotlin.math.absoluteValue
import kotlinx.browser.document
import kotlinx.browser.window as browserWindow
import kotlinx.coroutines.coroutineScope
import kotlinx.io.files.Path
import noria.ui.core.LocalWindow
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

private var nextWindowId = 1L

/**
 * The canvas-hosted [Window]. Rendering, input, IME, and drag-and-drop are wired the same way as
 * the `ComposeViewport` engine ([androidx.compose.ui.window.ComposeWindow]); the scene runs in the
 * [ApplicationSession]'s coroutine context and data-source context like every desktop window.
 *
 * Contract members without a browser counterpart (explicit sizing, z-order, minimize, decorations,
 * file dialogs) are no-ops, matching Noria's implementation of the same facade.
 */
class WasmJsWindow internal constructor(
    private val application: WasmJsApplication,
    val rootElement: HTMLDivElement,
    val canvas: HTMLCanvasElement,
    session: ApplicationSession,
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
) : Window {

    override val id: LightweightWindowId = LightweightWindowId(nextWindowId++)

    private var isDisposed = false

    override val density: Density
        get() = Density(density = browserWindow.devicePixelRatio.toFloat(), fontScale = 1f)

    private val state = DefaultWindowState(rootElement)
    private val canvasEvents = EventTargetListener(canvas)
    private val _windowInfo = WindowInfoImpl()
    private val domFocus = WasmJsDomFocus(canvas).apply {
        onFocusChanged = { focused -> _windowInfo.isWindowFocused = focused }
    }
    private val systemThemeObserver = getSystemThemeObserver()

    private val interopContainerElement = (document.createElement("div") as HTMLDivElement).apply {
        style.position = "absolute"
        style.top = "0"
        style.left = "0"
        rootElement.appendChild(this)
    }
    private val interopContainer = WebInteropContainer(InteropViewGroup(interopContainerElement))

    private var actualActivePointerButtons: PointerButtons? = null
    private var activeTouchOffset: Offset? = null

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner().apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }

    private val textInputSessionOwner = WasmJsTextInputSessionOwner(
        scene = { scene },
        density = { density },
        container = rootElement,
        focus = domFocus,
    )

    private val textInputService = object : WebTextInputService() {
        override val currentTouchOffset: Offset?
            get() = activeTouchOffset

        override val backingDomInputContainer: HTMLElement
            get() = rootElement

        override fun getNewGeometryForBackingInput(rect: Rect): DpRect {
            val dpRect = rect.toDpRect(density)
            return DpRect(DpOffset(dpRect.left, dpRect.top), dpRect.size)
        }

        override fun processKeyboardEvent(keyEvent: KeyEvent): Boolean = scene.sendKeyEvent(keyEvent)
    }

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {
            override val windowInfo get() = _windowInfo
            override val inputModeManager: InputModeManager = DefaultInputModeManager()
            override val architectureComponentsOwner = this@WasmJsWindow.architectureComponentsOwner

            override val dragAndDropManager: PlatformDragAndDropManager = object :
                WebDragAndDropManager(rootElement, canvasEvents, state.globalEvents, density) {
                override val rootDragAndDropNode: ComposeSceneDragAndDropNode
                    get() = scene.rootDragAndDropNode
            }

            override val textToolbar: TextToolbar = WebTextToolbar()

            override val textInputService: WebTextInputService
                get() = this@WasmJsWindow.textInputService

            override val viewConfiguration =
                object : ViewConfiguration by PlatformContext.DefaultViewConfiguration {
                    override val touchSlop: Float get() = with(density) { 18.dp.toPx() }
                    override val maximumFlingVelocity: Float
                        get() = with(density) { 8000.dp.toPx() }
                }

            override fun setPointerIcon(pointerIcon: PointerIcon) {
                if (pointerIcon is BrowserCursor) {
                    canvas.style.cursor = pointerIcon.id
                }
            }

            override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
                coroutineScope {
                    androidx.compose.ui.window.WebTextInputSession(this, this@WasmJsWindow.textInputService)
                        .startInputMethod(request)
                }
            }

            override fun textInputSessionOwner() = this@WasmJsWindow.textInputSessionOwner
        }

    private val skiaLayer: SkiaLayer = SkiaLayer().apply {
        renderDelegate = SkikoRenderDelegate { canvas, _, _, nanoTime ->
            scene.render(canvas.asComposeCanvas(), nanoTime)
        }
    }

    private val scene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        coroutineContext = session.coroutineScope.coroutineContext,
        platformContext = platformContext,
        dataSourceContext = session.dataSourceContext,
        invalidate = skiaLayer::needRender,
    )

    init {
        initEvents()
        state.init()
        canvas.setAttribute("tabindex", "0")
        canvas.setAttribute("draggable", "true")
        application.windows += id to this
    }

    // ----- Rendering and sizing -----

    private fun resize(boxSize: DpSize) {
        val density = density
        val sizeInPx = boxSize.toSize(density).toIntSize()

        canvas.width = sizeInPx.width
        canvas.height = sizeInPx.height
        // Scale canvas to allow high DPI rendering as suggested in
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI.
        canvas.style.width = "${boxSize.width.value}px"
        canvas.style.height = "${boxSize.height.value}px"

        _windowInfo.containerSize = sizeInPx
        _windowInfo.containerDpSize = boxSize

        skiaLayer.attachTo(canvas)
        scene.density = density
        scene.size = sizeInPx
        skiaLayer.needRender()
    }

    // ----- Input events -----

    private fun initEvents() {
        listOf(
            "pointerenter",
            "pointerdown",
            "pointermove",
            "pointerup",
            "pointerleave",
            "pointercancel",
        ).forEach { name ->
            canvasEvents.addDisposableEvent(name, passive = false) { event ->
                onPointerEvent(event as PointerEvent)
            }
        }

        canvasEvents.addDisposableEvent("wheel", passive = false) { event ->
            onWheelEvent(event as WheelEvent)
        }

        canvasEvents.addDisposableEvent("contextmenu") { event ->
            event.preventDefault()
        }

        canvasEvents.addDisposableEvent("keydown") { event ->
            processKeyboardEvent(event as KeyboardEvent)
        }

        canvasEvents.addDisposableEvent("keyup") { event ->
            processKeyboardEvent(event as KeyboardEvent)
        }
    }

    private fun processKeyboardEvent(keyboardEvent: KeyboardEvent) {
        val keyEvent = keyboardEvent.toComposeEvent()
        if (textInputSessionOwner.handleEventWithInputSession(keyEvent)) {
            // The browser must deliver this event to the IME text area so it can produce the
            // corresponding beforeinput/composition events; do not preventDefault.
            return
        }
        val processed = onPreviewKeyEventHandler(keyEvent) ||
            scene.sendKeyEvent(keyEvent) ||
            onKeyEventHandler(keyEvent)
        if (processed) {
            keyboardEvent.preventDefault()
        }
    }

    private class ActiveTouchPointer(
        val containerOffset: Offset,
        var pointer: ComposeScenePointer,
    )

    private val activeTouchPointers = LinkedHashMap<Int, ActiveTouchPointer>()

    private fun onPointerEvent(event: PointerEvent) {
        val eventType = event.getPointerEventType()
        if (isTouchEvent(event)) {
            if (eventType == PointerEventType.Enter || eventType == PointerEventType.Exit) {
                // Enter and Exit events have no sense for touches (Firefox and Safari send them)
                return
            }

            if (platformContext.inputModeManager.inputMode != InputMode.Touch) {
                platformContext.inputModeManager.requestInputMode(InputMode.Touch)
            }

            val active = activeTouchPointers[event.pointerId]
            val current = if (active == null) {
                event.target?.let { setPointerCapture(it, event.pointerId) }
                val containerOffset = canvas.getBoundingClientRect().let {
                    Offset(it.left.toFloat(), it.top.toFloat())
                }
                ActiveTouchPointer(containerOffset, event.toScenePointerEvent(containerOffset, density))
            } else {
                active.pointer = event.toScenePointerEvent(active.containerOffset, density)
                active
            }
            activeTouchPointers[event.pointerId] = current

            activeTouchOffset = current.pointer.position

            val pointers = activeTouchPointers.values.map { it.pointer }.toMutableList()
            val buttons = PointerButtons()
            val keyboardModifiers = PointerKeyboardModifiers()

            // Browsers deliver pointermove at frame cadence and fold the raw input samples into
            // the delivered event; replaying the coalesced samples preserves full-resolution
            // trajectories for velocity tracking and precise gestures, like upstream.
            val coalescedEvents = if (eventType == PointerEventType.Move) {
                getCoalescedEvents(event).toList()
            } else {
                null
            }

            var result: PointerEventResult? = null
            if (coalescedEvents != null && coalescedEvents.size > 1) {
                val indexOfCurrentPointer = pointers.indexOf(current.pointer)
                coalescedEvents.forEach { coalescedEvent ->
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
                        timeMillis = coalescedEvent.timeStamp.toDouble().toLong(),
                        nativeEvent = coalescedEvent,
                        button = null,
                    )
                }
            } else {
                result = scene.sendPointerEvent(
                    eventType = eventType,
                    pointers = pointers,
                    buttons = buttons,
                    keyboardModifiers = keyboardModifiers,
                    scrollDelta = Offset.Zero,
                    timeMillis = event.timeStamp.toDouble().toLong(),
                    nativeEvent = event,
                    button = null,
                )
            }

            activeTouchOffset = null

            if (eventType == PointerEventType.Release) {
                activeTouchPointers.remove(event.pointerId)
            }

            if (result?.anyChangeConsumed == true && event.cancelable) {
                event.preventDefault()
            }
        } else {
            // Validate the event before sending it further - see
            // https://youtrack.jetbrains.com/issue/CMP-8430
            var isValidEvent = true
            when (eventType) {
                PointerEventType.Press -> actualActivePointerButtons = event.composeButtons
                PointerEventType.Release -> actualActivePointerButtons = null
                PointerEventType.Move ->
                    isValidEvent = actualActivePointerButtons == null ||
                        actualActivePointerButtons == event.composeButtons
            }
            if (!isValidEvent) return

            scene.sendPointerEvent(
                eventType = eventType,
                position = event.offset,
                timeMillis = event.timeStamp.toDouble().toLong(),
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
        }
    }

    private fun onWheelEvent(event: WheelEvent) {
        val horizontalScroll = when {
            event.deltaX.absoluteValue >= event.deltaY.absoluteValue -> event.deltaX
            event.shiftKey -> event.deltaY
            else -> 0.0
        }
        val verticalScroll = if (horizontalScroll == 0.0) event.deltaY else 0.0

        // The wheel event's own buttons property is unreliable in Safari and Firefox (CMP-9900).
        val buttons = actualActivePointerButtons ?: event.composeButtons

        val result = scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = event.offset,
            scrollDelta = Offset(
                x = horizontalScroll.toFloat(),
                y = verticalScroll.toFloat(),
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
            y = offsetY.toFloat() * density.density,
        )

    // ----- Window contract -----

    override var title: String
        get() = document.title
        set(value) {
            document.title = value
        }

    override val size: DpSize
        get() = canvas.getBoundingClientRect().let {
            DpSize(it.width.dp, it.height.dp)
        }
    override val contentSize: DpSize get() = size

    override fun requestSize(size: DpSize) {
        // The canvas is sized by its container; explicit sizing has no browser counterpart.
    }

    override val minSize: DpSize get() = size
    override val maxSize: DpSize get() = size
    override fun requestMinSize(minSize: DpSize) {}
    override fun requestMaxSize(maxSize: DpSize) {}

    override val isUserResizable: Boolean get() = false
    override fun requestUserResizable(userResizable: Boolean) {}

    override val screen: Screen get() = WasmJsScreen

    override val isFocused: Boolean get() = domFocus.isFocused

    override fun requestFocus() {
        domFocus.requestFocus()
    }

    override fun requestBringToFront() {}

    override fun requestFocusAndBringToFront() {
        requestFocus()
    }

    override val decoration: WindowDecoration = WindowDecoration.Undecorated()

    override fun requestDecoration(vararg decorations: WindowDecoration) {}

    override val customTitleBarInsets: Pair<Dp, Dp> = 8.dp to 0.dp

    override val systemTheme: SystemTheme
        get() = systemThemeObserver.currentSystemTheme.value

    override fun requestSystemTheme(systemTheme: SystemTheme?) {}

    override fun requestMinimized(minimized: Boolean) {}

    override val placement: WindowPlacement
        get() = if (isFullscreenElement(canvas)) WindowPlacement.Fullscreen else WindowPlacement.Maximized

    override fun requestPlacement(placement: WindowPlacement) {
        when (placement) {
            WindowPlacement.Fullscreen -> requestFullscreen(canvas)
            WindowPlacement.Floating,
            WindowPlacement.Maximized,
            -> exitFullscreen()
        }
    }

    override fun showOpenSingleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): Path? = null

    override fun showOpenMultipleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): List<Path> = emptyList()

    override fun showSaveDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldLabel: String,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
    ): Path? = null

    override fun captureScreenshot(): ImageBitmap {
        TODO("captureScreenshot is not supported in the browser")
    }

    override val nativeWindow: Any get() = rootElement

    override fun requestClose(reason: WindowCloseRequestReason) {
        onCloseRequest(reason)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
        textInputSessionOwner.dispose()
        scene.close()
        skiaLayer.detach()
        systemThemeObserver.dispose()
        canvasEvents.dispose()
        state.dispose()
        domFocus.dispose()
        interopContainerElement.parentNode?.removeChild(interopContainerElement)
        application.windows -= id
    }

    /**
     * Releases everything owned by this window while leaving [rootElement] and [canvas] in the
     * document, so a successor window created by [WasmJsApplication.reuseWindow] can adopt them.
     */
    internal fun disposeForReuse() {
        dispose()
    }

    // ----- Content -----

    private var onPreviewKeyEventHandler: (KeyEvent) -> Boolean = { false }
    private var onKeyEventHandler: (KeyEvent) -> Boolean = { false }

    private val contentState = mutableStateOf<(@Composable WindowScope.() -> Unit)?>(null)
    private var sceneContentInstalled = false

    private fun installSceneContentIfNeeded() {
        if (sceneContentInstalled) return
        sceneContentInstalled = true
        val windowScope = object : WindowScope {
            override val window: Window get() = this@WasmJsWindow
        }
        scene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemThemeObserver.currentSystemTheme.value,
                LocalWindow provides this,
                LocalTextInputSessionOwner provides textInputSessionOwner,
                LocalInteropContainer provides interopContainer,
            ) {
                interopContainer.TrackInteropPlacementContainer {
                    contentState.value?.invoke(windowScope)
                }
                LaunchedEffect(Unit) {
                    state.sizeFlow().collect { size ->
                        resize(DpSize(size.width.dp, size.height.dp))
                    }
                }
            }
        }
    }

    override fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        content: @Composable WindowScope.() -> Unit,
    ) {
        this.onPreviewKeyEventHandler = onPreviewKeyEvent
        this.onKeyEventHandler = onKeyEvent
        contentState.value = content
        installSceneContentIfNeeded()
        skiaLayer.needRender()
    }

    @Composable
    override fun Content(onLayout: (noria.ui.core.WindowData) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
    }
}

private fun isFullscreenElement(element: HTMLElement): Boolean =
    js("document.fullscreenElement === element")

private fun requestFullscreen(element: HTMLElement) {
    js("element.requestFullscreen && element.requestFullscreen()")
}

private fun exitFullscreen() {
    js("document.exitFullscreen && document.fullscreenElement && document.exitFullscreen()")
}
