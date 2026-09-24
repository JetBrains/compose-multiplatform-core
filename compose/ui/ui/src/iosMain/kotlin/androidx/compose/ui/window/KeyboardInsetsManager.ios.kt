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
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionCurveEaseInOut
import platform.UIKit.UIViewAnimationOptions

internal class KeyboardInsetsManager(
    private val view: UIView,
    private val frameChoreographer: FrameChoreographer,
    private val onKeyboardOverlapHeightChanged: (Dp) -> Unit
) : KeyboardVisibilitySubscriber, FrameChoreographer.Listener {
    private var isDisposed: Boolean = false
    private var isStarted = false
    private val activitiesHandler = frameChoreographer.createActivitiesHandler()
    private val coroutineScope = CoroutineScope(frameChoreographer.coroutineContext)
    private var awaitingKeyboardFrameJob: Job? = null
    private var activeAnimation: KeyboardAnimation? = null
    val hasPendingWork get() = activeAnimation != null || awaitingKeyboardFrameJob != null
    private data class KeyboardAnimation(
        val view: UIView,
        val previousKeyboardTop: Double,
        val targetKeyboardTop: Double,
        val viewBounds: CValue<CGRect>,
    )

    fun start() {
        if (isStarted) return
        isStarted = true

        KeyboardVisibilityListener.initialize()
        KeyboardVisibilityListener.addSubscriber(this)
        frameChoreographer.addListener(this)

        adjustViewBounds(
            KeyboardVisibilityListener.keyboardFrame,
            KeyboardVisibilityListener.keyboardFrame,
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
        if (keyboardHeight(KeyboardVisibilityListener.keyboardFrame) == 0.0) {
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
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()

        adjustViewBounds(
            currentFrame = KeyboardVisibilityListener.keyboardFrame,
            targetFrame = targetFrame,
            duration = duration,
            animationOptions = animationOptions
        )
    }

    override fun keyboardWillHide(
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelAwaitingKeyboardFrame()
    }

    private fun adjustViewBounds(
        currentFrame: CValue<CGRect>,
        targetFrame: CValue<CGRect>,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        val screen = view.window?.screen ?: return

        animateKeyboard(
            previousKeyboardTop = keyboardTop(currentFrame, screen),
            targetKeyboardTop = keyboardTop(targetFrame, screen),
            duration = duration,
            animationOptions = animationOptions
        )
    }

    /** Recomputes overlap after geometry changes, unless a keyboard animation is running. */
    fun updateOverlapForCurrentGeometry() {
        // Recomputing would cancel the running animation and snap to the target overlap.
        if (!isStarted || activeAnimation != null) return

        val frame = KeyboardVisibilityListener.keyboardFrame
        adjustViewBounds(
            currentFrame = frame,
            targetFrame = frame,
            duration = 0.0,
            animationOptions = UIViewAnimationOptionCurveEaseInOut,
        )
    }

    private fun keyboardTop(frame: CValue<CGRect>, screen: UIScreen): Double {
        // For an empty frame, animate from the screen's bottom edge rather than y = 0 at the top.
        val keyboardFrame =
            if (CGRectIsEmpty(frame)) {
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

    private fun keyboardHeight(frame: CValue<CGRect>): Double {
        val screen = view.window?.screen ?: return 0.0
        return if (CGRectIsEmpty(frame)) {
            0.0
        } else {
            max(0.0, screen.bounds.useContents { size.height } - CGRectGetMinY(frame))
        }
    }

    private fun animateKeyboard(
        previousKeyboardTop: Double,
        targetKeyboardTop: Double,
        duration: Double,
        animationOptions: UIViewAnimationOptions
    ) {
        cancelActiveAnimation()

        val viewBounds = view.bounds
        if (previousKeyboardTop == targetKeyboardTop) {
            val overlap =
                (CGRectGetMaxY(viewBounds) - targetKeyboardTop).coerceIn(
                    0.0,
                    viewBounds.useContents { size.height },
                )
            onKeyboardOverlapHeightChanged(overlap.dp)
            return
        }

        val animationView = UIView()
        view.addSubview(animationView)
        val animation =
            KeyboardAnimation(
                view = animationView,
                previousKeyboardTop = previousKeyboardTop,
                targetKeyboardTop = targetKeyboardTop,
                viewBounds = viewBounds,
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

    private fun KeyboardAnimation.progress(): Double {
        val layer = view.layer.presentationLayer() ?: return 0.0
        return layer.frame.useContents { size.height / ANIMATION_TARGET_SIZE }
    }

    private fun KeyboardAnimation.keyboardOverlapHeight(progress: Double = progress()): Dp {
        val keyboardTop = previousKeyboardTop + (targetKeyboardTop - previousKeyboardTop) * progress
        // Clamp after interpolation so the inset stays zero until the keyboard reaches the view.
        return (CGRectGetMaxY(viewBounds) - keyboardTop)
            .coerceIn(0.0, viewBounds.useContents { size.height })
            .dp
    }

    private fun finishAnimation(animation: KeyboardAnimation) {
        if (activeAnimation !== animation) {
            animation.view.removeFromSuperview()
            return
        }

        val finalOverlapHeight = animation.keyboardOverlapHeight(progress = 1.0)
        onKeyboardOverlapHeightChanged(finalOverlapHeight)
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        animation.view.removeFromSuperview()
    }

    private fun cancelActiveAnimation() {
        val animation = activeAnimation ?: return
        activeAnimation = null
        activitiesHandler.onActivitiesEnded()
        UIView.performWithoutAnimation {
            animation.view.layer.removeAllAnimations()
            animation.view.removeFromSuperview()
        }
    }

    private companion object {
        const val ANIMATION_TARGET_SIZE = 1000.0
        val KEYBOARD_FRAME_AWAIT_TIMEOUT_MILLIS = 500.milliseconds
    }
}
