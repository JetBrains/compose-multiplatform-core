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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.window

import androidx.annotation.VisibleForTesting
import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableIntSetOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.draganddrop.WebDragAndDropManager
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.composeButton
import androidx.compose.ui.input.pointer.composeButtons
import androidx.compose.ui.internal.focusExt
import androidx.compose.ui.navigationevent.BackNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.EmptyPlatformWindowInsets
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WebOutOfFrameExecutor
import androidx.compose.ui.platform.WebHapticFeedback
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.WebTextToolbar
import androidx.compose.ui.platform.WebWakeLockManager
import androidx.compose.ui.platform.WebWindowInsetsManager
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.platform.accessibility.ComposeWebSemanticsListener
import androidx.compose.ui.platform.isPostingTasksSupported
import androidx.compose.ui.platform.installFallbackFontDownloader
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformOutOfFrameExecutor
import androidx.compose.ui.platform.PlatformPrefetchScheduler
import androidx.compose.ui.platform.WebPrefetchScheduler
import androidx.compose.ui.platform.isIdleCallbackSupported
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.scene.SingleComposeSceneRenderingScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.InteropViewGroup
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.WebInteropContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.hostOs
import org.w3c.dom.DocumentReadyState
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.LOADING
import org.w3c.dom.Node
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.FocusEvent
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent

private val actualDensity
    get() = window.devicePixelRatio

internal interface ComposeWindowState {
    fun init() {}
    fun sizeFlow(): Flow<IntSize>

    val globalEvents: EventTargetListener

    fun dispose() {
        globalEvents.dispose()
    }
}

private sealed interface KeyboardModeState {
    object Virtual : KeyboardModeState
    object Hardware : KeyboardModeState
}

internal class DefaultWindowState(private val viewportContainer: Element) : ComposeWindowState {
    private val channel = Channel<IntSize>(CONFLATED)

    override val globalEvents = EventTargetListener(window)

    private var mediaQueryListener: MediaQueryListener? = null

    override fun init() {

        globalEvents.addDisposableEvent("resize") {
            channel.trySend(getParentContainerBox())
        }

        recreateMediaQueryListener()

        channel.trySend(getParentContainerBox())
    }

    private fun getParentContainerBox(): IntSize {
        return IntSize(viewportContainer.clientWidth, viewportContainer.clientHeight)
    }

    private fun recreateMediaQueryListener() {
        mediaQueryListener?.dispose()
        val contentScale = actualDensity
        mediaQueryListener = object : MediaQueryListener("(resolution: ${contentScale}dppx)") {
            override fun onChange(matches: Boolean) {
                if (!matches) {
                    channel.trySend(getParentContainerBox())
                }
                recreateMediaQueryListener()
            }
        }
    }

    override fun dispose() {
        mediaQueryListener?.dispose()
        super.dispose()
    }

    override fun sizeFlow() = channel.receiveAsFlow()
}

@VisibleForTesting
// This value is for internal usage, for example, to call ComposeWindow.dispose() in the tests
internal val LocalComposeWindow: ProvidableCompositionLocal<ComposeWindow?> = staticCompositionLocalOf {
    error("ComposeWindow is not available in this composition")
}

@OptIn(InternalComposeApi::class)
internal class ComposeWindow(
    private val canvas: HTMLCanvasElement,
    private val rootNode: Node,
    private val layerRoot: HTMLElement,
    private val interopContainerElement: HTMLDivElement,
    private val a11yContainerElement: HTMLDivElement?,
    private val configuration: ComposeViewportConfiguration,
    content: @Composable () -> Unit,
    private val state: ComposeWindowState
) {
    private var isDisposed = false

    private var actualActivePointerButtons: PointerButtons = PointerButtons()

    private val density: Density = Density(
        density = actualDensity.toFloat(),
        fontScale = 1f
    )

    private val _windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }

    @VisibleForTesting
    internal val archComponentsOwner = DefaultArchitectureComponentsOwner()

    private val navigationEventInput = BackNavigationEventInput()

    private val webOutOfFrameExecutor by lazy(LazyThreadSafetyMode.NONE) {
        if (isPostingTasksSupported) WebOutOfFrameExecutor() else null
    }

    private val canvasEvents = EventTargetListener(canvas)

    private var insetsManager: WebWindowInsetsManager? = null

    private var keyboardModeState: KeyboardModeState = KeyboardModeState.Hardware

    // Used in WebTextInputService. Also see https://youtrack.jetbrains.com/issue/CMP-8611
    private var activeTouchOffset: Offset = Offset.Unspecified

    private val clipTarget = clipTargetElement(canvas)

    // TODO: It must be shared between Compose instances.
    //  It's supposed to be stored in platform's root view or window.
    private val frameRecomposer = FrameRecomposer(Dispatchers.Main, invalidate = { skiaLayer.needRender() })

    // TODO: It cannot be used in case of shared [FrameRecomposer], replace this helper with calling
    //  - [frameRecomposer.performFrame] once per frame (across all instances) before platform views layout phase
    //  - [scene.measureAndLayout] during platform views layout phase. Note that it should be triggered
    //    by platform view invalidation (which is triggered by [scene.invalidateLayout] OR by regular platform invalidation)
    //  - [scene.draw] during drawing phase of platform views (which is triggered by [scene.invalidateDraw]).
    //    Note that in case of custom GPU surface/V-Sync handling, it needs to be handled differently.
    private val sceneRenderingScope = SingleComposeSceneRenderingScope { skiaLayer.needRender() }

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {

            override val outOfFrameExecutor: PlatformOutOfFrameExecutor? get() = webOutOfFrameExecutor

            override val windowInfo get() = _windowInfo
            override val architectureComponentsOwner get() = archComponentsOwner
            override val windowInsets get() = insetsManager?.windowInsets ?: EmptyPlatformWindowInsets

            override val dragAndDropManager: PlatformDragAndDropManager = object :
                WebDragAndDropManager(rootNode, canvasEvents, state.globalEvents, density) {
                override val rootDragAndDropNode: ComposeSceneDragAndDropNode
                    get() = scene.rootDragAndDropNode
            }

            @Suppress("RedundantOverride")
            override fun convertLocalToWindowPosition(localPosition: Offset): Offset {
                // TODO (o.karpovich): Currently, CfW uses AttachedComposeSceneLayer, so
                // Window Rect == Canvas Rect, although a canvas might take only a portion of the browser's
                // viewport: Window Rect > Canvas Rect.
                // Update this implementation when implementing https://youtrack.jetbrains.com/issue/CMP-8359
                // The implementation will have to rely on the <canvas> of a particular layer.
                return super.convertLocalToWindowPosition(localPosition)
            }

            @Suppress("RedundantOverride")
            override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset {
                // TODO (o.karpovich): Currently, CfW uses AttachedComposeSceneLayer, so
                // Window Rect == Canvas Rect, although a canvas might take only a portion of the browser's
                // viewport: Window Rect > Canvas Rect.
                // Update this implementation when implementing https://youtrack.jetbrains.com/issue/CMP-8359
                return super.convertWindowToLocalPosition(positionInWindow)
            }

            override val textToolbar: TextToolbar by lazy(LazyThreadSafetyMode.NONE) {
                WebTextToolbar()
            }

            override val hapticFeedback by lazy(LazyThreadSafetyMode.NONE) {
                WebHapticFeedback.webHapticFeedbackOrDefault()
            }

            override val prefetchScheduler: PlatformPrefetchScheduler =
                if (isIdleCallbackSupported) WebPrefetchScheduler() else super.prefetchScheduler

            override val semanticsOwnerListener: PlatformContext.SemanticsOwnerListener? =
                if (configuration.isA11YEnabled) {
                    ComposeWebSemanticsListener(
                        webSemanticsRoot = a11yContainerElement?.apply {
                            setAttribute("aria-label", "")
                            setAttribute("role", "presentation")
                            setAttribute("aria-live", "polite")
                            id = "cmp_a11y_root"
                            style.opacity = "0"
                            style.setProperty("pointer-events", "none")
                        } ?: error("a11yContainerElement must be provided"),
                    )
                } else {
                    null
                }

            override val textInputService: WebTextInputService by lazy(LazyThreadSafetyMode.NONE) {
                object : WebTextInputService() {

                    override val currentTouchOffset: Offset
                        get() = activeTouchOffset

                    override val backingDomInputContainer: HTMLElement
                        get() = layerRoot

                    override fun getNewGeometryForBackingInput(rect: Rect): DpRect {
                        val dpRect = rect.toDpRect(density)
                        val left = dpRect.left.value
                        val top = dpRect.top.value

                        return DpRect(DpOffset(left.dp, top.dp), dpRect.size)
                    }

                    override fun processKeyboardEvent(keyEvent: KeyEvent): Boolean {
                        //this@ComposeWindow.processKeyboardEvent(keyboardEvent)
                        return scene.sendKeyEvent(keyEvent)
                    }

                    override fun stopInput() {
                        // super.stopInput() will remove the focused backing html input.
                        // Once a focused HTML element is removed, the browser moves focus to <body>.
                        // Focus the canvas first so it retains focus after the backing input is removed:
                        if (!canvas.isFocused()) {
                            canvas.focus()
                        }
                        super.stopInput()
                    }
                }
            }

            override val viewConfiguration =
                object : ViewConfiguration by PlatformContext.DefaultViewConfiguration {
                    // Aligning the touchSlop value with the Android default:
                    // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/ViewConfiguration.java?pli=1#191
                    override val touchSlop: Float get() = with(density) { 8.dp.toPx() }
                    override val maximumFlingVelocity: Float
                        //https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/view/ViewConfiguration.java;l=240;drc=733537294b158d22f2ae383f2ed77c93741798e9
                        get() = with(density) { 8000.dp.toPx() }
                }

            override var isKeepScreenOnEnabled: Boolean
                get() = WebWakeLockManager.isWakeLockActive()
                set(value) = WebWakeLockManager.sendWakeLockRequest(this@ComposeWindow, value)

            override fun setPointerIcon(pointerIcon: PointerIcon) {
                if (pointerIcon is BrowserCursor) {
                    canvas.style.cursor = pointerIcon.id
                }
            }

            override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
                coroutineScope {
                    WebTextInputSession(this, textInputService)
                        .startInputMethod(request)
                }
            }

            override val isClearFocusOnMouseDownEnabled: Boolean
                get() = configuration.isClearFocusOnMouseDownEnabled
        }

    private val skiaLayer: SkiaLayer = SkiaLayer().apply {
        renderDelegate = SkikoRenderDelegate { canvas, _, _, nanoTime ->
            with(sceneRenderingScope) {
                scene.render(frameRecomposer, canvas.asComposeCanvas(), nanoTime)
            }
        }
    }

    init {
        registerSkikoComposeImplementation()
    }

    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        platformContext = platformContext,
        density = density,
        // TODO: Split layout invalidation from draw invalidation once the web host has distinct
        //  scheduling paths for relayout vs redraw.
        invalidateLayout = sceneRenderingScope::onSceneInvalidation,
        invalidateDraw = sceneRenderingScope::onSceneInvalidation,
    )

    private val systemThemeObserver = getSystemThemeObserver()

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

    private fun processKeyboardEvent(keyboardEvent: KeyboardEvent) {
        val keyEvent = keyboardEvent.toComposeEvent()
        val processed = scene.sendKeyEvent(keyEvent) ||
            navigationEventInput.onKeyEvent(keyEvent)

        if (processed) {
            keyboardEvent.preventDefault()
        } else if (keyEvent.type == KeyEventType.KeyDown) {
            processClipKeyDown(keyEvent)
        }
    }

    private val isMacOS = hostOs.isMacOS

    private var canvasFocused = false

    private fun processClipKeyDown(keyEvent: KeyEvent) {
        val mod = if (isMacOS) keyEvent.isMetaPressed else keyEvent.isCtrlPressed
        if (!mod) return
        if (keyEvent.key == Key.C || keyEvent.key == Key.V || keyEvent.key == Key.X) {
            // A browser is about to dispatch a Clipboard Event.
            // Some browsers do not dispatch Clipboard events to <canvas> despite it having focus,
            // so let it dispatch the event to clipTarget (text area).
            // By focusing on it, we let a browser dispatch the event to it.
            layerRoot.appendChild(clipTarget)
            focusExt(clipTarget, true)
        }
    }

    // It helps Compose to co-operate with the browser's scroll when the ComposeViewport
    // is nested in a scrollable html container
    private val rootScrollObserver = RootScrollObserver()

    private fun initEvents(canvas: HTMLCanvasElement) {

        val onPointerCallback: (PointerEvent) -> Unit = { onPointerEvent(it) }
        listOf(
            "pointerenter",
            "pointerdown",
            "pointermove",
            "pointerup",
            "pointerleave",
            "pointercancel"
        ).fastForEach { name ->
            addTypedEvent<PointerEvent>(name, passive = false, handler = onPointerCallback)
        }

        state.globalEvents.addDisposableEvent("dragend") {
            // in Safari pointerup event is not firing when we drop or cancel drop
            // see https://youtrack.jetbrains.com/issue/CMP-10102
            actualActivePointerButtons = PointerButtons()
        }

        val webTextInputService = platformContext.textInputService as WebTextInputService

        addTypedEvent<TouchEvent>("touchstart", passive = false) { evt ->
            // preventDefault the touchstart if the corresponding pointerdown hits the active text input.
            // Pros: a long press (touchstart + ~500ms delay after it) triggers focus changes in iOS Safari,
            // and this is the only chance (the only event we have) to prevent that behavior,
            // since we want the focus to remain in the active text input.
            // Cons: the browser's gestures (e.g., pull-to-refresh) become suppressed while
            // the pointer is in the active text input.
            val isTextFieldActive = webTextInputService.getBackingInput()?.isFocused() ?: false
            if (isTextFieldActive) {
                val position = activeTouchPointers[lastPointerDownId]?.composePointer?.position
                if (webTextInputService.hitTest(position ?: Offset.Unspecified)) {
                    evt.preventDefault()
                }
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

        addTypedEvent<TouchEvent>("touchend", passive = false) { evt ->
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
        addTypedEvent<TouchEvent>("touchmove", passive = false) { evt ->
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

        addTypedEvent<WheelEvent>("wheel", passive = false) { event ->
            onWheelEvent(event)
        }

        canvas.addEventListener("contextmenu", { event ->
            event.preventDefault()
        })

        val onKeyboardEventCallback: (KeyboardEvent) -> Unit = { event ->
            processKeyboardEvent(event)
        }
        addTypedEvent<KeyboardEvent>("keydown", onKeyboardEventCallback)
        addTypedEvent<KeyboardEvent>("keyup", onKeyboardEventCallback)

        addTypedEvent<FocusEvent>("focus") { event ->
            canvasFocused = true
        }

        addTypedEvent<FocusEvent>("blur") { event ->
            canvasFocused = false
        }

        state.globalEvents.addDisposableEvent("focus") {
            archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        state.globalEvents.addDisposableEvent("blur") {
            archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }

        state.globalEvents.addDisposableEvent("visibilitychange") { event ->
            archComponentsOwner.lifecycle.handleLifecycleEvent(
                if (documentIsVisible()) Lifecycle.Event.ON_START
                else Lifecycle.Event.ON_STOP
            )
        }
    }

    init {
        if (configuration.enableBrowserWindowInsets) {
            checkViewportFitCover()
            insetsManager = WebWindowInsetsManager(density, canvas)
        }

        initEvents(canvas)
        state.init()


        scene.density = density
        archComponentsOwner.enableSavedStateHandles()

        val interopContainer = WebInteropContainer(InteropViewGroup(interopContainerElement))

        val clipEventsTargetProvider: () -> HTMLElement = {
            (platformContext.textInputService as WebTextInputService).getBackingInput()
                ?: clipTarget
        }
        scene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemThemeObserver.currentSystemTheme.value,
                LocalInteropContainer provides interopContainer,
                LocalActiveClipEventsTarget provides clipEventsTargetProvider,
                LocalComposeWindow provides this,
                content = {
                    installFallbackFontDownloader()
                    WithNestedScrollObserver(rootScrollObserver) {
                        interopContainer.TrackInteropPlacementContainer {
                            content()
                        }
                    }

                    LaunchedEffect(Unit) {
                        state.sizeFlow().collect { size ->
                            // Convert to proper type: IntSize was exposed to public API with meaning of DPs.
                            val boxSize = DpSize(size.width.dp, size.height.dp)
                            this@ComposeWindow.resize(boxSize)
                        }
                    }

                    val webSemanticsListener = platformContext.semanticsOwnerListener as? ComposeWebSemanticsListener
                    if (webSemanticsListener != null) {
                        LaunchedEffect(Unit) {
                            coroutineScope {
                                // The initial composition would create a lot of noisy invalidations,
                                // so it makes sense to start the listener here - after the initial composition.
                                // The composition's coroutine scope ties the listener's lifetime to the composition.
                                webSemanticsListener.start(this)
                            }
                        }
                    }
                }
            )
        }

        archComponentsOwner.lifecycle.handleLifecycleEvent(
            if (document.hasFocus()) Lifecycle.Event.ON_RESUME
            else Lifecycle.Event.ON_START
        )
        archComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.addInput(navigationEventInput)
    }

    private fun resize(boxSize: DpSize) {
        val sizeInPx = boxSize.toSize(density).toIntSize()

        // we need to scale canvas both via CSS styling and HTML attributes
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI
        canvas.width = sizeInPx.width
        canvas.height = sizeInPx.height

        // The a11y container is sized via CSS (`width: 100%; height: 100%`) at construction
        // time, so it tracks the canvas automatically without a per-resize bridge call across
        // the wasm2js boundary. See ComposeViewport for the setup.

        _windowInfo.containerSize = sizeInPx
        _windowInfo.containerDpSize = boxSize

        // TODO: Align with Container/Mediator architecture
        skiaLayer.attachTo(canvas)
        scene.size = sizeInPx
        skiaLayer.needRender()

        insetsManager?.onCanvasResized(canvas)
    }

    // This is called automatically on destroying the corresponding DOM container
    fun dispose() {
        // dispose() can be called both explicitly and via the DOM disconnectedCallback
        // when the container element is removed from the document. Make it idempotent so
        // the auto-dispose fallback doesn't fail after an explicit dispose.
        if (isDisposed) return
        (platformContext.prefetchScheduler as? WebPrefetchScheduler)?.dispose()
        archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        archComponentsOwner.viewModelStore.clear()
        archComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.removeInput(navigationEventInput)

        webOutOfFrameExecutor?.dispose()
        scene.close()
        frameRecomposer.close()
        skiaLayer.detach()

        insetsManager?.dispose()
        systemThemeObserver.dispose()
        state.dispose()
        // modern browsers supposed to garbage collect all events on the element disposed
        // but actually we never can be sure dom element was collected in first place
        canvasEvents.dispose()
        isDisposed = true
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

            // iOS Safari doesn't request focus when the page is shown,
            // and the lifecycle doesn't trigger ON_RESUME.
            // so, we decided to handle every touch
            archComponentsOwner.lifecycle.currentState = Lifecycle.State.RESUMED

            val inputModeManager = platformContext.inputModeManager
            if (inputModeManager.inputMode != InputMode.Touch) {
                inputModeManager.requestInputMode(InputMode.Touch)
            }
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
        val buttons = if(actualActivePointerButtons != PointerButtons()) actualActivePointerButtons else event.composeButtons

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

    companion object {
        private val DomDisposableRegistry = WeakMap<JsAny>()

        fun registerDisposableFor(element: HTMLElement, handler: () -> Unit) {
            DomDisposableRegistry.set(element, {
                handler()
                DomDisposableRegistry.delete(element)
            })
        }

        fun createComposeComponent(): HTMLElement {
            if (customElements.get("compose-component") == null) {
                customElements.define(
                    "compose-component",
                    composeComponentElementCtor(DomDisposableRegistry)
                )
            }

            return document.createElement("compose-component") as HTMLElement
        }
    }
}

//https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState
internal fun documentIsVisible(): Boolean = js("document.visibilityState === 'visible'")

// In K/JS target, an application can't start right away. We should wait until skiko.wasm is ready.
// We'll do it implicitly, rather than asking the app developers to call it.
internal fun onSkikoReady(block: () -> Unit) {
    @Suppress("INVISIBLE_REFERENCE")
    org.jetbrains.skiko.wasm.onWasmReady { block() }
}

internal fun onDomReady(block: () -> Unit) {
    // https://developer.mozilla.org/en-US/docs/Web/API/Document/DOMContentLoaded_event
    if (document.readyState == DocumentReadyState.LOADING) {
        document.addEventListener("DOMContentLoaded", {
            block()
        })
    } else {
        block()
    }
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

// language=js
private fun checkViewportFitCover(): Unit = js(
    """(function() {
        let meta = document.querySelector('meta[name=viewport]');
        let content = meta ? (meta.getAttribute('content') || '') : '';
        if (!content.includes('viewport-fit=cover')) {
            console.warn(
                "[ComposeWeb] enableBrowserWindowInsets is set to true, but " +
                "'viewport-fit=cover' is not found in the viewport meta tag. " +
                "Safe area insets will be zero. Add viewport-fit=cover to your viewport meta tag: " +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
            );
        }
    })()"""
)

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

private fun Element.isFocused(): Boolean {
    val activeElement = when {
        document.activeElement?.shadowRoot != null -> (document.activeElement?.shadowRoot as? ShadowRootExt)?.activeElement
        else -> document.activeElement
    }

    if (activeElement == null) {
        return false
    }

    return activeElement == this
}

private external interface ShadowRootExt {
    val activeElement: Element?
}

// A real ES6 `class extends HTMLElement` is required by `customElements.define`.
// Kotlin/Wasm does not emit Kotlin classes as JS constructors, so we create the
// constructor in JS and return it as a JS function so it can be passed directly
// to `customElements.define`.
@OptIn(ExperimentalWasmJsInterop::class)
private fun composeComponentElementCtor(weakMap: WeakMap<JsAny>): JsAny = js("""
    (() => {
        class ComposeComponentElement extends HTMLElement {
            disconnectedCallback() {
                const cb = weakMap.get(this);
                if (cb) cb();
            }
        }
        return ComposeComponentElement;
    })()
""")


// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
// Kotlin/Wasm JS interop  only allows type parameters with an upper bound of `JsAny` or its subtypes,
// while the actual value stored here is a Kotlin function type `() -> Unit`.
@OptIn(ExperimentalWasmJsInterop::class)
private external class WeakMap<K : JsAny>(): JsAny {
    fun get(key: K): (() -> Unit)?
    fun set(key: K, value: () -> Unit): WeakMap<K>
    fun has(key: K): Boolean
    fun delete(key: K): Boolean
}

private external interface CustomElementRegistry : JsAny {
    fun get(name: String): JsAny?
    fun define(name: String, constructor: JsAny)
}

private external val customElements: CustomElementRegistry
