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

package androidx.compose.ui.platform

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.TestOnly
import androidx.compose.ui.node.WeakReference
import androidx.compose.ui.uikit.toNanoSeconds
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.DisplayLinkFrameRate
import androidx.compose.ui.window.OutOfFrameExecutor
import androidx.compose.ui.window.SceneForegroundStateListener
import kotlin.coroutines.CoroutineContext
import kotlin.math.min
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSTimeInterval
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIWindowScene
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.objc.OBJC_ASSOCIATION_RETAIN
import platform.objc.objc_getAssociatedObject
import platform.objc.objc_setAssociatedObject

/**
 * Manages recomposition, frame adjustment, and rendering synchronization for all Compose containers
 * inside a single `UIWindowScene`.
 */
internal class FrameChoreographer private constructor(
    scene: UIWindowScene,
    val coroutineContext: CoroutineContext = Dispatchers.Main
) {
    companion object {
        fun choreographerForScene(scene: UIWindowScene): FrameChoreographer {
            return scene.frameChoreographer ?: FrameChoreographer(scene).also {
                scene.frameChoreographer = it
            }
        }

        @TestOnly
        fun configureForScene(scene: UIWindowScene, coroutineContext: CoroutineContext) {
            scene.frameChoreographer?.dispose()
            scene.frameChoreographer = FrameChoreographer(scene, coroutineContext)
        }

        private const val FramesToAdvanceAfterInvalidation = 2
    }


    /**
     * Interface for receiving callbacks related to frame rendering and out-of-frame processing.
     */
    interface Listener {
        /**
         * Callback method triggered on each frame refresh in synchronization with the display's refresh rate.
         */
        fun onDisplayLinkTick()

        /**
         * The next runloop is performed after all draw calls are processed and before the next
         * runloop starts, so this is the moment out-of-frame work should run.
         */
        fun onOutOfFrame(lastFrameTimestamp: NSTimeInterval, targetTimestamp: NSTimeInterval) = Unit
    }

    /**
     * Tracks ongoing activities that keep the display link running (i.e. producing frames) even
     * when there is nothing to redraw, such as an in-progress animation or gesture.
     */
    interface ActivitiesHandler {
        /**
         * Registers [count] started activities. While at least one activity is ongoing the display
         * link keeps ticking.
         */
        fun onActivitiesStarted(count: Int = 1)

        /**
         * Marks [count] previously started activities as finished, allowing the display link to
         * pause once no activities remain.
         */
        fun onActivitiesEnded(count: Int = 1)

        /**
         * Releases this handler and ends any activities it still holds.
         */
        fun dispose()
    }

    val frameRecomposer = FrameRecomposer(
        coroutineContext = coroutineContext,
        invalidate = ::requestFrame
    )

    private val displayLink = CADisplayLink.displayLinkWithTarget(
        target = DisplayLinkProxy(::onDisplayLinkTick),
        selector = NSSelectorFromString(DisplayLinkProxy::handleDisplayLinkTick.name)
    ).also {
        it.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
    }

    private val displayLinkFrameRate = DisplayLinkFrameRate(displayLink).also {
        val maximumFramesPerSecond = scene.screen.maximumFramesPerSecond
        it.maximumFramesPerSecond = maximumFramesPerSecond
        it.preferredFramesPerSecond = maximumFramesPerSecond
    }

    private val sceneRef = WeakReference(scene)
    private val foregroundStateListener = SceneForegroundStateListener(
        getScene = { sceneRef.get() },
        onSceneForegroundStateChanged = { inForeground -> isSceneInForeground = inForeground }
    )

    private var isSceneInForeground: Boolean = foregroundStateListener.isSceneInForeground
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                requestFrame()
            } else {
                displayLink.paused = true
            }
        }

    val outOfFrameExecutor = OutOfFrameExecutor()

    /**
     * The [listeners] list must not be changed when providing notifications. Also, [onDisplayLinkTick]
     * callback must be called first for every listener no matter at what time it is added.
     *
     * To satisfy the aforementioned conditions and reduce extra runtime memory allocations,
     * use extra lists to hold pending adds / removals.
     */
    private val listeners = mutableListOf<Listener>()
    private val pendingListenersToAdd = mutableListOf<Listener>()
    private val pendingListenersToRemove = mutableListOf<Listener>()

    @TestOnly
    fun dispose() {
        displayLink.invalidate()
        foregroundStateListener.dispose()
        frameRecomposer.close()
        outOfFrameExecutor.dispose()

        listeners.clear()
        pendingListenersToAdd.clear()
        pendingListenersToRemove.clear()
    }

    fun addListener(listener: Listener) {
        pendingListenersToAdd.add(listener)
        pendingListenersToRemove.remove(listener)
    }

    fun removeListener(listener: Listener) {
        pendingListenersToRemove.add(listener)
        pendingListenersToAdd.remove(listener)
    }

    fun voteFrameRate(frameRate: Float, frameRateCategory: Float) {
        displayLinkFrameRate.voteFrameRate(frameRate, frameRateCategory)
    }

    private var isPerformingFrame = false
    fun performFrameIfNeeded() {
        if (isPerformingFrame) return
        isPerformingFrame = true
        try {
            frameRecomposer.performFrame(displayLink.targetTimestamp.toNanoSeconds())
        } finally {
            isPerformingFrame = false
        }
    }

    private var ongoingActivitiesCount: Int = 0
        set(value) {
            assert(value >= 0)
            field = value
            requestFrame()
        }

    fun createActivitiesHandler(): ActivitiesHandler {
        return object : ActivitiesHandler {
            var handlerActivitiesCounter = 0
            var disposed = false

            override fun onActivitiesStarted(count: Int) {
                if (disposed) return
                handlerActivitiesCounter += count
                ongoingActivitiesCount += count
            }

            override fun onActivitiesEnded(count: Int) {
                if (disposed) return
                // TODO: CMP-10557 - Remove min and fix gestures counter
                val endCount = min(count, ongoingActivitiesCount)
                ongoingActivitiesCount -= endCount
                handlerActivitiesCounter -= endCount
                assert(handlerActivitiesCounter >= 0)
            }

            override fun dispose() {
                ongoingActivitiesCount -= handlerActivitiesCounter
                handlerActivitiesCounter = 0
                disposed = true
            }
        }
    }

    private var advancedFramesCount = FramesToAdvanceAfterInvalidation
    fun requestFrame() {
        advancedFramesCount = FramesToAdvanceAfterInvalidation
        if (isSceneInForeground) {
            displayLink.paused = false
        }
    }

    @VisibleForTesting
    val preferredFramesPerSecond: NSInteger get() = displayLink.preferredFramesPerSecond

    @VisibleForTesting
    val currentTargetFrameDuration: NSTimeInterval
        get() = displayLink.targetTimestamp - displayLink.timestamp

    private fun onDisplayLinkTick() {
        val lastFrameTimestamp = displayLink.timestamp
        val targetTimestamp = displayLink.targetTimestamp

        // Drain out-of-frame work scheduled between frames before producing this frame.
        outOfFrameExecutor.onFrameStart()

        applyPendingListeners()
        dispatch_async(dispatch_get_main_queue()) {
            outOfFrameExecutor.onFrameEnd()

            applyPendingListenersToRemove()
            listeners.fastForEach { it.onOutOfFrame(lastFrameTimestamp, targetTimestamp) }
        }
        listeners.fastForEach { it.onDisplayLinkTick() }

        advancedFramesCount--

        displayLinkFrameRate.updateFrameRateIfNeeded()
        performFrameIfNeeded()
        if (advancedFramesCount <= 0 && ongoingActivitiesCount == 0) {
            advancedFramesCount = 0
            displayLink.paused = true
        }
    }

    private fun applyPendingListeners() {
        if (pendingListenersToAdd.isNotEmpty()) {
            listeners.addAll(pendingListenersToAdd)
            pendingListenersToAdd.clear()
        }
        applyPendingListenersToRemove()
    }

    private fun applyPendingListenersToRemove() {
        if (pendingListenersToRemove.isNotEmpty()) {
            listeners.removeAll(pendingListenersToRemove)
            pendingListenersToRemove.clear()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private val frameChoreographerAssociationKey: COpaquePointer = nativeHeap.alloc<IntVar>().ptr

@OptIn(ExperimentalForeignApi::class)
private var UIWindowScene.frameChoreographer: FrameChoreographer?
    get() = objc_getAssociatedObject(this, frameChoreographerAssociationKey) as? FrameChoreographer
    set(value) {
        objc_setAssociatedObject(this, frameChoreographerAssociationKey, value, OBJC_ASSOCIATION_RETAIN)
    }

private class DisplayLinkProxy(
    private val callback: () -> Unit
) : NSObject() {
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun handleDisplayLinkTick() {
        callback()
    }
}
