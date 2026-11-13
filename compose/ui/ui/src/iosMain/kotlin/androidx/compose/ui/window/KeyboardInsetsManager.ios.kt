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

package androidx.compose.ui.window

import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectGetMaxY
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptions

internal class KeyboardInsetsManager(
    private val view: UIView,
    private val frameChoreographer: FrameChoreographer,
    private val onKeyboardOverlapHeightChanged: (Dp) -> Unit,
    private val keyboardOverlapHeightSource: () -> Dp = { keyboardOverlapHeightFromKeyboardLayoutGuide(view) },
    private val shouldDeferZeroKeyboardOverlapHeight: () -> Boolean = { isDockedKeyboardTransitionInProgress(view) },
) : KeyboardVisibilitySubscriber, FrameChoreographer.Listener {
    private var isDisposed: Boolean = false
    private var isStarted = false
    private val activitiesHandler = frameChoreographer.createActivitiesHandler()
    private val coroutineScope = CoroutineScope(frameChoreographer.coroutineContext)
    private var awaitingKeyboardFrameJob: Job? = null
    private var activeAnimation: KeyboardAnimation? = null
    private var lastKeyboardOverlapHeight = 0.0
    val hasPendingWork get() = activeAnimation != null || awaitingKeyboardFrameJob != null

    private class KeyboardAnimation(
        val animationView: UIView,
        val startHeight: Double,
        val targetHeight: Double,
    ) {
        fun progress(): Double {
            val layer = animationView.layer.presentationLayer() ?: return 0.0
            return layer.frame.useContents { size.height / ANIMATION_TARGET_SIZE }
        }

        fun currentHeight(): Double = startHeight + (targetHeight - startHeight) * progress()
    }

    fun start() {
        if (isStarted) return
        isStarted = true

        KeyboardVisibilityListener.initialize()
        KeyboardVisibilityListener.addSubscriber(this)
        frameChoreographer.addListener(this)
        updateOverlapForCurrentGeometry()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        cancelAwaitingKeyboardFrame()
        KeyboardVisibilityListener.removeSubscriber(this)
        frameChoreographer.removeListener(this)
        cancelActiveAnimation()
    }

    fun dispose() {
        check(!isDisposed) { "KeyboardInsetsManager is already disposed" }
        isDisposed = true
        stop()
        activitiesHandler.dispose()
    }

    /**
     * Wait for the first keyboard layout-guide update after input startup.
     * Without this, test's `waitForIdle` can return before the keyboard starts moving.
     * The wait is bounded because hardware keyboards and zero-sized custom input views never
     * produce a visible layout-guide inset, and the wait would be indefinite.
     */
    fun awaitKeyboardFrameIfNeeded() {
        cancelAwaitingKeyboardFrame()
        if (keyboardOverlapHeightSource() == 0.dp) {
            awaitingKeyboardFrameJob = coroutineScope.launch {
                delay(KEYBOARD_INSET_AWAIT_TIMEOUT_MILLIS)
                if (awaitingKeyboardFrameJob === coroutineContext[Job]) {
                    awaitingKeyboardFrameJob = null
                }
            }
        }
    }

    fun cancelAwaitingKeyboardFrame() {
        awaitingKeyboardFrameJob?.cancel()
        awaitingKeyboardFrameJob = null
    }

    override fun onDisplayLinkTick() {
        val animation = activeAnimation ?: return
        publishKeyboardOverlapHeight(animation.currentHeight())
    }

    // Notification frames do not reliably represent custom input and accessory views, so they
    // provide timing only.
    override fun keyboardWillShow(
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        animateToKeyboardLayoutGuide(duration, animationOptions)
    }

    override fun keyboardWillHide(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()
        animateToKeyboardLayoutGuide(duration, animationOptions)
    }

    override fun keyboardWillChangeFrame(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()
        animateToKeyboardLayoutGuide(duration, animationOptions)
    }

    /** Publishes the current keyboard overlap from UIKit's rendered keyboard layout guide. */
    fun updateOverlapForCurrentGeometry() {
        // Recomputing would cancel the running animation and snap to the target overlap.
        if (!isStarted || activeAnimation != null) return

        val keyboardOverlapHeight = keyboardOverlapHeightSource()
        val deferZeroKeyboardOverlapHeight = keyboardOverlapHeight == 0.dp && lastKeyboardOverlapHeight > 0.0 && shouldDeferZeroKeyboardOverlapHeight()
        if (deferZeroKeyboardOverlapHeight) {
            return
        }
        if (keyboardOverlapHeight > 0.dp) {
            cancelAwaitingKeyboardFrame()
        }
        publishKeyboardOverlapHeight(keyboardOverlapHeight.value.toDouble())
    }

    private fun animateToKeyboardLayoutGuide(duration: Double, animationOptions: UIViewAnimationOptions) {
        // UIKit may deliver the keyboard notification before it lays out the guide's new target.
        view.layoutIfNeeded()

        val targetHeight = keyboardOverlapHeightSource().value.toDouble()
        val currentAnimation = activeAnimation
        if (currentAnimation?.targetHeight == targetHeight) return

        val startHeight = currentAnimation?.currentHeight() ?: lastKeyboardOverlapHeight
        cancelActiveAnimation()

        if (duration <= 0.0 || startHeight == targetHeight) {
            publishKeyboardOverlapHeight(targetHeight)
            return
        }

        val animationView = UIView()
        view.addSubview(animationView)
        val animation = KeyboardAnimation(
            animationView = animationView,
            startHeight = startHeight,
            targetHeight = targetHeight,
        )
        activeAnimation = animation
        activitiesHandler.onActivitiesStarted()

        val animationTargetFrame = CGRectMake(0.0, 0.0, 0.0, ANIMATION_TARGET_SIZE)

        UIView.animateWithDuration(
            duration = duration,
            delay = 0.0,
            options = animationOptions,
            animations = {
                animationView.setFrame(animationTargetFrame)
            },
            completion = { _ ->
                finishAnimation(animation)
            }
        )
    }

    private fun publishKeyboardOverlapHeight(overlapHeight: Double) {
        lastKeyboardOverlapHeight = overlapHeight
        onKeyboardOverlapHeightChanged(overlapHeight.dp)
    }

    private fun cancelActiveAnimation() {
        if (activeAnimation == null) return
        val animation = activeAnimation!!
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        UIView.performWithoutAnimation {
            animation.animationView.layer.removeAllAnimations()
            animation.animationView.removeFromSuperview()
        }
    }

    private fun finishAnimation(animation: KeyboardAnimation) {
        if (activeAnimation !== animation) {
            animation.animationView.removeFromSuperview()
            return
        }
        publishKeyboardOverlapHeight(animation.targetHeight)
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        animation.animationView.removeFromSuperview()
    }

    private companion object {
        const val ANIMATION_TARGET_SIZE = 1000.0
        val KEYBOARD_INSET_AWAIT_TIMEOUT_MILLIS = 500.milliseconds
    }
}

private fun keyboardOverlapHeightFromKeyboardLayoutGuide(view: UIView): Dp {
    val layoutFrame = view.keyboardLayoutGuide.layoutFrame
    return keyboardOverlapHeightForKeyboardLayoutGuide(
        viewBottom = CGRectGetMaxY(view.bounds),
        layoutGuideBottom = CGRectGetMaxY(layoutFrame),
        layoutGuideHeight = CGRectGetHeight(layoutFrame),
        safeAreaBottom = view.safeAreaInsets.useContents { bottom },
    )
}

internal fun keyboardOverlapHeightForKeyboardLayoutGuide(
    viewBottom: Double,
    layoutGuideBottom: Double,
    layoutGuideHeight: Double,
    safeAreaBottom: Double,
): Dp {
    if (viewBottom == 0.0) return 0.dp

    if (layoutGuideBottom < viewBottom - KEYBOARD_BOTTOM_EDGE_TOLERANCE) return 0.dp

    // When the keyboard is hidden, UIKit ties the guide to the safe-area bottom.
    return if (layoutGuideHeight <= safeAreaBottom) 0.dp else layoutGuideHeight.dp
}

private fun isDockedKeyboardTransitionInProgress(view: UIView): Boolean =
    keyboardLayoutGuideIsAheadOfViewLayout(view) || keyboardFrameIsAttachedToLeadingScreenEdge()

private fun keyboardLayoutGuideIsAheadOfViewLayout(view: UIView): Boolean {
    val layoutFrame = view.keyboardLayoutGuide.layoutFrame
    val safeAreaBottom = view.safeAreaInsets.useContents { bottom }
    return CGRectGetHeight(layoutFrame) > safeAreaBottom &&
        CGRectGetMaxY(layoutFrame) < CGRectGetMaxY(view.bounds) - KEYBOARD_BOTTOM_EDGE_TOLERANCE
}

private fun keyboardFrameIsAttachedToLeadingScreenEdge(): Boolean {
    val keyboardFrame = KeyboardVisibilityListener.keyboardFrame
    return CGRectGetHeight(keyboardFrame) > 0.0 &&
        CGRectGetMinX(keyboardFrame) <= KEYBOARD_BOTTOM_EDGE_TOLERANCE
}

private const val KEYBOARD_BOTTOM_EDGE_TOLERANCE = 0.5
