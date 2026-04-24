/*
 * Copyright 2023 The Android Open Source Project
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
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.rotary.RotaryScrollEvent
import androidx.compose.ui.node.SnapshotInvalidationTracker
import androidx.compose.ui.node.WeakReference
import androidx.compose.ui.platform.PlatformFrameDispatcher
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.ProvidePlatformCompositionLocals
import androidx.compose.ui.platform.platformFrameDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.findPlatformFrameDispatcherInNearestAncestor
import androidx.compose.ui.platform.findCompositionContextInNearestAncestor
import androidx.compose.ui.util.trace
import kotlin.concurrent.Volatile

/**
 * BaseComposeScene is an internal abstract class that implements the ComposeScene interface.
 * It provides a base implementation for managing composition, input events, and rendering.
 *
 * @property composeSceneContext the object that used to share "context" between multiple scenes
 * on the screen. Also, it provides a way for platform interaction that is required within a scene.
 */
@OptIn(InternalComposeUiApi::class)
internal abstract class BaseComposeScene(
    private val invalidateLayout: () -> Unit,
    private val invalidateDraw: () -> Unit,
) : ComposeScene {
    protected val snapshotInvalidationTracker = SnapshotInvalidationTracker(::updateInvalidations)
    protected val inputHandler: ComposeSceneInputHandler =
        ComposeSceneInputHandler(
            prepareForPointerInputEvent = ::runMeasureAndLayout,
            processPointerInputEvent = ::onPointerInputEvent,
            cancelPointerInput = ::processCancelPointerInput,
            processKeyEvent = ::processKeyEvent,
        )

    private var composition: Composition? = null

    abstract val composeSceneContext: ComposeSceneContext

    protected var isClosed = false
        private set

    private var isInvalidationDisabled = false
    private inline fun <T> postponeInvalidation(traceTag: String, crossinline block: () -> T): T = trace(traceTag) {
        check(!isClosed) { "postponeInvalidation called after ComposeScene is closed" }
        if (isInvalidationDisabled) return block()
        isInvalidationDisabled = true
        return try {
            // Try to get see the up-to-date state before running block
            // Note that this doesn't guarantee it, if sendApplyNotifications is called concurrently
            // in a different thread than this code.
            snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
            snapshotInvalidationTracker.performSnapshotChangesSynchronously(block)
        } finally {
            snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
            isInvalidationDisabled = false
        }.also {
            updateInvalidations()
        }
    }

    protected fun updateInvalidations() {
        hasPendingMeasureOrLayout = snapshotInvalidationTracker.hasPendingMeasureOrLayout
        hasPendingDraw = snapshotInvalidationTracker.hasPendingDraw
            || snapshotInvalidationTracker.hasPendingSnapshotCommands
        if (!isInvalidationDisabled && !isClosed && composition != null) {
            if (hasPendingMeasureOrLayout) {
                invalidateLayout()
            }
            if (hasPendingDraw) {
                invalidateDraw()
            }
        }
    }

    override var compositionLocalContext: CompositionLocalContext? by mutableStateOf(null)

    /**
     * The last known position of pointer cursor position or `null` if cursor is not inside a scene.
     *
     * TODO: Move it to PlatformContext
     */
    val lastKnownPointerPosition by inputHandler::lastKnownPointerPosition

    init {
        GlobalSnapshotManager.ensureStarted()
    }

    override fun close() {
        check(!isClosed) { "ComposeScene is already closed" }
        isClosed = true

        composition?.dispose()
    }

    @Volatile
    override var hasPendingMeasureOrLayout: Boolean = true
        protected set

    @Volatile
    override var hasPendingDraw: Boolean = true
        protected set

    override fun setContent(content: @Composable () -> Unit) =
        postponeInvalidation("BaseComposeScene:setContent") {
            check(!isClosed) { "setContent called after ComposeScene is closed" }
            inputHandler.onChangeContent()

            /*
             * This is usually a no-op for the first composition, but it must drain any stale
             * host work from the previous content before replacing the composition. Otherwise,
             * changed parameters can be applied in a separate turn and trigger double
             * recomposition when new content is installed.
             */
            resolveFrameDispatcher().performScheduledRecomposerTasks()
            composition?.dispose()
            composition = createComposition {
                ProvidePlatformCompositionLocals(
                    @Suppress("DEPRECATION")
                    LocalComposeScene provides this,
                    LocalComposeSceneContext provides composeSceneContext,
                    platformContext = composeSceneContext.platformContext,
                    content = content
                )
            }
            resolveFrameDispatcher().performScheduledRecomposerTasks()
        }

    override fun measureAndLayout() {
        if (isClosed) return

        postponeInvalidation("BaseComposeScene:measureAndLayout") {
            runMeasureAndLayout()

            // Schedule synthetic events to be sent after measure/layout completes.
            if (inputHandler.needUpdatePointerPosition) {
                resolveFrameDispatcher().dispatch {
                    inputHandler.updatePointerPosition()
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (isClosed) return

        postponeInvalidation("BaseComposeScene:draw") {
            runMeasureAndLayout()
            snapshotInvalidationTracker.onDraw()
            doDraw(canvas)
        }
    }

    override fun sendPointerEvent(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset,
        timeMillis: Long,
        type: PointerType,
        buttons: PointerButtons?,
        keyboardModifiers: PointerKeyboardModifiers?,
        nativeEvent: Any?,
        button: PointerButton?,
        scaleGestureFactor: Float,
        panGestureOffset: Offset
    ): PointerEventResult = postponeInvalidation(
        "BaseComposeScene:sendPointerEvent"
    ) {
        inputHandler.onPointerEvent(
            eventType = eventType,
            position = position,
            scrollDelta = scrollDelta,
            timeMillis = timeMillis,
            type = type,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            nativeEvent = nativeEvent,
            button = button,
            scaleGestureFactor = scaleGestureFactor,
            panGestureOffset = panGestureOffset,
        ).also {
            resolveFrameDispatcher().performScheduledEffects()
        }
    }

    // TODO(demin) verify that pressure is the same on Android and iOS
    override fun sendPointerEvent(
        eventType: PointerEventType,
        pointers: List<ComposeScenePointer>,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        scrollDelta: Offset,
        timeMillis: Long,
        nativeEvent: Any?,
        button: PointerButton?,
        scaleGestureFactor: Float,
        panGestureOffset: Offset,
    ): PointerEventResult = postponeInvalidation(
        "BaseComposeScene:sendPointerEvent"
    ) {
        inputHandler.onPointerEvent(
            eventType = eventType,
            pointers = pointers,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            scrollDelta = scrollDelta,
            timeMillis = timeMillis,
            nativeEvent = nativeEvent,
            button = button,
            scaleGestureFactor = scaleGestureFactor,
            panGestureOffset = panGestureOffset,
        ).also {
            resolveFrameDispatcher().performScheduledEffects()
        }
    }

    override fun cancelPointerInput() {
        inputHandler.cancelPointerInput()
    }

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean =
        postponeInvalidation("BaseComposeScene:sendKeyEvent") {
            inputHandler.onKeyEvent(keyEvent).also {
                resolveFrameDispatcher().performScheduledEffects()
            }
        }

    override fun sendRotaryScrollEvent(
        verticalScrollPixels: Float,
        horizontalScrollPixels: Float,
        timeMillis: Long
    ): Boolean = postponeInvalidation("BaseComposeScene:sendRotaryScrollEvent") {
        val event = RotaryScrollEvent(
            verticalScrollPixels = verticalScrollPixels,
            horizontalScrollPixels = horizontalScrollPixels,
            uptimeMillis = timeMillis
        )
        processRotaryScrollEvent(event).also {
            resolveFrameDispatcher().performScheduledEffects()
        }
    }

    private var parentCompositionContext: CompositionContext? = null
    protected fun resolveParentCompositionContext(): CompositionContext =
        parentCompositionContext ?: with(composeSceneContext.platformContext.valueStorage) {
            compositionContext
                ?: findCompositionContextInNearestAncestor()
                ?: findPlatformFrameDispatcherInNearestAncestor()?.compositionContext
                ?: error("Parent CompositionContext is not found")
        }


    private var frameDispatcher: PlatformFrameDispatcher? = null
    private fun resolveFrameDispatcher(): PlatformFrameDispatcher =
        frameDispatcher ?: with(composeSceneContext.platformContext.valueStorage) {
            findPlatformFrameDispatcherInNearestAncestor()
                ?: error("PlatformFrameDispatcher is not found")
        }

    protected fun runMeasureAndLayout() {
        snapshotInvalidationTracker.onMeasureAndLayout()
        doMeasureAndLayout()
    }

    protected abstract fun createComposition(content: @Composable () -> Unit): Composition

    private fun onPointerInputEvent(event: PointerInputEvent) = processPointerInputEvent(event)
        .also {
            if (composeSceneContext.platformContext.isClearFocusOnMouseDownEnabled) {
                val isDown = event.eventType == PointerEventType.Press
                val pointer = event.pointers.singleOrNull()
                val isFromMouse = pointer?.type == PointerType.Mouse
                if (isDown && isFromMouse) {
                    focusManager.clearFocusIfOutsideOfActiveFocusTargetNode(pointer.position)
                }
            }
        }

    protected abstract fun processPointerInputEvent(event: PointerInputEvent): PointerEventResult

    protected abstract fun processCancelPointerInput()

    protected abstract fun processKeyEvent(keyEvent: KeyEvent): Boolean

    protected abstract fun processRotaryScrollEvent(event: RotaryScrollEvent): Boolean

    protected abstract fun doMeasureAndLayout()

    protected abstract fun doDraw(canvas: Canvas)
}

internal val BaseComposeScene.semanticsOwnerListener
    get() = composeSceneContext.platformContext.semanticsOwnerListener

// TODO: Remove the cast once there is a way to obtain it from [PlatformContext]
internal val ComposeScene.lastKnownPointerPosition: Offset?
    get() {
        this as BaseComposeScene
        return lastKnownPointerPosition
    }
