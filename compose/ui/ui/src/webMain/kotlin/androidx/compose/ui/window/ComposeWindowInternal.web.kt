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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.navigationevent.BackNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WebHapticFeedback
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.WebTextToolbar
import androidx.compose.ui.platform.WebWakeLockManager
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.platform.accessibility.ComposeWebSemanticsListener
import androidx.compose.ui.platform.installFallbackFontDownloader
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneDragAndDropNode
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.scene.WebComposeSceneLayer
import androidx.compose.ui.scene.WebComposeSceneMediator
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.DocumentReadyState
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.LOADING
import org.w3c.dom.MediaQueryListEvent
import org.w3c.dom.Node

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

internal class DefaultWindowState(private val viewportContainer: Element) : ComposeWindowState {
    private val channel = Channel<IntSize>(CONFLATED)

    override val globalEvents = EventTargetListener(window)

    override fun init() {

        globalEvents.addDisposableEvent("resize") {
            channel.trySend(getParentContainerBox())
        }

        initMediaEventListener {
            channel.trySend(getParentContainerBox())
        }

        channel.trySend(getParentContainerBox())
    }

    private fun getParentContainerBox(): IntSize {
        return IntSize(viewportContainer.clientWidth, viewportContainer.clientHeight)
    }

    private fun initMediaEventListener(handler: (Double) -> Unit) {
        val contentScale = actualDensity
        window.matchMedia("(resolution: ${contentScale}dppx)")
            .addEventListener("change", { evt ->
                evt as MediaQueryListEvent
                if (!evt.matches) {
                    handler(contentScale)
                }
                initMediaEventListener(handler)
            }, AddEventListenerOptions(capture = true, once = true))
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
    internal val layersRoot: HTMLElement,
    private val a11yContainerElement: HTMLDivElement?,
    internal val configuration: ComposeViewportConfiguration,
    content: @Composable () -> Unit,
    private val state: ComposeWindowState
) {
    private var isDisposed = false

    private val density: Density = Density(
        density = actualDensity.toFloat(),
        fontScale = 1f
    )

    private val _windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }

    internal val windowInfo: WindowInfo get() = _windowInfo

    internal val globalEvents: EventTargetListener get() = state.globalEvents

    // Layers created via [createComposeSceneContext] (CMP-8359 slice 2/3); shared across nesting
    // depth so a layer created from within another layer's content registers here too.
    internal val layers = mutableListOf<WebComposeSceneLayer>()

    @VisibleForTesting
    internal val archComponentsOwner = DefaultArchitectureComponentsOwner()

    private val navigationEventInput = BackNavigationEventInput()

    // TODO: [frameRecomposer] must be shared between Compose instances.
    //  It's supposed to be stored in platform's root view or window.
    // TODO: It cannot be used in case of shared [FrameRecomposer], replace this helper with calling
    //  - [frameRecomposer.performFrame] once per frame (across all instances) before platform views layout phase
    //  - [scene.measureAndLayout] during platform views layout phase. Note that it should be triggered
    //    by platform view invalidation (which is triggered by [scene.invalidateLayout] OR by regular platform invalidation)
    //  - [scene.draw] during drawing phase of platform views (which is triggered by [scene.invalidateDraw]).
    //    Note that in case of custom GPU surface/V-Sync handling, it needs to be handled differently.
    private val mediator = WebComposeSceneMediator(
        canvas = canvas,
        coroutineContext = Dispatchers.Main,
        archComponentsOwner = archComponentsOwner,
        navigationEventInput = navigationEventInput,
        globalEvents = state.globalEvents,
        clipTargetContainer = layerRoot,
        density = density,
        isBackingInputFocused = {
            (platformContext.textInputService as WebTextInputService).getBackingInput()?.isFocused() == true
        },
        requestTouchInputMode = ::requestTouchInputMode,
    )

    private fun requestTouchInputMode() {
        val inputModeManager = platformContext.inputModeManager
        if (inputModeManager.inputMode != InputMode.Touch) {
            inputModeManager.requestInputMode(InputMode.Touch)
        }
    }

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {
            override val windowInfo get() = _windowInfo
            override val architectureComponentsOwner get() = archComponentsOwner

            override val dragAndDropManager: PlatformDragAndDropManager = object :
                WebDragAndDropManager(rootNode, mediator.canvasEvents, state.globalEvents, density) {
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

                    override val currentTouchOffset: Offset?
                        get() = mediator.activeTouchOffset

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
                }
            }

            override val viewConfiguration =
                object : ViewConfiguration by PlatformContext.DefaultViewConfiguration {
                    override val touchSlop: Float get() = with(density) { 18.dp.toPx() }
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

    // Bound to a [WebComposeSceneLayer] created via [createComposeSceneContext] (CMP-8359 slice 2);
    // returns the same context type regardless of nesting depth, so a layer created from within
    // another layer's content still shares this window's [layers] registry.
    internal fun createComposeSceneContext(context: PlatformContext): ComposeSceneContext =
        object : ComposeSceneContext {
            override val platformContext = context

            override fun createLayer(
                density: Density,
                layoutDirection: LayoutDirection,
                focusable: Boolean,
                consumePointerInputOutside: Boolean,
            ): ComposeSceneLayer = WebComposeSceneLayer(
                composeWindow = this@ComposeWindow,
                initialDensity = density,
                initialLayoutDirection = layoutDirection,
                initialFocusable = focusable,
                consumePointerInputOutside = consumePointerInputOutside,
            )
        }

    private val scene = if (configuration.isPerCanvasSceneLayerEnabled) {
        PlatformLayersComposeScene(
            frameRecomposer = mediator.frameRecomposer,
            density = density,
            composeSceneContext = createComposeSceneContext(platformContext),
            // TODO: Split layout invalidation from draw invalidation once the web host has distinct
            //  scheduling paths for relayout vs redraw.
            invalidateLayout = mediator.invalidateLayout,
            invalidateDraw = mediator.invalidateDraw,
        )
    } else {
        CanvasLayersComposeScene(
            frameRecomposer = mediator.frameRecomposer,
            platformContext = platformContext,
            density = density,
            // TODO: Split layout invalidation from draw invalidation once the web host has distinct
            //  scheduling paths for relayout vs redraw.
            invalidateLayout = mediator.invalidateLayout,
            invalidateDraw = mediator.invalidateDraw,
        )
    }.also { mediator.scene = it }

    private val systemThemeObserver = getSystemThemeObserver()

    init {
        state.init()

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

        scene.density = density
        archComponentsOwner.enableSavedStateHandles()

        val interopContainer = WebInteropContainer(InteropViewGroup(interopContainerElement))

        val clipEventsTargetProvider: () -> HTMLElement = {
            (platformContext.textInputService as WebTextInputService).getBackingInput()
                ?: mediator.clipTarget
        }
        scene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemThemeObserver.currentSystemTheme.value,
                LocalInteropContainer provides interopContainer,
                LocalActiveClipEventsTarget provides clipEventsTargetProvider,
                LocalComposeWindow provides this,
                content = {
                    installFallbackFontDownloader()
                    interopContainer.TrackInteropPlacementContainer {
                        content()
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

        _windowInfo.containerSize = sizeInPx
        _windowInfo.containerDpSize = boxSize

        mediator.resize(sizeInPx)
    }

    // TODO: need to call .dispose() on window close.
    fun dispose() {
        check(!isDisposed)

        // Dispose compositions (which may still reference the lifecycle/ViewModelStore while
        // tearing down) before destroying the shared owner they reference.
        layers.toList().forEach { it.close() }
        scene.close()
        mediator.dispose()

        archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        archComponentsOwner.viewModelStore.clear()
        archComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.removeInput(navigationEventInput)

        systemThemeObserver.dispose()
        state.dispose()
        isDisposed = true
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

internal fun Element.isFocused(): Boolean {
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
