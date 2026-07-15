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

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.isolate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
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
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.ProvidePlatformCompositionLocals
import androidx.compose.ui.util.trace
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * BaseComposeScene is an internal abstract class that implements the ComposeScene interface.
 * It provides a base implementation for managing composition, input events, and rendering.
 *
 * @property composeSceneContext the object that used to share "context" between multiple scenes
 * on the screen. Also, it provides a way for platform interaction that is required within a scene.
 */
@OptIn(InternalComposeUiApi::class, InternalComposeApi::class)
internal abstract class BaseComposeScene(
    coroutineContext: CoroutineContext,
    private val invalidate: () -> Unit,
) : ComposeScene {
    protected val snapshotInvalidationTracker = SnapshotInvalidationTracker(::updateInvalidations)
    protected val inputHandler: ComposeSceneInputHandler =
        ComposeSceneInputHandler(
            prepareForPointerInputEvent = ::doMeasureAndLayout,
            processPointerInputEvent = ::onPointerInputEvent,
            cancelPointerInput = ::processCancelPointerInput,
            processKeyEvent = ::processKeyEvent,
        )

    private val frameClock = BroadcastFrameClock(onNewAwaiters = ::updateInvalidations)

    /**
     * Carries the scene's current frame-cycle [DataSource.Snapshot].
     * Empty while frame isolation is disabled,
     * rotated by [rotateCycleSnapshot] at the latest frame's start,
     * re-entered by every span/slice.
     */
    private val frameSnapshotHolder: SnapshotHolder? =
        if (ComposeSceneFeatureFlags.isFrameIsolationEnabled) {
            SnapshotHolder().apply { current = DataSource.takeSnapshot() }
        } else {
            null
        }

    /**
     * Wakes render scheduling when an external commit's invalidations get parked behind an
     * open frame-isolation pin: parking replaces the dispatch that would otherwise reach
     * the invalidation tracker, so without this content-free nudge an idle scene would
     * never render - and never rotate the pin that releases the parked work.
     */
    private val parkedInvalidationHandle: ObserverHandle? =
        if (frameSnapshotHolder != null) {
            Snapshot.registerParkedApplyNotifier { updateInvalidations() }
        } else {
            null
        }

    private val recomposer: ComposeSceneRecomposer =
        ComposeSceneRecomposer(coroutineContext, frameSnapshotHolder, frameClock)
    private var composition: Composition? = null

    protected val compositionContext: CompositionContext
        get() = recomposer.compositionContext

    abstract val composeSceneContext: ComposeSceneContext

    protected var isClosed = false
        private set

    private var isInvalidationDisabled = false
    private inline fun <T> postponeInvalidation(
        traceTag: String,
        isolated: Boolean = true,
        crossinline block: () -> T
    ): T =
        trace(traceTag) {
            check(!isClosed) { "postponeInvalidation called after ComposeScene is closed" }
            isInvalidationDisabled = true
            return try {
                // Try to get see the up-to-date state before running block
                // Note that this doesn't guarantee it, if sendApplyNotifications is called concurrently
                // in a different thread than this code.
                snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
                snapshotInvalidationTracker.performSnapshotChangesSynchronously {
                    if (isolated && frameSnapshotHolder != null) {
                        frameSnapshotHolder.checkedCurrent.isolate(block)
                    } else {
                        block()
                    }
                }
            } finally {
                snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
                isInvalidationDisabled = false
            }.also {
                updateInvalidations()
            }
        }

    protected inline fun <T> withIsolationIfEnabled(block: () -> T): T {
        return if (frameSnapshotHolder != null) {
            frameSnapshotHolder.checkedCurrent.isolate(block)
        } else {
            block()
        }
    }

    /**
     * Runs [block] as one slice of the frame cycle, published atomically (with delivery of
     * its invalidations) when the block ends — [isolate] merges into an enclosing slice
     * instead if one is already current, so the outermost boundary owns the publish. With
     * frame isolation disabled, runs [block] directly and flushes the global snapshot's
     * pending invalidations after it, preserving the stock phase-boundary behavior.
     */
    private inline fun withIsolationOrApplyNotifications(block: () -> Unit) {
        return if (frameSnapshotHolder != null) {
            frameSnapshotHolder.checkedCurrent.isolate(block)
        } else {
            block()
            Snapshot.sendApplyNotifications()
        }
    }

    /**
     * The frame-start pin swap: publishes nothing itself; external changes published
     * since the previous swap become visible to the new cycle, and invalidations that
     * were consumed against the old pinned view are re-armed by the dispose.
     */
    private fun rotateCycleSnapshot() {
        val old = frameSnapshotHolder?.checkedCurrent ?: return
        frameSnapshotHolder.current = null
        old.dispose()
        frameSnapshotHolder.current = DataSource.takeSnapshot()
    }

    @Volatile
    private var hasPendingDraws = true
    protected fun updateInvalidations() {
        hasPendingDraws = frameClock.hasAwaiters ||
            snapshotInvalidationTracker.hasInvalidations ||
            (frameSnapshotHolder != null && Snapshot.hasParkedApplyNotifications())
        if (hasPendingDraws && !isInvalidationDisabled && !isClosed && composition != null) {
            invalidate()
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

        parkedInvalidationHandle?.dispose()
        val oldSnapshot = frameSnapshotHolder?.current
        frameSnapshotHolder?.current = null
        // With frame isolation enabled, close() must not be called from within a frame,
        // input, or effect slice (e.g. an event handler that synchronously closes its own
        // scene): the slice's child snapshot is still open there and dispose() fails fast
        // with "Cannot dispose while a child snapshot is open". Previously this same
        // reentrant pattern silently corrupted the unit's state instead of failing.
        oldSnapshot?.dispose()
        composition?.dispose()
        recomposer.cancel()
    }

    override fun hasInvalidations(): Boolean = hasPendingDraws || recomposer.hasPendingWork

    override fun setContent(content: @Composable () -> Unit) =
        postponeInvalidation("BaseComposeScene:setContent") {
            check(!isClosed) { "setContent called after ComposeScene is closed" }
            inputHandler.onChangeContent()

            /*
             * It's required before setting content to apply changed parameters
             * before first recomposition. Otherwise, it can lead to double recomposition.
             */
            recomposer.performScheduledRecomposerTasks()

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

            recomposer.performScheduledRecomposerTasks()
        }

    override fun render(canvas: Canvas, nanoTime: Long) {
        // This is a no-op if the scene is closed, this situation can happen if the scene is
        // in the list for rendering, but recomposition in another scene from the same list
        // processed earlier has closed it.

        if (isClosed) return

        postponeInvalidation("BaseComposeScene:render", isolated = false) {
            // We try to run the phases here in the same order Android does.

            // Flush composition effects (e.g. LaunchedEffect, coroutines launched in
            // rememberCoroutineScope()) before everything else. Their slices run and
            // publish on the previous frame's pin - they are inter-frame work.
            recomposer.performScheduledEffects()

            // Pin swap: publishes nothing itself; external changes published since the
            // previous swap become visible to this frame.
            rotateCycleSnapshot()

            // The frame dispatch is deliberately NOT wrapped in a scene-level slice: with
            // frame isolation on, the Recomposer slices its own pipeline (the animation
            // pump, then the recompose+apply pass) into sequential child slices, and each
            // must publish - with delivery of its invalidations - before the next one is
            // taken (the same-frame animation contract). An enclosing slice would merge
            // them and defer that delivery to its own end.
            recomposer.performScheduledRecomposerTasks()
            frameClock.sendFrame(nanoTime) // withFrameMillis/Nanos and recomposition

            // Between layout and draw, Android's Choreographer flushes the main
            // dispatcher. We can't do quite that, but an important side effect of
            // that is that pending invalidations get dispatched, which we can (and
            // must) do - by publishing each phase slice when frame isolation is on,
            // and via the global snapshot otherwise.
            withIsolationOrApplyNotifications {
                doMeasureAndLayout()  // Layout

                // Schedule synthetic events to be sent after `render` completes
                if (inputHandler.needUpdatePointerPosition) {
                    recomposer.scheduleAsEffect {
                        inputHandler.updatePointerPosition()
                    }
                }
            }

            // The drawing phase.
            // Android calls these two before drawing (AndroidComposeView.dispatchDraw)
            withIsolationOrApplyNotifications { doMeasureAndLayout() }

            // Actually draw
            withIsolationOrApplyNotifications {
                snapshotInvalidationTracker.onDraw()
                draw(canvas)
            }
        } // the frame's writes publish here - visible immediately after the frame
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
            recomposer.performScheduledEffects()
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
            recomposer.performScheduledEffects()
        }
    }

    override fun cancelPointerInput() {
        inputHandler.cancelPointerInput()
    }

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean =
        postponeInvalidation("BaseComposeScene:sendKeyEvent") {
            inputHandler.onKeyEvent(keyEvent).also {
                recomposer.performScheduledEffects()
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
            recomposer.performScheduledEffects()
        }
    }

    override suspend fun withMonotonicFrameClock(block: suspend () -> Unit) {
        val monotonicFrameClock = compositionContext.effectCoroutineContext[MonotonicFrameClock]
            ?: error("No MonotonicFrameClock found in compositionContext")
        withContext(monotonicFrameClock) {
            block()
        }
    }

    protected fun doMeasureAndLayout() {
        snapshotInvalidationTracker.onMeasureAndLayout()
        measureAndLayout()
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

    protected abstract fun measureAndLayout()

    protected abstract fun draw(canvas: Canvas)
}

internal val BaseComposeScene.semanticsOwnerListener
    get() = composeSceneContext.platformContext.semanticsOwnerListener

// TODO: Remove the cast once there is a way to obtain it from [PlatformContext]
internal val ComposeScene.lastKnownPointerPosition: Offset?
    get() {
        this as BaseComposeScene
        return lastKnownPointerPosition
    }
