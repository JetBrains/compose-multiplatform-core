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

import androidx.compose.runtime.TestOnly
import androidx.compose.ui.uikit.toNanoSeconds
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.DisplayLinkFrameRate
import androidx.compose.ui.window.MetalOutOfFrameExecutor
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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

internal class FrameChoreographer(
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
            scene.frameChoreographer = FrameChoreographer(scene, coroutineContext)
        }
    }

    interface Listener {
        fun onDisplayLink()

        fun onOutOfFrame(lastFrameTimestamp: NSTimeInterval, targetTimestamp: NSTimeInterval)
    }

    val frameRecomposer = FrameRecomposer(
        coroutineContext = coroutineContext,
        invalidate = ::setNeedsRedraw
    )

    private val displayLink = CADisplayLink.displayLinkWithTarget(
        target = SurfaceDisplayLinkProxy(::onDisplayLink),
        selector = NSSelectorFromString(SurfaceDisplayLinkProxy::handleDisplayLinkTick.name)
    ).also {
        it.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
    }

    private val displayLinkFrameRate = DisplayLinkFrameRate(displayLink).also {
        val maximumFramesPerSecond = scene.screen.maximumFramesPerSecond
        it.maximumFramesPerSecond = maximumFramesPerSecond
        it.preferredFramesPerSecond = maximumFramesPerSecond
    }

    val outOfFrameExecutor = MetalOutOfFrameExecutor()

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun voteFrameRate(frameRate: Float, frameRateCategory: Float) {
        displayLinkFrameRate.voteFrameRate(frameRate, frameRateCategory)
    }

    var ongoingActivitiesCount: Int = 0
        set(value) {
            assert(value >= 0)
            field = value
            setNeedsRedraw()
        }

    private var advancedFramesCount = 2
    fun setNeedsRedraw() {
        advancedFramesCount = 2
        displayLink.paused = false
    }

    val targetTimestamp get() = displayLink.targetTimestamp

    val preferredFramesPerSecond: NSInteger get() = displayLink.preferredFramesPerSecond

    val currentTargetFrameDuration: NSTimeInterval
        get() = displayLink.targetTimestamp - displayLink.timestamp

    private fun onDisplayLink() {
        val lastFrameTimestamp = displayLink.timestamp
        val targetTimestamp = displayLink.targetTimestamp

        // Drain out-of-frame work scheduled between frames before producing this frame.
        outOfFrameExecutor.onFrameStart()

        val outOfFrameListeners = listeners.toList()
        dispatch_async(dispatch_get_main_queue()) {
            // The next runloop is performed after all draw calls are processed and before the next
            // runloop starts, so this is the moment out-of-frame work should run.
            outOfFrameExecutor.onFrameEnd()
            outOfFrameListeners.fastForEach { it.onOutOfFrame(lastFrameTimestamp, targetTimestamp) }
        }

        listeners.toList().fastForEach { it.onDisplayLink() }

        val timestamp = lastFrameTimestamp.toNanoSeconds()
        advancedFramesCount--

        displayLinkFrameRate.updateFrameRateIfNeeded()
        frameRecomposer.performFrame(timestamp)
        if (advancedFramesCount <= 0 && ongoingActivitiesCount == 0) {
            advancedFramesCount = 0
            displayLink.paused = true
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private val frameChoreographerAssociationKey: COpaquePointer = nativeHeap.alloc<IntVar>().ptr

@OptIn(ExperimentalForeignApi::class)
internal var UIWindowScene.frameChoreographer: FrameChoreographer?
    get() = objc_getAssociatedObject(this, frameChoreographerAssociationKey) as? FrameChoreographer
    set(value) {
        objc_setAssociatedObject(this, frameChoreographerAssociationKey, value, OBJC_ASSOCIATION_RETAIN)
    }

private class SurfaceDisplayLinkProxy(
    private val callback: () -> Unit
) : NSObject() {
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun handleDisplayLinkTick() {
        callback()
    }
}
