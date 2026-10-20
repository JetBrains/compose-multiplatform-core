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

package androidx.compose.ui.window

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.asComposeSystemTheme
import androidx.compose.ui.draganddrop.WebDragAndDropManager
import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SkiaCanvasHolder
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
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
import androidx.compose.ui.platform.PlatformScreenReader
import androidx.compose.ui.platform.WebPrefetchScheduler
import androidx.compose.ui.platform.isIdleCallbackSupported
import androidx.compose.ui.platform.isVibrationSupported
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
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
import androidx.compose.ui.viewinterop.InteropViewGroup
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.WebInteropContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@VisibleForTesting
// This value is for internal usage, for example, to call ComposeWindow.dispose() in the tests
// `null` when Compose is not hosted by a ComposeWindow, e.g. in some tests.
internal val LocalComposeWindow: ProvidableCompositionLocal<ComposeWindow?> =
    staticCompositionLocalOf {
        null
    }

@OptIn(InternalComposeApi::class)
internal class ComposeWindow(
    private val canvas: HTMLCanvasElement,
    private val rootNode: Node,
    private val layerRoot: HTMLElement,
    private val viewportContainer: Element,
    private val interopContainerElement: HTMLDivElement,
    private val a11yContainerElement: HTMLDivElement?,
    private val configuration: ComposeViewportConfiguration,
    content: @Composable () -> Unit,
    private val state: ComposeWindowState
) {
    private var isDisposed = false

    private var density: Density = Density(
        density = actualDensity.toFloat(),
        fontScale = 1f
    )

    private val _windowInfo = WindowInfoImpl().apply {
        isWindowFocused = document.hasFocus()
    }

    @VisibleForTesting
    internal val archComponentsOwner = DefaultArchitectureComponentsOwner()

    @VisibleForTesting
    internal val webSemanticsListener: ComposeWebSemanticsListener?
        get() = platformContext.semanticsOwnerListener as? ComposeWebSemanticsListener

    private val navigationEventInput = BackNavigationEventInput()

    private val webOutOfFrameExecutor by lazy(LazyThreadSafetyMode.NONE) {
        if (isPostingTasksSupported) WebOutOfFrameExecutor() else null
    }

    private val canvasEvents = EventTargetListener(canvas)

    private var insetsManager: WebWindowInsetsManager? = null

    private val clipboardEventBridge = WebClipboardEventBridge(
        canvas = canvas,
        layerRoot = layerRoot,
        backingInputProvider = {
            (platformContext.textInputService as WebTextInputService).getBackingInput()
        }
    )

    // TODO: It must be shared between Compose instances.
    //  It's supposed to be stored in platform's root view or window.
    private val frameRecomposer =
        FrameRecomposer(Dispatchers.Main, invalidate = { skiaLayer.needRender() })

    // TODO: It cannot be used in case of shared [FrameRecomposer], replace this helper with calling
    //  - [frameRecomposer.performFrame] once per frame (across all instances) before platform views layout phase
    //  - [scene.measureAndLayout] during platform views layout phase. Note that it should be triggered
    //    by platform view invalidation (which is triggered by [scene.invalidateLayout] OR by regular platform invalidation)
    //  - [scene.draw] during drawing phase of platform views (which is triggered by [scene.invalidateDraw]).
    //    Note that in case of custom GPU surface/V-Sync handling, it needs to be handled differently.
    private val sceneRenderingScope = SingleComposeSceneRenderingScope { skiaLayer.needRender() }

    private val webTextToolbar = WebTextToolbar()

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {

            override val outOfFrameExecutor: PlatformOutOfFrameExecutor? get() = webOutOfFrameExecutor

            override val windowInfo get() = _windowInfo

            override val screenReader: PlatformScreenReader
                get() = object : PlatformScreenReader {
                    override var isActive = configuration.isA11YEnabled
                }

            override val architectureComponentsOwner get() = archComponentsOwner
            override val windowInsets
                get() = insetsManager?.windowInsets ?: EmptyPlatformWindowInsets

            override val dragAndDropManager: PlatformDragAndDropManager = object :
                WebDragAndDropManager(rootNode, canvasEvents, state.globalEvents, { density }) {
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

            override val textToolbar: TextToolbar get() = webTextToolbar

            override val hapticFeedback by lazy(LazyThreadSafetyMode.NONE) {
                if (isVibrationSupported()) WebHapticFeedback() else super.hapticFeedback
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
                        get() = pointerInputHandler.activeTouchOffset

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

    private val canvasHolder = SkiaCanvasHolder()

    internal val htmlCanvas: HTMLCanvasElement get() = canvas

    /**
     * Skia's GPU context, captured from the surface canvas on the first rendered frame;
     * It's used by WebGL texture adoption demo.
     */
    internal var skiaDirectContext: DirectContext? = null
        private set

    private val skiaLayer: SkiaLayer = SkiaLayer().apply {
        renderDelegate = SkikoRenderDelegate { canvas, _, _, nanoTime ->
            if (skiaDirectContext == null) {
                skiaDirectContext = canvas.recordingContext
            }
            canvasHolder.drawInto(canvas) {
                with(sceneRenderingScope) {
                    scene.render(frameRecomposer, this@drawInto, nanoTime)
                }
            }
        }
    }

    init {
        registerSkikoComposeImplementation()
    }

    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        platformContext = platformContext,
        density = density, // initial density
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

    private fun processKeyboardEvent(keyboardEvent: KeyboardEvent) {
        val keyEvent = keyboardEvent.toComposeEvent()
        val processed = scene.sendKeyEvent(keyEvent) ||
            navigationEventInput.onKeyEvent(keyEvent)

        if (processed) {
            keyboardEvent.preventDefault()
        } else if (keyEvent.type == KeyEventType.KeyDown) {
            clipboardEventBridge.processKeyDown(keyEvent)
        }
    }

    // It helps Compose to co-operate with the browser's scroll when the ComposeViewport
    // is nested in a scrollable html container
    private val rootScrollObserver = RootScrollObserver()

    private val pointerInputHandler = object : WebPointerInputHandler(canvas, scene) {
        override val density: Density
            get() = this@ComposeWindow.density

        override val rootScrollObserver: RootScrollObserver
            get() = this@ComposeWindow.rootScrollObserver

        override fun beforePointerEvent(pointerType: PointerType) {
            if (pointerType != PointerType.Mouse) {
                // iOS Safari doesn't request focus when the page is shown,
                // and the lifecycle doesn't trigger ON_RESUME.
                // so, we decided to handle every touch
                archComponentsOwner.lifecycle.currentState = Lifecycle.State.RESUMED

                val inputModeManager = platformContext.inputModeManager
                if (inputModeManager.inputMode != InputMode.Touch) {
                    inputModeManager.requestInputMode(InputMode.Touch)
                }
            }
        }

        override fun onTouchStart() {
            if (!_windowInfo.isWindowFocused) {
                restoreWindowFocusFromBackingInput()
                // The user is interacting with the canvas, so the window has to be treated as
                // focused right away. Otherwise, the logic relying on WindowInfo.isWindowFocused
                // (e.g. placing the cursor at the tapped position in a text field) would be
                // skipped for the very first tap: the native `focus` event is delivered
                // asynchronously, long after the tap gesture is processed.
                // See https://youtrack.jetbrains.com/issue/CMP-10885
                _windowInfo.isWindowFocused = true
            }
        }

        override fun hasActiveTextInputAt(position: Offset): Boolean {
            val webTextInputService = platformContext.textInputService as WebTextInputService
            val isTextFieldActive = webTextInputService.getBackingInput()?.isFocused() ?: false
            return isTextFieldActive && webTextInputService.hitTest(position)
        }
    }

    private fun initEvents(canvas: HTMLCanvasElement) {
        pointerInputHandler.initEvents(canvasEvents, state.globalEvents)

        canvas.addEventListener("contextmenu", { event ->
            event.preventDefault()
        })

        val onKeyboardEventCallback: (KeyboardEvent) -> Unit = { event ->
            processKeyboardEvent(event)
        }
        addTypedEvent("keydown", onKeyboardEventCallback)
        addTypedEvent("keyup", onKeyboardEventCallback)

        state.globalEvents.addDisposableEvent("focus") {
            _windowInfo.isWindowFocused = true
            archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        state.globalEvents.addDisposableEvent("blur") {
            _windowInfo.isWindowFocused = false
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
            insetsManager = WebWindowInsetsManager({ density }, canvas)
        }

        initEvents(canvas)
        state.init()

        applyResizeAndScale(
            size = state.currentSize(),
            viewportScale = getVisualViewportScale()
        )
        archComponentsOwner.enableSavedStateHandles()

        val interopContainer = WebInteropContainer(InteropViewGroup(interopContainerElement))

        val clipEventsTargetProvider: () -> HTMLElement = clipboardEventBridge::getEventTarget
        scene.setContent {
            CompositionLocalProvider(
                @Suppress("DEPRECATION")
                LocalSystemTheme provides systemThemeObserver.currentSystemTheme.value.asComposeSystemTheme(),
                LocalInteropContainer provides interopContainer,
                LocalActiveClipEventsTarget provides clipEventsTargetProvider,
                LocalComposeWindow provides this,
                content = {
                    installFallbackFontDownloader(configuration.fontFallbackUrl)
                    WithNestedScrollObserver(rootScrollObserver) {
                        interopContainer.TrackInteropPlacementContainer {
                            content()
                        }
                    }

                    webTextToolbar.Render()

                    DisposableEffect(state) {
                        // Observe the resize/scale events and retrieve the new values.
                        // Apply new values eagerly to improve resize smoothness and avoid performing
                        // canvas/scene resize mutations while the recomposer drains composition effects.
                        // See https://youtrack.jetbrains.com/issue/CMP-10751
                        val stopObservingSize = state.observeSizeAndScaleChanges {
                            applyResizeAndScale(
                                size = state.currentSize(),
                                viewportScale = getVisualViewportScale()
                            )
                        }
                        onDispose(stopObservingSize)
                    }

                    LaunchedEffect(Unit) {
                        coroutineScope {
                            // The initial composition would create a lot of noisy invalidations,
                            // so it makes sense to start the listener here - after the initial composition.
                            // The composition's coroutine scope ties the listener's lifetime to the composition.
                            webSemanticsListener?.start(this)
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose { webSemanticsListener?.stop() }
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

    /**
     * Requests native window focus through the active text input during a user gesture.
     *
     * On iOS Safari, a backing field can remain DOM-focused after the page loses native focus.
     * WebKit's Document::setFocusedElement skips dispatchFocusEvent (and elementDidFocus) while
     * the page is unfocused, so focusing a different element can update only the DOM focus.
     * Two synchronous focus() calls ensure the backing field is selected first, then enter
     * Element::focus's elementDidRefocus path, which can notify the UI process without that check.
     * Both calls are unconditional: the DOM-active check in BackingDomInput.focus() would skip
     * the refocus request. Window focus is still updated only by the browser's focus event.
     *
     * See: https://youtrack.jetbrains.com/issue/CMP-10883
     */
    private fun restoreWindowFocusFromBackingInput() {
        val input = (platformContext.textInputService as WebTextInputService).getBackingInput()
        if (input == null) return
        input.focus()
        input.focus()
    }

    private fun applyResizeAndScale(
        size: IntSize,
        viewportScale: Float
    ) = Snapshot.withMutableSnapshot {
        // Coerce the original value so it doesn't exceed 2.0 to avoid unlimited canvas growth and memory consumption.
        // Otherwise, the browser might clip the canvas content (tested in Chrome) making some UI parts unreachable.
        // We accept some blur might be still noticeable on higher than 2.0 scale.
        val coercedViewportScale = viewportScale.coerceIn(1f, 2f)

        val newDensity = Density(
            // actualDensity accounts for browser zoom (via Cmd/Ctrl+-),
            // but it doesn't account for viewport scale.
            // So account for viewport scale to avoid blurred UI after pinch-to-zoom:
            density = actualDensity.toFloat() * coercedViewportScale,
            fontScale = density.fontScale
        )

        if (newDensity != density) {
            density = newDensity
            scene.density = newDensity
        }

        val dpSize = DpSize(size.width.dp, size.height.dp)
        val sizeInPx = dpSize.toSize(density).toIntSize()

        // we need to scale canvas both via CSS styling and HTML attributes
        // https://www.khronos.org/webgl/wiki/HandlingHighDPI
        canvas.width = sizeInPx.width
        canvas.height = sizeInPx.height

        // The a11y container is sized via CSS (`width: 100%; height: 100%`) at construction
        // time, so it tracks the canvas automatically without a per-resize bridge call across
        // the wasm2js boundary. See ComposeViewport for the setup.

        _windowInfo.containerSize = sizeInPx
        _windowInfo.containerDpSize = dpSize

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

        webSemanticsListener?.stop()
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

    internal fun focusCanvas() {
        canvas.focus()
    }

    internal fun isFocusInComposeContainer(): Boolean {
        return document.activeElement != null && viewportContainer.contains(document.activeElement)
            || rootNode.hasActiveElement()
    }
}
