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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.keysymToKey
import androidx.compose.ui.input.key.xKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.SingleComposeSceneRenderingScope
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpSize
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlinx.cinterop.*
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.X11Window
import org.jetbrains.skiko.LinuxMainDispatcher
import org.jetbrains.skiko.SkikoDispatchers
import x11gl.*
import platform.posix.*

interface WindowScope {
    val window: X11Window
}

private val openWindows = mutableListOf<ComposeWindow>()

private fun runEventLoop() {
    val arena = Arena()
    try {
        val event = arena.alloc<XEvent>()
        while (openWindows.isNotEmpty()) {
            LinuxMainDispatcher.drain()
            val processedCount = openWindows.toList().sumOf { it.pumpPendingEvents(event) }
            LinuxMainDispatcher.drain()
            if (processedCount == 0) {
                waitForEvents(timeoutMs = 10)
            }
        }
    } finally {
        arena.clear()
    }
}

private fun waitForEvents(timeoutMs: Int): Int = memScoped {
    val firstWindow = openWindows.firstOrNull() ?: return@memScoped 0
    val fd = alloc<pollfd>().apply {
        this.fd = XConnectionNumber(firstWindow.x11Window.display)
        events = POLLIN.toShort()
    }
    poll(fd.ptr, 1u, timeoutMs)
}

fun Window(
    title: String = "ComposeWindow",
    size: DpSize = DpSize(800.dp, 600.dp),
    content: @Composable WindowScope.() -> Unit,
) {
    val window = ComposeWindow(
        title = title,
        size = size,
        content = content,
    )
    openWindows.add(window)
    if (openWindows.size == 1) {
        runEventLoop()
    }
}

private class ComposeWindow(
    title: String,
    size: DpSize,
    content: @Composable WindowScope.() -> Unit,
) : WindowScope {
    private var isDisposed = false
    
    val x11Window = X11Window(title, size.width.value.toInt(), size.height.value.toInt())
    override val window: X11Window get() = x11Window

    private val _windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }
    private val archComponentsOwner = DefaultArchitectureComponentsOwner()

    private val frameRecomposer = FrameRecomposer(SkikoDispatchers.Main) { skiaLayer.needRender() }
    private val sceneRenderingScope = SingleComposeSceneRenderingScope { skiaLayer.needRender() }

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {
            override val windowInfo get() = _windowInfo
            override val architectureComponentsOwner get() = archComponentsOwner
            override fun setPointerIcon(pointerIcon: PointerIcon) {
                // Ignore
            }
        }
    
    private val skiaLayer = SkiaLayer()
    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        platformContext = platformContext,
        invalidateLayout = sceneRenderingScope::onSceneInvalidation,
        invalidateDraw = sceneRenderingScope::onSceneInvalidation,
    )
    
    private val renderDelegate = object : SkikoRenderDelegate {
        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            val sizeInPx = IntSize(width, height)
            _windowInfo.containerSize = sizeInPx
            _windowInfo.containerDpSize = sizeInPx.toSize().toDpSize(scene.density)
            scene.size = sizeInPx
            with(sceneRenderingScope) {
                scene.render(frameRecomposer, canvas.asComposeCanvas(), nanoTime)
            }
        }
    }

    init {
        skiaLayer.renderDelegate = renderDelegate
        skiaLayer.attachTo(x11Window)
        x11Window.show()

        scene.density = Density(x11Window.contentScale)
        scene.setContent {
            content()
        }

        archComponentsOwner.enableSavedStateHandles()
        archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun dispose() {
        if (isDisposed) return
        archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        archComponentsOwner.viewModelStore.clear()
        skiaLayer.detach()
        scene.close()
        frameRecomposer.close()
        x11Window.close()
        openWindows.remove(this)
        isDisposed = true
    }

    /**
     * Handles every X11 event queued for this window's display and returns how many were
     * processed. Stops as soon as the window is disposed: [handleEvent] may close the
     * display (WM_DELETE_WINDOW → [dispose]), after which it must not be touched again.
     */
    fun pumpPendingEvents(event: XEvent): Int =
        generateSequence {
            event.takeIf { !isDisposed && XPending(x11Window.display) > 0 }?.also {
                XNextEvent(x11Window.display, it.ptr)
                handleEvent(it)
            }
        }.count()

    private fun handleEvent(event: XEvent) {
        if (isDisposed) return
        when (event.type) {
            ClientMessage -> {
                if (event.xclient.data.l[0].toULong() == x11Window.wmDeleteWindow) {
                    dispose()
                }
            }

            Expose -> skiaLayer.needRender()

            ConfigureNotify -> {
                val w = event.xconfigure.width
                val h = event.xconfigure.height
                x11Window.width = w
                x11Window.height = h
                skiaLayer.needRender()
            }

            MotionNotify -> {
                val x = event.xmotion.x.toFloat()
                val y = event.xmotion.y.toFloat()
                val modifiers = xKeyboardModifiers(event.xmotion.state.toLong())
                onMouseEvent(PointerEventType.Move, x, y, modifiers)
            }

            ButtonPress -> {
                val x = event.xbutton.x.toFloat()
                val y = event.xbutton.y.toFloat()
                val button = event.xbutton.button.toInt()
                val modifiers = xKeyboardModifiers(event.xbutton.state.toLong())
                handleButton(x, y, button, modifiers, pressed = true)
            }

            ButtonRelease -> {
                val x = event.xbutton.x.toFloat()
                val y = event.xbutton.y.toFloat()
                val button = event.xbutton.button.toInt()
                val modifiers = xKeyboardModifiers(event.xbutton.state.toLong())
                handleButton(x, y, button, modifiers, pressed = false)
            }

            KeyPress -> handleKey(event, KeyEventType.KeyDown)

            KeyRelease -> handleKey(event, KeyEventType.KeyUp)
        }
    }

    private fun onMouseEvent(
        eventType: PointerEventType,
        x: Float,
        y: Float,
        modifiers: PointerKeyboardModifiers,
    ) {
        scene.sendPointerEvent(
            eventType = eventType,
            position = Offset(x, y),
            keyboardModifiers = modifiers,
        )
    }

    private fun handleButton(
        x: Float,
        y: Float,
        button: Int,
        modifiers: PointerKeyboardModifiers,
        pressed: Boolean,
    ) {
        when (button) {
            4, 5 -> {
                if (pressed) {
                    val deltaY = if (button == 4) -1f else 1f
                    scene.sendPointerEvent(
                        eventType = PointerEventType.Scroll,
                        position = Offset(x, y),
                        scrollDelta = Offset(0f, deltaY),
                        keyboardModifiers = modifiers,
                    )
                }
            }
            else -> {
                val composeButton = when (button) {
                    1 -> PointerButton.Primary
                    2 -> PointerButton.Tertiary
                    3 -> PointerButton.Secondary
                    else -> PointerButton(button)
                }
                val eventType = if (pressed) PointerEventType.Press else PointerEventType.Release
                scene.sendPointerEvent(
                    eventType = eventType,
                    position = Offset(x, y),
                    keyboardModifiers = modifiers,
                    button = composeButton,
                )
            }
        }
    }

    /**
     * Sends the X11 key event to the scene. The [Key] identity comes from the effective
     * keysym (post Shift/NumLock, so keypad digits resolve correctly) with a fallback to
     * the unshifted keysym (so shifted punctuation lands on its physical key, e.g. `!` on
     * [Key.One]); unmapped keysyms still go through as [Key.Unknown] so their typed
     * character reaches text fields via the code point.
     */
    private fun handleKey(event: XEvent, type: KeyEventType) = memScoped {
        val buffer = allocArray<ByteVar>(32)
        val keysym = alloc<KeySymVar>()
        val length = XLookupString(event.xkey.ptr, buffer, 32, keysym.ptr, null)
        val key = keysymToKey[keysym.value.toLong()]
            ?: keysymToKey[XLookupKeysym(event.xkey.ptr, 0).toLong()]
            ?: Key.Unknown
        val modifiers = xKeyboardModifiers(
            state = event.xkey.state.toLong(),
            key = key,
            isKeyDown = type == KeyEventType.KeyDown,
        )
        _windowInfo.keyboardModifiers = modifiers
        // XLookupString emits Latin-1, one byte per character.
        val codePoint = if (length > 0) buffer[0].toUByte().toInt() else 0
        scene.sendKeyEvent(
            KeyEvent(
                nativeKeyEvent = InternalKeyEvent(
                    key = key,
                    type = type,
                    codePoint = codePoint,
                    modifiers = modifiers,
                    nativeEvent = event,
                )
            )
        )
    }
}
