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
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetMaxY
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectIsEmpty
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionCurveEaseInOut
import platform.UIKit.UIViewAnimationOptions

/**
 * Reports the height of the keyboard overlap with the Compose overlay view. The notification frame
 * starts the animation, while the layout guide supplies the actual target keyboard geometry.
 */
internal class KeyboardInsetsManager(
    private val view: UIView,
    private val frameChoreographer: FrameChoreographer,
    private val onKeyboardOverlapHeightChanged: (Dp) -> Unit
) : KeyboardVisibilitySubscriber, FrameChoreographer.Listener {
    private val layoutGuideGeometrySource = KeyboardLayoutGuideGeometrySource(view)
    private var isDisposed: Boolean = false
    private var isStarted = false
    private val activitiesHandler = frameChoreographer.createActivitiesHandler()
    private val coroutineScope = CoroutineScope(frameChoreographer.coroutineContext)
    private var awaitingKeyboardFrameJob: Job? = null
    private var activeAnimation: KeyboardAnimation? = null
    val hasPendingWork get() = activeAnimation != null || awaitingKeyboardFrameJob != null

    private val keyboardFrame: CValue<CGRect>
        get() = resolveKeyboardFrame(
            layoutGuideGeometrySource.keyboardFrame,
            KeyboardVisibilityListener.keyboardFrame
        )

    /**
     * UIKit does not expose a callback for each presentation frame of a keyboard animation.
     * This invisible view is animated with the keyboard notification's timing, and its
     * presentation layer is sampled to interpolate the keyboard top on every display-link tick.
     */
    private data class KeyboardAnimation(
        val progressView: UIView,
        val previousKeyboardTop: Double,
        val targetKeyboardTop: Double,
        val overlayBounds: CValue<CGRect>,
    )

    fun start() {
        if (isStarted) return
        isStarted = true

        KeyboardVisibilityListener.initialize()
        KeyboardVisibilityListener.addSubscriber(this)
        frameChoreographer.addListener(this)

        animateKeyboardOverlap(
            keyboardFrame,
            keyboardFrame,
            0.0,
            UIViewAnimationOptionCurveEaseInOut
        )
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
     * Wait for the first keyboard frame reported after input startup.
     * Without this, test's `waitForIdle` can return before the keyboard frame starts changing.
     * The wait is bounded because hardware keyboards and zero-sized custom input views never
     * report a visible keyboard frame, and the wait would be indefinite.
     */
    fun awaitKeyboardFrameIfNeeded() {
        cancelAwaitingKeyboardFrame()
        if (keyboardHeight(keyboardFrame) == 0.0) {
            awaitingKeyboardFrameJob = coroutineScope.launch {
                delay(KEYBOARD_FRAME_AWAIT_TIMEOUT_MILLIS)
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
        activeAnimation?.let { animation ->
            onKeyboardOverlapHeightChanged(animation.keyboardOverlapHeight())
        }
    }

    override fun keyboardWillShow(
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) = Unit

    override fun keyboardWillChangeFrame(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()
        animateKeyboardOverlap(
            currentFrame = currentFrame,
            targetFrame = targetFrame,
            duration = duration,
            animationOptions = animationOptions
        )
    }

    override fun keyboardWillHide(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()
        // A preceding willChangeFrame may already have animated to the empty target frame.
        if (!CGRectIsEmpty(currentFrame)) {
            animateKeyboardOverlap(
                currentFrame = currentFrame,
                targetFrame = targetFrame,
                duration = duration,
                animationOptions = animationOptions
            )
        }
    }

    /** Starts an animation from the current keyboard top to the target keyboard top. */
    private fun animateKeyboardOverlap(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        val screen = view.window?.screen ?: return
        val resolvedTargetFrame = resolvedTargetKeyboardFrame(targetFrame, screen)

        animateKeyboard(
            previousKeyboardTop = keyboardTop(currentFrame, screen),
            targetKeyboardTop = keyboardTop(resolvedTargetFrame, screen),
            duration = duration,
            animationOptions = animationOptions
        )
    }

    /** Recomputes overlap after geometry changes, unless a keyboard animation is running. */
    fun updateOverlapForCurrentGeometry() {
        // Recomputing would cancel the running animation and snap to the target overlap.
        if (!isStarted || activeAnimation != null) return

        val frame = keyboardFrame
        animateKeyboardOverlap(
            currentFrame = frame,
            targetFrame = frame,
            duration = 0.0,
            animationOptions = UIViewAnimationOptionCurveEaseInOut,
        )
    }

    private fun keyboardTop(frame: CValue<CGRect>, screen: UIScreen): Double {
        // For an empty or undocked frame, animate from the screen's bottom edge
        // rather than y = 0 at the top.
        val keyboardFrame = if (CGRectIsEmpty(frame) || isUndockedKeyboard(frame, screen)) {
            screen.bounds.useContents {
                CGRectMake(origin.x, origin.y + size.height, size.width, 0.0)
            }
        } else {
            frame
        }

        val frameInView =
            view.coordinateSpace.convertRect(
                rect = keyboardFrame,
                fromCoordinateSpace = screen.coordinateSpace,
            )
        return CGRectGetMinY(frameInView)
    }

    private fun resolvedTargetKeyboardFrame(
        notificationFrame: CValue<CGRect>,
        screen: UIScreen
    ): CValue<CGRect> {
        val screenHeight = screen.bounds.useContents { size.height }
        return if (
            CGRectIsEmpty(notificationFrame) ||
            isUndockedKeyboard(notificationFrame, screen) ||
            CGRectGetMinY(notificationFrame) >= screenHeight
        ) {
            CGRectZero.readValue()
        } else {
            keyboardFrame
        }
    }

    private fun keyboardHeight(frame: CValue<CGRect>): Double {
        val screen = view.window?.screen ?: return 0.0
        return if (CGRectIsEmpty(frame) || isUndockedKeyboard(frame, screen)) {
            0.0
        } else {
            max(0.0, screen.bounds.useContents { size.height } - CGRectGetMinY(frame))
        }
    }

    private fun isUndockedKeyboard(frame: CValue<CGRect>, screen: UIScreen): Boolean =
        CGRectGetMaxY(frame) < CGRectGetMaxY(screen.bounds) - KEYBOARD_BOTTOM_EDGE_TOLERANCE

    private fun animateKeyboard(
        previousKeyboardTop: Double,
        targetKeyboardTop: Double,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        // A willHide notification can interrupt an animation to the docked keyboard shortcut bar.
        // Continue from the visible animation position instead of its previous target.
        val currentKeyboardTop = activeAnimation?.keyboardTop() ?: previousKeyboardTop
        cancelActiveAnimation()

        val overlayBounds = view.bounds
        if (currentKeyboardTop == targetKeyboardTop) {
            val overlap =
                (CGRectGetMaxY(overlayBounds) - targetKeyboardTop).coerceIn(
                    0.0,
                    overlayBounds.useContents { size.height },
                )
            onKeyboardOverlapHeightChanged(overlap.dp)
            return
        }

        // This view is never visible and is not the keyboard. UIKit animates its frame only so
        // its presentation layer can be sampled as a progress value for the keyboard transition.
        val progressView = UIView()
        view.addSubview(progressView)
        val animation =
            KeyboardAnimation(
                progressView = progressView,
                previousKeyboardTop = currentKeyboardTop,
                targetKeyboardTop = targetKeyboardTop,
                overlayBounds = overlayBounds,
            )
        activeAnimation = animation
        activitiesHandler.onActivitiesStarted()

        val animationTargetFrame = CGRectMake(0.0, 0.0, 0.0, ANIMATION_TARGET_SIZE)

        UIView.animateWithDuration(
            duration = duration,
            delay = 0.0,
            options = animationOptions,
            animations = {
                progressView.setFrame(animationTargetFrame)
            },
            completion = { _ ->
                finishAnimation(animation)
            }
        )
    }

    private fun KeyboardAnimation.progress(): Double {
        val layer = progressView.layer.presentationLayer() ?: return 0.0
        return layer.frame.useContents { size.height / ANIMATION_TARGET_SIZE }
    }

    private fun KeyboardAnimation.keyboardOverlapHeight(progress: Double = progress()): Dp {
        val keyboardTop = keyboardTop(progress)
        // Clamp after interpolation so the inset stays zero until the keyboard reaches the view.
        return (CGRectGetMaxY(overlayBounds) - keyboardTop)
            .coerceIn(0.0, overlayBounds.useContents { size.height })
            .dp
    }

    private fun KeyboardAnimation.keyboardTop(progress: Double = progress()): Double =
        previousKeyboardTop + (targetKeyboardTop - previousKeyboardTop) * progress

    private fun finishAnimation(animation: KeyboardAnimation) {
        if (activeAnimation !== animation) {
            animation.progressView.removeFromSuperview()
            return
        }

        val finalOverlapHeight = animation.keyboardOverlapHeight(progress = 1.0)
        onKeyboardOverlapHeightChanged(finalOverlapHeight)
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        animation.progressView.removeFromSuperview()
    }

    private fun cancelActiveAnimation() {
        val animation = activeAnimation ?: return
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        UIView.performWithoutAnimation {
            animation.progressView.layer.removeAllAnimations()
            animation.progressView.removeFromSuperview()
        }
    }

    private companion object {
        const val ANIMATION_TARGET_SIZE = 1000.0
        const val KEYBOARD_BOTTOM_EDGE_TOLERANCE = 0.5
        val KEYBOARD_FRAME_AWAIT_TIMEOUT_MILLIS = 500.milliseconds
    }
}
