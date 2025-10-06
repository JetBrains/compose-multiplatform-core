/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.kdt.macos

import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.kdt.ComposeWindow
import androidx.compose.ui.kdt.toDpSize
import androidx.compose.ui.kdt.toIntSize
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.atomicfu.atomic
import org.jetbrains.desktop.macos.DisplayLink
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.MouseButton
import org.jetbrains.desktop.macos.Window
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

class ComposeWindowMacOs(
    val application: ComposeApplicationMacOs,
    val onCloseRequested: () -> Unit
) : ComposeWindow {
    val window = Window.create()
    val viewContext = application.gpuContext.createMetalViewContext()

    init {
        window.attachView(viewContext.view)
    }

    val pictureRecorder = PictureRecorder()

    internal val isFrameScheduled = atomic(false)

    val scene = CanvasLayersComposeScene(
        density = Density(window.scaleFactor().toFloat()),
        size = window.contentSize.toIntSize(),
        coroutineContext = ComposeUIDispatcher,
        invalidate = {
            isFrameScheduled.compareAndSet(expect = false, update = true)
        }
    )

    var displayLink: DisplayLink? = null

    fun preparePicture(): PresentablePicture {
        // todo[ps] viewContext might be already closed here
        val size = viewContext.view.size()
        val bounds = Rect.makeWH(size.width.toFloat(), size.height.toFloat())
        val canvas = pictureRecorder.beginRecording(bounds)
        canvas.clear(Color.White.toArgb())
        scene.render(canvas.asComposeCanvas(), System.nanoTime())
        return PresentablePicture(pictureRecorder.finishRecordingAsPicture(), size)
    }

    fun setupDisplayLink() {
        displayLink?.close()
        displayLink = DisplayLink.create(window.screenId()) {
            if (isFrameScheduled.compareAndSet(expect = true, update = false)) {
                GrandCentralDispatch.dispatchOnMain(highPriority = true) {
                    val presentablePicture = preparePicture()
                    viewContext.presentAsync(
                        presentablePicture,
                        waitForCATransaction = false,
                        onComplete = {
                            presentablePicture.close()
                        })
                }
            }
        }
        displayLink!!.setRunning(true)
    }

    /**
     * This operation might block because it's waiting while display link exits from the callback.
     */
    fun destroyDisplayLink() {
        displayLink!!.setRunning(false)
        displayLink!!.close()
        displayLink = null
    }

    fun repaintSynchronously() {
        displayLink?.setRunning(false)
        isFrameScheduled.value = false
        preparePicture().use { picture ->
            viewContext.presentSync(picture, waitForCATransaction = true)
        }
        displayLink?.setRunning(true)
    }

    fun setupDisplayLayerCallback() {
        viewContext.onDisplayLayer = {
            repaintSynchronously()
        }
    }

    init {
        setupDisplayLink()
        setupDisplayLayerCallback()
    }

    fun handleEvent(event: WindowEvent) {
        when (event) {
            // todo[ps] update scene density
            // todo[ps] check occlusion state
            is Event.WindowScreenChange -> {
                setupDisplayLink()
            }

            is Event.WindowResize -> {
                scene.size = window.contentSize.toIntSize()
            }

            is Event.WindowCloseRequest -> {
                onCloseRequested()
            }

            // Mouse events
            is Event.MouseDown -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Press,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event,
                    button = event.button.toComposePointerButton()
                )
            }

            is Event.MouseUp -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Release,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event,
                    button = event.button.toComposePointerButton()
                )
            }

            is Event.MouseMoved -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Move,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event
                )
            }

            is Event.MouseDragged -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Move,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event
                )
            }

            is Event.MouseEntered -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Enter,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event
                )
            }

            is Event.MouseExited -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Exit,
                    position = event.toOffset(window.scaleFactor()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event
                )
            }

            is Event.ScrollWheel -> {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Scroll,
                    position = event.toOffset(window.scaleFactor()),
                    scrollDelta = Offset(event.scrollingDeltaX.toFloat(), event.scrollingDeltaY.toFloat()),
                    timeMillis = event.toTimeMillis(),
                    buttons = getPointerButtons(),
                    keyboardModifiers = getKeyboardModifiers(),
                    nativeEvent = event
                )
            }

            // Keyboard events
            is Event.KeyDown, is Event.KeyUp -> {
                event.toComposeKeyEvent()?.let { keyEvent ->
                    scene.sendKeyEvent(keyEvent)
                }
            }

            is Event.ModifiersChanged -> {
                // Modifier changes are reflected in other events
                // No specific handling needed
            }
        }
    }

    override fun setContent(content: @Composable () -> Unit) {
        scene.setContent(content)
    }

    override val size: DpSize
        get() = window.size.toDpSize()
    override val contentSize: DpSize
        get() = window.contentSize.toDpSize()
    override val isActive: Boolean
        get() = window.isMain
    override val isKey: Boolean
        get() = window.isKey

    override fun close() {
        application.allWindows.remove(window.windowId())
        destroyDisplayLink()
        pictureRecorder.close()
        application.gpuContext.destroyMetalViewContext(viewContext)
        window.close()
    }
}

// Mouse event conversion utilities
internal fun MouseButton.toComposePointerButton(): PointerButton = when (this) {
    MouseButton.LEFT -> PointerButton.Primary
    MouseButton.RIGHT -> PointerButton.Secondary
    MouseButton.MIDDLE -> PointerButton.Tertiary
    else -> PointerButton(this.value)
}

internal fun Event.toOffset(scaleFactor: Double): Offset = when (this) {
    is Event.MouseDown -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.MouseUp -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.MouseMoved -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.MouseDragged -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.MouseEntered -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.MouseExited -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    is Event.ScrollWheel -> {
        val physical = locationInWindow.toPhysical(scaleFactor)
        Offset(physical.x.toFloat(), physical.y.toFloat())
    }
    else -> Offset.Zero
}

internal fun Event.toTimeMillis(): Long {
    // Use system time for now since timestamp conversion needs investigation
    return System.currentTimeMillis()
}

internal fun getPointerButtons(): PointerButtons {
    val pressedButtons = Event.pressedMouseButtons()
    return PointerButtons(
        isPrimaryPressed = pressedButtons.contains(MouseButton.LEFT),
        isSecondaryPressed = pressedButtons.contains(MouseButton.RIGHT),
        isTertiaryPressed = pressedButtons.contains(MouseButton.MIDDLE)
    )
}

internal fun getKeyboardModifiers(): PointerKeyboardModifiers {
    // TODO: Map KeyModifiersSet to PointerKeyboardModifiers
    // For now return empty modifiers
    return PointerKeyboardModifiers()
}

// Keyboard event conversion utilities
internal fun org.jetbrains.desktop.macos.KeyCode.toComposeKey(): Key {
    // Map macOS KeyCode to Compose Key
    // KeyCode is a value class wrapping an Int representing the macOS key code
    // We use hashCode() which returns the underlying Int value
    return Key(this.hashCode().toLong())
}

internal fun org.jetbrains.desktop.macos.KeyModifiersSet.toPointerKeyboardModifiers(): PointerKeyboardModifiers {
    return PointerKeyboardModifiers(
        isCtrlPressed = control,
        isMetaPressed = command,
        isAltPressed = option,
        isShiftPressed = shift,
        isCapsLockOn = capsLock,
        isFunctionPressed = function
    )
}

internal fun Event.toComposeKeyEvent(): ComposeKeyEvent? {
    return when (this) {
        is Event.KeyDown -> {
            ComposeKeyEvent(
                nativeKeyEvent = androidx.compose.ui.input.key.InternalKeyEvent(
                    key = keyCode.toComposeKey(),
                    type = KeyEventType.KeyDown,
                    codePoint = typedCharacters.firstOrNull()?.code ?: 0,
                    modifiers = modifiers.toPointerKeyboardModifiers(),
                    nativeEvent = this
                )
            )
        }
        is Event.KeyUp -> {
            ComposeKeyEvent(
                nativeKeyEvent = androidx.compose.ui.input.key.InternalKeyEvent(
                    key = keyCode.toComposeKey(),
                    type = KeyEventType.KeyUp,
                    codePoint = typedCharacters.firstOrNull()?.code ?: 0,
                    modifiers = modifiers.toPointerKeyboardModifiers(),
                    nativeEvent = this
                )
            )
        }
        else -> null
    }
}