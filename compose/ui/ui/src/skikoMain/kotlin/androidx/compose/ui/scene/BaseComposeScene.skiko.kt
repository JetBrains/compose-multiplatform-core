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
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.enter
import androidx.compose.runtime.withTransaction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.pumpScenelessDomainRotations
import androidx.compose.runtime.setValue
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
 * BaseComposeScene is an internal abstract class that implements the ComposeScene interface. It
 * provides a base implementation for managing composition, input events, and rendering.
 *
 * @property composeSceneContext the object that used to share "context" between multiple scenes on
 *   the screen. Also, it provides a way for platform interaction that is required within a scene.
 */
@OptIn(InternalComposeUiApi::class, InternalComposeApi::class)
internal abstract class BaseComposeScene(
    coroutineContext: CoroutineContext,
    dataSourceContext: DataSourceContext = DataSourceContext(),
    private val invalidate: () -> Unit,
) : ComposeScene {
    private val isFrameIsolationEnabled = ComposeSceneFeatureFlags.isFrameIsolationEnabled

    /**
     * The scene's frame domain: carries the [DataSourceContext] (the flag-off composing path fans
     * out through it too), the current frame-cycle unit while frame isolation is on (rotated at the
     * latest frame's start), and the pending invalidations delivered at that rotation.
     */
    private val frameSnapshotHolder: SnapshotHolder =
        SnapshotHolder(dataSourceContext, isolating = isFrameIsolationEnabled)

    protected val snapshotInvalidationTracker =
        SnapshotInvalidationTracker(::updateInvalidations, frameSnapshotHolder)
    protected val inputHandler: ComposeSceneInputHandler =
        ComposeSceneInputHandler(
            prepareForPointerInputEvent = ::doMeasureAndLayout,
            processPointerInputEvent = ::onPointerInputEvent,
            cancelPointerInput = ::processCancelPointerInput,
            processKeyEvent = ::processKeyEvent,
        )

    private val frameClock = BroadcastFrameClock(onNewAwaiters = ::updateInvalidations)

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
        crossinline block: () -> T,
    ): T =
        trace(traceTag) {
            check(!isClosed) { "postponeInvalidation called after ComposeScene is closed" }
            isInvalidationDisabled = true
            return try {
                    // The read scope covers the WHOLE ingress, including both drains below: they
                    // dispatch invalidations, and a handler that reads a data source needs a view.
                    // This is independent of [isolated], which decides only whether a TRANSACTION
                    // is opened - render deliberately opens none at this level, yet needs a view.
                    enterCurrentUnit {
                        // Try to get see the up-to-date state before running block
                        // Note that this doesn't guarantee it, if sendApplyNotifications is called
                        // concurrently
                        // in a different thread than this code.
                        snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
                        snapshotInvalidationTracker.performSnapshotChangesSynchronously {
                            val unit = if (isolated) frameSnapshotHolder.checkedCurrent else null
                            if (unit != null) {
                                unit.withTransaction(block)
                            } else {
                                block()
                            }
                        }
                    }
                } finally {
                    enterCurrentUnit {
                        snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
                    }
                    isInvalidationDisabled = false
                }
                .also { updateInvalidations() }
        }

    override val currentFrameSnapshot: DataSource.Snapshot?
        get() = frameSnapshotHolder.checkedCurrent

    /**
     * Runs [block] as one slice of the frame cycle, published atomically (with delivery of its
     * invalidations) when the block ends — [withTransaction] merges into an enclosing slice instead if one
     * is already current, so the outermost boundary owns the publish. With frame isolation
     * disabled, runs [block] directly and flushes the global snapshot's pending invalidations after
     * it, preserving the stock phase-boundary behavior.
     */
    /**
     * Runs [block] with the current frame unit's read view bound to this thread: reads see the
     * frame's view, no transaction is opened, no snapshot is taken and nothing publishes.
     *
     * With frame isolation disabled there is no unit, so [block] runs bare and stock behavior is
     * unchanged.
     *
     * This is what a publication's invalidation handlers read through. A transaction restores the
     * thread BEFORE it publishes, so its own dispatch runs in the enclosing scope - which, on a
     * platform callback thread, is this one.
     */
    private inline fun <T> enterCurrentUnit(block: () -> T): T {
        val unit = frameSnapshotHolder.checkedCurrent
        return if (unit != null) unit.enter(block) else block()
    }

    private inline fun withTransactionOrApplyNotifications(block: () -> Unit) {
        val unit = frameSnapshotHolder.checkedCurrent
        return if (unit != null) {
            unit.withTransaction(block)
        } else {
            block()
            Snapshot.sendApplyNotifications()
        }
    }

    @Volatile private var hasPendingDraws = true

    protected fun updateInvalidations() {
        hasPendingDraws =
            frameClock.hasAwaiters ||
                snapshotInvalidationTracker.hasInvalidations ||
                frameSnapshotHolder.hasPendingDelivery ||
                // A foreign source holding unpublished data needs a pass to drain it, even when
                // nothing wrote snapshot state. Without this, a store-only change requests no
                // frame at all and the UI stays stale until something else happens to render.
                frameSnapshotHolder.context.hasPendingAdvance
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

    /**
     * Fully qualified: `androidx.compose.runtime.snapshots.ObserverHandle` is a separate,
     * identically-shaped interface, and this handle comes from `DataSourceContext`.
     */
    private var contextWakeHandle: androidx.compose.runtime.ObserverHandle? = null

    init {
        GlobalSnapshotManager.ensureStarted()
        // Wakes render scheduling when a foreign commit lands in this domain's pending
        // union - only fires for an activated (frame-isolation-on) holder. Wired here,
        // during construction, so it is in place before activateFrameDomain() runs (the
        // wake-wired-before-activate invariant).
        frameSnapshotHolder.onPendingDelivery = ::updateInvalidations
        // The other half: a member of this scene's context signalling that it holds
        // unpublished data. Unlike the delivery wake above, this one matters regardless of
        // frame isolation - the drain in sendAndPerformSnapshotChanges is unconditional.
        contextWakeHandle = frameSnapshotHolder.context.registerWake(::updateInvalidations)
    }

    /**
     * Activates the frame domain: takes the standing pin's substrate snapshot and registers this
     * holder for delivery routing. INVARIANT: this MUST be called immediately after construction
     * completes (from the factory / construction site), NOT during construction. Activation
     * snapshots the pin, so every scene-owned snapshot state - this base class's
     * [compositionLocalContext] plus all subclass property initializers - must predate the pin BY
     * CONSTRUCTION. Any isolated slice that runs before the first [SnapshotHolder.rotate] (e.g. the
     * first [setContent] on iOS, deferred to layoutSubviews before any render) would otherwise read
     * a state created after the pin's snapshot and fail fast with "Reading a state that was created
     * after the snapshot was taken". Corollary: scene-owned snapshot state must not be created
     * post-construction outside a slice. No-op when frame isolation is disabled.
     */
    internal fun activateFrameDomain() {
        if (isFrameIsolationEnabled) frameSnapshotHolder.activate()
    }

    override fun close() {
        check(!isClosed) { "ComposeScene is already closed" }
        isClosed = true

        contextWakeHandle?.dispose()
        contextWakeHandle = null

        // With frame isolation enabled, close() must not be called from within a frame,
        // input, or effect slice (e.g. an event handler that synchronously closes its own
        // scene): the slice's child snapshot is still open there and dispose() fails fast
        // with "Cannot dispose while a child snapshot is open". Previously this same
        // reentrant pattern silently corrupted the unit's state instead of failing.
        frameSnapshotHolder.close()
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
                    @Suppress("DEPRECATION") LocalComposeScene provides this,
                    LocalComposeSceneContext provides composeSceneContext,
                    platformContext = composeSceneContext.platformContext,
                    content = content,
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

            // Scene-less domains (the application composition) can only rotate through the
            // platform's async main-thread queue, which starves under sustained rendering.
            // Their un-rotated pins would then retain superseded state records without
            // bound (per-consumer delivery makes a lagging domain harmless for DELIVERY,
            // but record retention is process-wide). Pump their pending swaps here, on the
            // render ingress that survives saturation.
            pumpScenelessDomainRotations()

            // Pin swap: publishes nothing itself; external changes published since the
            // previous swap become visible to this frame, and this domain's pending
            // delivery is dispatched against that new view. Swap-first ordering (so
            // `delivered ⊆ visible`) lives in SnapshotHolder.rotate.
            frameSnapshotHolder.rotate()

            // The pin just swapped, so the scope postponeInvalidation entered is the
            // PREDECESSOR's - correct for the inter-frame work above, which runs on the
            // previous frame's pin, and wrong for everything below. Re-enter so the rest of
            // the frame reads the successor's view. This binds a view only; it opens no
            // transaction, so the sequential-siblings topology below is unaffected.
            enterCurrentUnit {
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
                withTransactionOrApplyNotifications {
                    doMeasureAndLayout() // Layout

                    // Schedule synthetic events to be sent after `render` completes
                    if (inputHandler.needUpdatePointerPosition) {
                        recomposer.scheduleAsEffect { inputHandler.updatePointerPosition() }
                    }
                }

                // The drawing phase.
                // Android calls these two before drawing (AndroidComposeView.dispatchDraw)
                withTransactionOrApplyNotifications { doMeasureAndLayout() }

                // Actually draw
                withTransactionOrApplyNotifications {
                    snapshotInvalidationTracker.onDraw()
                    draw(canvas)
                }
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
        panGestureOffset: Offset,
    ): PointerEventResult =
        postponeInvalidation("BaseComposeScene:sendPointerEvent") {
            inputHandler
                .onPointerEvent(
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
                )
                .also { recomposer.performScheduledEffects() }
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
    ): PointerEventResult =
        postponeInvalidation("BaseComposeScene:sendPointerEvent") {
            inputHandler
                .onPointerEvent(
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
                )
                .also { recomposer.performScheduledEffects() }
        }

    override fun cancelPointerInput() {
        inputHandler.cancelPointerInput()
    }

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean =
        postponeInvalidation("BaseComposeScene:sendKeyEvent") {
            inputHandler.onKeyEvent(keyEvent).also { recomposer.performScheduledEffects() }
        }

    override fun sendRotaryScrollEvent(
        verticalScrollPixels: Float,
        horizontalScrollPixels: Float,
        timeMillis: Long,
    ): Boolean =
        postponeInvalidation("BaseComposeScene:sendRotaryScrollEvent") {
            val event =
                RotaryScrollEvent(
                    verticalScrollPixels = verticalScrollPixels,
                    horizontalScrollPixels = horizontalScrollPixels,
                    uptimeMillis = timeMillis,
                )
            processRotaryScrollEvent(event).also { recomposer.performScheduledEffects() }
        }

    override suspend fun withMonotonicFrameClock(block: suspend () -> Unit) {
        val monotonicFrameClock =
            compositionContext.effectCoroutineContext[MonotonicFrameClock]
                ?: error("No MonotonicFrameClock found in compositionContext")
        withContext(monotonicFrameClock) { block() }
    }

    protected fun doMeasureAndLayout() {
        snapshotInvalidationTracker.onMeasureAndLayout()
        measureAndLayout()
    }

    protected abstract fun createComposition(content: @Composable () -> Unit): Composition

    private fun onPointerInputEvent(event: PointerInputEvent) =
        processPointerInputEvent(event).also {
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
