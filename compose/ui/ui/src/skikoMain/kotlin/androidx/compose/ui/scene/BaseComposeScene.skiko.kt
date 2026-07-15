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
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ObserverHandle
import androidx.compose.runtime.enter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withTransaction
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
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.ProvidePlatformCompositionLocals
import androidx.compose.ui.util.trace
import kotlin.concurrent.Volatile

/**
 * BaseComposeScene is an internal abstract class that implements the ComposeScene interface.
 * It provides a base implementation for managing composition, input events, and rendering.
 *
 * @property composeSceneContext the object that used to share "context" between multiple scenes
 * on the screen. Also, it provides a way for platform interaction that is required within a scene.
 */
@OptIn(InternalComposeUiApi::class, InternalComposeApi::class)
internal abstract class BaseComposeScene(
    protected val frameRecomposer: FrameRecomposer,
    dataSourceContext: DataSourceContext = DataSourceContext(),
    private val invalidateLayout: () -> Unit,
    private val invalidateDraw: () -> Unit,
) : ComposeScene {
    private val isFrameIsolationEnabled = ComposeSceneFeatureFlags.isFrameIsolationEnabled

    /**
     * The scene's frame domain: carries the [DataSourceContext] (the flag-off composing path fans
     * out through it too), the current frame-cycle unit while frame isolation is on (rotated by
     * the host [FrameRecomposer] at the start of each frame), and the pending invalidations
     * delivered at that rotation.
     */
    private val frameSnapshotHolder: SnapshotHolder =
        SnapshotHolder(dataSourceContext, isolating = isFrameIsolationEnabled)

    /**
     * The pin is swapped once per host frame, so the domain lives in the host's registry rather
     * than being rotated from a scene phase - `measureAndLayout()` is also a public phase that can
     * run outside a frame, and a scene must not start a new frame cycle from one.
     */
    private val frameDomainRegistration: AutoCloseable =
        frameRecomposer.registerFrameDomain(frameSnapshotHolder)

    /**
     * Fully qualified elsewhere in fleet's code because
     * `androidx.compose.runtime.snapshots.ObserverHandle` is a separate, identically-shaped
     * interface; this handle comes from [DataSourceContext].
     */
    private var contextWakeHandle: ObserverHandle? = null

    init {
        // Wakes render scheduling when a foreign commit lands in this domain's pending union -
        // only fires for an activated (frame-isolation-on) holder. Wired during construction, so
        // it is in place before activateFrameDomain() runs (wake-wired-before-activate).
        frameSnapshotHolder.onPendingDelivery = { invokeInvalidationCallbacks() }
        // The other half: a member of this scene's context signalling that it holds unpublished
        // data. Unlike the delivery wake above, this one matters regardless of frame isolation.
        contextWakeHandle = frameSnapshotHolder.context.registerWake { invokeInvalidationCallbacks() }
    }

    /**
     * Activates the frame domain: takes the standing pin's substrate snapshot and registers this
     * holder for delivery routing. INVARIANT: this MUST be called immediately after construction
     * completes (from the factory / construction site), NOT during construction. Activation
     * snapshots the pin, so every scene-owned snapshot state - this base class's
     * [compositionLocalContext] plus all subclass property initializers - must predate the pin BY
     * CONSTRUCTION. Any isolated slice that runs before the first rotation would otherwise read a
     * state created after the pin's snapshot and fail fast with "Reading a state that was created
     * after the snapshot was taken". Corollary: scene-owned snapshot state must not be created
     * post-construction outside a slice. No-op when frame isolation is disabled.
     */
    internal fun activateFrameDomain() {
        if (isFrameIsolationEnabled) frameSnapshotHolder.activate()
    }

    override val currentFrameSnapshot: DataSource.Snapshot?
        get() = frameSnapshotHolder.checkedCurrent

    /**
     * Runs [block] with the current frame unit's read view bound to this thread: reads see the
     * frame's view, no transaction is opened, no snapshot is taken and nothing publishes. With
     * frame isolation disabled there is no unit, so [block] runs bare and stock behavior is
     * unchanged.
     */
    private inline fun <T> enterCurrentUnit(block: () -> T): T {
        val unit = frameSnapshotHolder.checkedCurrent
        return if (unit != null) unit.enter(block) else block()
    }

    /**
     * Runs [block] as one slice of the frame cycle, published atomically - with delivery of its
     * invalidations - when the block ends. [withTransaction] merges into an enclosing slice
     * instead if one is already current, so the outermost boundary owns the publish. With frame
     * isolation disabled there is no unit and [block] runs bare, leaving the phase sequence
     * exactly as upstream has it.
     */
    private inline fun <T> withFrameSlice(block: () -> T): T {
        val unit = frameSnapshotHolder.checkedCurrent
        return if (unit != null) unit.withTransaction(block) else block()
    }
    protected val inputHandler: ComposeSceneInputHandler =
        ComposeSceneInputHandler(
            prepareForPointerInputEvent = ::doMeasureAndLayout,
            processPointerInputEvent = ::onPointerInputEvent,
            cancelPointerInput = ::processCancelPointerInput,
            processKeyEvent = ::processKeyEvent,
        )

    private var composition: Composition? = null

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
            if (isInvalidationDisabled) return block()
            isInvalidationDisabled = true
            return try {
                // The read scope covers the WHOLE ingress: dispatching invalidations needs a view,
                // and a handler that reads a data source needs one too. This is independent of
                // [isolated], which decides only whether a TRANSACTION is opened - the render
                // phases deliberately open none at this level, yet still need a view.
                enterCurrentUnit {
                    if (isolated) withFrameSlice(block) else block()
                }
            } finally {
                isInvalidationDisabled = false
            }.also {
                invokeInvalidationCallbacks()
            }
        }

    @Volatile
    protected var hasForcedLayout: Boolean = false
        private set

    @Volatile
    protected var hasForcedDraw: Boolean = false
        private set

    protected fun invokeInvalidationCallbacks(
        forceLayout: Boolean = false,
        forceDraw: Boolean = false,
    ) {
        hasForcedLayout = hasForcedLayout || forceLayout
        hasForcedDraw = hasForcedDraw || forceDraw
        if (isInvalidationDisabled || isClosed || composition == null) return
        if (hasForcedLayout || hasPendingMeasureOrLayout) {
            invalidateLayout()
        }
        if (hasForcedDraw || hasPendingDraw || hasPendingFrameDomainWork) {
            invalidateDraw()
        }
    }

    /**
     * Work owned by the frame domain rather than by the layout tree: invalidations waiting for the
     * next pin swap, and foreign sources holding unpublished data. Without the latter a store-only
     * change would request no frame at all and the UI would stay stale until something else
     * happened to render.
     */
    private val hasPendingFrameDomainWork: Boolean
        get() =
            frameSnapshotHolder.hasPendingDelivery || frameSnapshotHolder.context.hasPendingAdvance

    override var compositionLocalContext: CompositionLocalContext? by mutableStateOf(null)

    /**
     * The last known position of pointer cursor position or `null` if cursor is not inside a scene.
     *
     * TODO: Move it to PlatformContext
     */
    val lastKnownPointerPosition by inputHandler::lastKnownPointerPosition

    override fun close() {
        check(!isClosed) { "ComposeScene is already closed" }
        isClosed = true

        contextWakeHandle?.dispose()
        contextWakeHandle = null
        frameDomainRegistration.close()

        // With frame isolation enabled, close() must not be called from within a frame, input or
        // effect slice (e.g. an event handler that synchronously closes its own scene): the
        // slice's child snapshot is still open there and dispose() fails fast with "Cannot dispose
        // while a child snapshot is open". Previously this same reentrant pattern silently
        // corrupted the unit's state instead of failing.
        frameSnapshotHolder.close()
        composition?.dispose()
    }

    override fun setContent(
        parentCompositionContext: CompositionContext?,
        content: @Composable () -> Unit,
    ) = postponeInvalidation("BaseComposeScene:setContent") {
            check(!isClosed) { "setContent called after ComposeScene is closed" }
            inputHandler.onChangeContent()

            /*
             * This is usually a no-op for the first composition, but it must drain any stale
             * host work from the previous content before replacing the composition. Otherwise,
             * changed parameters can be applied in a separate turn and trigger double
             * recomposition when new content is installed.
             */
            frameRecomposer.performFrameDispatch()
            composition?.dispose()
            composition = createComposition(
                parentCompositionContext = parentCompositionContext ?: frameRecomposer.compositionContext,
            ) {
                ProvidePlatformCompositionLocals(
                    @Suppress("DEPRECATION")
                    LocalComposeScene provides this,
                    LocalComposeSceneContext provides composeSceneContext,
                    platformContext = composeSceneContext.platformContext,
                    content = content
                )
            }
            frameRecomposer.performFrameDispatch()
        }

    override fun measureAndLayout() {
        if (isClosed) return
        hasForcedLayout = false

        // isolated = false: the phase opens its own slice below, so the ingress must only bind
        // the read view. Wrapping here as well would merge the phase into the ingress slice and
        // defer its publication past the phase boundary.
        postponeInvalidation("BaseComposeScene:measureAndLayout", isolated = false) {
            withFrameSlice {
                doMeasureAndLayout()

                // Schedule synthetic events to be sent after measure/layout completes.
                if (inputHandler.needUpdatePointerPosition) {
                    frameRecomposer.dispatch {
                        inputHandler.updatePointerPosition()
                    }
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (isClosed) return
        hasForcedDraw = false

        postponeInvalidation("BaseComposeScene:draw", isolated = false) {
            // With frame isolation on, publishing each phase slice is what makes the preceding
            // phase's invalidations visible to the next - the isolated equivalent of the two
            // global-snapshot advances below. Reaching into the global snapshot from inside a
            // frame would publish foreign writes mid-frame and tear it.
            val isolated = frameSnapshotHolder.checkedCurrent != null

            if (!isolated) {
                // FIXME: Remove applying the global snapshot here.
                //  Android never applies the snapshot *between* the layout and draw phases
                //  (applies happen once per frame on the main looper, not between phases).
                //  This between-phase apply is a temporary workaround kept only to preserve
                //  current behavior for OffsetToFocusedRect (iOS FocusableAboveKeyboard).
                Snapshot.sendApplyNotifications()
            }

            // AndroidComposeView.dispatchDraw() begins with measureAndLayout() so layout changes
            // discovered after the host layout traversal are still settled before drawing. Keep
            // that trailing layout pass here even though measureAndLayout() is also a public phase.
            withFrameSlice { doMeasureAndLayout() }

            if (!isolated) {
                // Advance the global snapshot before drawing so writes made since the last pass
                // including state objects created during a prior draw are recorded as modified
                // and visible to this draw. Lighter than sendApplyNotifications, matches Android.
                Snapshot.notifyObjectsInitialized()
            }

            withFrameSlice { doDraw(canvas) }
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
            frameRecomposer.performTrampolineDispatch()
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
            frameRecomposer.performTrampolineDispatch()
        }
    }

    override fun cancelPointerInput() {
        inputHandler.cancelPointerInput()
    }

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean =
        postponeInvalidation("BaseComposeScene:sendKeyEvent") {
            inputHandler.onKeyEvent(keyEvent).also {
                frameRecomposer.performTrampolineDispatch()
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
            frameRecomposer.performTrampolineDispatch()
        }
    }

    protected abstract fun createComposition(
        parentCompositionContext: CompositionContext,
        content: @Composable () -> Unit
    ): Composition

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
