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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.navigationevent.BackNavigationEventInput
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WebHapticFeedback
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.WebTextToolbar
import androidx.compose.ui.platform.accessibility.ComposeWebSemanticsListener
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.viewinterop.InteropViewGroup
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.WebInteropContainer
import androidx.compose.ui.window.ComposeWindow
import androidx.compose.ui.window.WebTextInputSession
import androidx.compose.ui.window.isFocused
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node

/**
 * Web implementation of [ComposeSceneLayer] (used by [androidx.compose.ui.window.Popup]/
 * [androidx.compose.ui.window.Dialog] when [ComposeWindow] is running with
 * `isPerCanvasSceneLayerEnabled`, see `CMP-8359-plan.md`).
 *
 * Web has no separate OS-level windows, so this mirrors desktop's `SwingComposeSceneLayer`
 * ("OnComponent": a sibling surface in the same window/DOM tree) rather than
 * `WindowComposeSceneLayer` — a single implementation covers what desktop needs two for.
 *
 * Every instance owns its own `<canvas>`, [WebComposeSceneMediator], scrim `<div>`, interop
 * container, a11y container, and [WebTextInputService], all appended into [ComposeWindow.layersRoot]
 * so z-order falls out of DOM append order.
 */
internal class WebComposeSceneLayer(
    private val composeWindow: ComposeWindow,
    initialDensity: Density,
    initialLayoutDirection: LayoutDirection,
    initialFocusable: Boolean,
    override var consumePointerInputOutside: Boolean,
) : ComposeSceneLayer {
    private var isClosed = false

    // RootNodeOwner's constructor (triggered synchronously below, while constructing [scene])
    // calls back into this layer's [platformContext], which reads [density]/[boundsInWindow] —
    // so these must be fully initialized (backing fields assigned) before [mediator]/[platformContext]/
    // [scene] are constructed. Renaming the constructor parameters (instead of the usual
    // `override var density: Density = density` shadowing pattern) keeps that ordering requirement
    // obvious rather than relying on declaration order alone.
    override var density: Density = initialDensity
        set(value) {
            field = value
            scene.density = value
        }

    override var layoutDirection: LayoutDirection = initialLayoutDirection
        set(value) {
            field = value
            scene.layoutDirection = value
        }

    override var boundsInWindow: IntRect = IntRect.Zero
        set(value) {
            field = value
            val scale = 1f / density.density
            layerContainer.style.left = "${value.left * scale}px"
            layerContainer.style.top = "${value.top * scale}px"
            layerContainer.style.width = "${value.width * scale}px"
            layerContainer.style.height = "${value.height * scale}px"
            // Popup/Dialog positioning sets this from within their own measure pass (they need to
            // measure content first to know their bounds) — mediator.resize() assigns
            // scene.size, and doing that synchronously here would reenter that same measure pass.
            // Deferring to the next frame breaks the reentrancy without any visible delay, since
            // this is still well within the same "layout settles" cycle a two-pass measurement
            // already implies. Guard against the layer having been closed in the meantime.
            window.requestAnimationFrame { if (!isClosed) mediator.resize(value.size) }
        }

    override var compositionLocalContext: CompositionLocalContext?
        get() = scene.compositionLocalContext
        set(value) {
            scene.compositionLocalContext = value
        }

    override var scrimColor: Color? = null
        set(value) {
            field = value
            if (value != null) {
                scrim.style.backgroundColor = value.toCssColor()
                scrim.style.setProperty("pointer-events", "auto")
            } else {
                scrim.style.backgroundColor = "transparent"
                scrim.style.setProperty("pointer-events", "none")
            }
        }

    override var focusable: Boolean = initialFocusable

    private var outsidePointerCallback: ((
        eventType: PointerEventType,
        button: PointerButton?
    ) -> Unit)? = null

    internal val canvas: HTMLCanvasElement = (document.createElement("canvas") as HTMLCanvasElement).apply {
        setAttribute("role", "generic")
        style.position = "absolute"
        style.top = "0"
        style.left = "0"
        style.width = "100%"
        style.height = "100%"
        style.outline = "none"
        style.setProperty("touch-action", "none")
    }

    private val a11yContainerElement: HTMLDivElement? = if (composeWindow.configuration.isA11YEnabled) {
        (document.createElement("div") as HTMLDivElement).apply {
            style.position = "absolute"
            style.top = "0"
            style.left = "0"
        }
    } else {
        null
    }

    private val interopContainerElement: HTMLDivElement = (document.createElement("div") as HTMLDivElement).apply {
        style.position = "absolute"
        style.top = "0"
        style.left = "0"
    }

    // Anchored at this layer's own canvas origin, not the shared window one — see the
    // "Interop views" section of the top-level plan for why a shared container would misplace
    // interop views once there's more than one canvas.
    private val interopContainer = WebInteropContainer(InteropViewGroup(interopContainerElement))

    private val layerContainer: HTMLDivElement = (document.createElement("div") as HTMLDivElement).apply {
        style.position = "absolute"
        appendChild(canvas)
        a11yContainerElement?.let { appendChild(it) }
        appendChild(interopContainerElement)
    }

    /**
     * A CSS backdrop for [scrimColor], sized to the viewport rather than [layerContainer] (which
     * is sized to [boundsInWindow], not the whole app) — see the "Scrim" section of the top-level
     * plan. Inserted immediately before [layerContainer] so DOM order alone puts it below this
     * layer's own canvas but above everything layered before it.
     */
    private val scrim: HTMLDivElement = (document.createElement("div") as HTMLDivElement).apply {
        style.position = "fixed"
        style.top = "0"
        style.left = "0"
        style.width = "100vw"
        style.height = "100vh"
        style.setProperty("pointer-events", "none")
    }

    private val navigationEventInput = BackNavigationEventInput()

    private val mediator = WebComposeSceneMediator(
        canvas = canvas,
        coroutineContext = Dispatchers.Main,
        archComponentsOwner = composeWindow.archComponentsOwner,
        navigationEventInput = navigationEventInput,
        globalEvents = composeWindow.globalEvents,
        clipTargetContainer = layerContainer,
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
            override val windowInfo get() = composeWindow.windowInfo
            override val architectureComponentsOwner get() = composeWindow.archComponentsOwner

            // Popup/Dialog content shouldn't delegate focus to the parent window's content,
            // matching desktop's AttachedComposeSceneLayer/DesktopComposeSceneLayer.
            override val parentFocusManager: FocusManager get() = PlatformContext.EmptyFocusManager

            override fun convertLocalToWindowPosition(localPosition: Offset): Offset =
                localPosition + boundsInWindow.topLeft.toOffset()

            override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset =
                positionInWindow - boundsInWindow.topLeft.toOffset()

            override val textToolbar: TextToolbar by lazy(LazyThreadSafetyMode.NONE) {
                WebTextToolbar()
            }

            override val hapticFeedback by lazy(LazyThreadSafetyMode.NONE) {
                WebHapticFeedback.webHapticFeedbackOrDefault()
            }

            override val semanticsOwnerListener: PlatformContext.SemanticsOwnerListener? =
                a11yContainerElement?.let {
                    it.setAttribute("aria-label", "")
                    it.setAttribute("role", "presentation")
                    it.setAttribute("aria-live", "polite")
                    it.id = "cmp_a11y_root"
                    it.style.opacity = "0"
                    it.style.setProperty("pointer-events", "none")
                    ComposeWebSemanticsListener(webSemanticsRoot = it)
                }

            override val textInputService: WebTextInputService by lazy(LazyThreadSafetyMode.NONE) {
                object : WebTextInputService() {
                    override val currentTouchOffset: Offset?
                        get() = mediator.activeTouchOffset

                    override val backingDomInputContainer: HTMLElement
                        get() = layerContainer

                    override fun getNewGeometryForBackingInput(rect: Rect): DpRect {
                        val dpRect = rect.toDpRect(density)
                        val left = dpRect.left.value
                        val top = dpRect.top.value

                        return DpRect(DpOffset(left.dp, top.dp), dpRect.size)
                    }

                    override fun processKeyboardEvent(keyEvent: KeyEvent): Boolean {
                        return scene.sendKeyEvent(keyEvent)
                    }
                }
            }

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
                    WebTextInputSession(this, textInputService)
                        .startInputMethod(request)
                }
            }

            override val isClearFocusOnMouseDownEnabled: Boolean
                get() = composeWindow.configuration.isClearFocusOnMouseDownEnabled
        }

    private val scene: ComposeScene = PlatformLayersComposeScene(
        frameRecomposer = mediator.frameRecomposer,
        density = density,
        layoutDirection = layoutDirection,
        composeSceneContext = composeWindow.createComposeSceneContext(platformContext),
        invalidateLayout = mediator.invalidateLayout,
        invalidateDraw = mediator.invalidateDraw,
    ).also { mediator.scene = it }

    init {
        composeWindow.layersRoot.appendChild(scrim)
        composeWindow.layersRoot.appendChild(layerContainer)
        composeWindow.layers.add(this)
        composeWindow.archComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.addInput(navigationEventInput)
        mediator.attach()

        if (focusable) {
            canvas.focus()
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        composeWindow.archComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.removeInput(navigationEventInput)
        composeWindow.layers.remove(this)

        scene.close()
        mediator.dispose()

        layerContainer.remove()
        scrim.remove()
    }

    override fun setContent(parentCompositionContext: CompositionContext, content: @Composable () -> Unit) {
        check(!isClosed) { "WebComposeSceneLayer is closed" }
        scene.setContent(parentCompositionContext) {
            CompositionLocalProvider(LocalInteropContainer provides interopContainer) {
                interopContainer.TrackInteropPlacementContainer {
                    content()
                }
            }
        }
    }

    override fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
    ) {
        mediator.setKeyEventListeners(
            onPreviewKeyEvent = onPreviewKeyEvent ?: { false },
            onKeyEvent = onKeyEvent ?: { false },
        )
    }

    override fun setOutsidePointerEventListener(
        onOutsidePointerEvent: ((eventType: PointerEventType, button: PointerButton?) -> Unit)?,
    ) {
        outsidePointerCallback = onOutsidePointerEvent
    }

    override fun calculateLocalPosition(positionInWindow: IntOffset): IntOffset =
        positionInWindow - boundsInWindow.topLeft

    /**
     * Invoked by [ComposeWindow]'s outside-click registry (CMP-8359 slice 2 sub-step 4) for any
     * pointer event whose target isn't contained by [canvas].
     */
    internal fun onOutsidePointerEvent(eventType: PointerEventType, button: PointerButton?) {
        outsidePointerCallback?.invoke(eventType, button)
    }

    /** Whether [target] belongs to this layer's own DOM subtree. */
    internal fun containsEventTarget(target: Node?): Boolean =
        target != null && layerContainer.contains(target)
}

private fun Color.toCssColor(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "rgba($r, $g, $b, $alpha)"
}
