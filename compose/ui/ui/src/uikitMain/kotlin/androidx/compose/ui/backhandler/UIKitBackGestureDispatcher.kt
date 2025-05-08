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

package androidx.compose.ui.backhandler

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.uikit.utils.CMPScreenEdgePanGestureRecognizer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.asDpOffset
import androidx.compose.ui.unit.asDpRect
import androidx.compose.ui.unit.toOffset
import kotlin.math.abs
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIRectEdgeRight
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private const val BACK_GESTURE_SCREEN_SIZE = 0.3
private const val BACK_GESTURE_VELOCITY = 100

@OptIn(ExperimentalComposeUiApi::class)
internal class UIKitBackGestureDispatcher(
    private val enableBackGesture: Boolean,
    density: Density,
    getTopLeftOffsetInWindow: () -> IntOffset
) : BackGestureDispatcher() {
    private val iosGestureHandler = UiKitScreenEdgePanGestureHandler(
        density = density,
        getTopLeftOffsetInWindow = getTopLeftOffsetInWindow,
        getListener = { activeListener }
    )

    private val leftEdgePanGestureRecognizer = UIKitBackGestureRecognizer(
        target = iosGestureHandler,
        action = NSSelectorFromString(UiKitScreenEdgePanGestureHandler::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeLeft
        enabled = activeListener != null
    }

    private val rightEdgePanGestureRecognizer = UIKitBackGestureRecognizer(
        target = iosGestureHandler,
        action = NSSelectorFromString(UiKitScreenEdgePanGestureHandler::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeRight
        enabled = activeListener != null
    }

    override fun activeListenerChanged() {
        val listener = activeListener
        leftEdgePanGestureRecognizer.enabled = listener != null
        rightEdgePanGestureRecognizer.enabled = listener != null
    }

    private val activeGestureStates = listOf(
        UIGestureRecognizerStateBegan,
        UIGestureRecognizerStateChanged
    )

    val isBackGestureActive: Boolean
        get() =
            leftEdgePanGestureRecognizer.state in activeGestureStates ||
                rightEdgePanGestureRecognizer.state in activeGestureStates

    fun onDidMoveToWindow(window: UIWindow?, composeRootView: UIView) {
        if (enableBackGesture) {
            removeGestureListeners()
            if (window != null) {
                var view: UIView = composeRootView
                while (view.superview != window) {
                    view = requireNotNull(view.superview) {
                        "Window is not null, but superview is null for ${view.debugDescription}"
                    }
                }
                addGestureListeners(view)
            }
        }
    }

    private fun addGestureListeners(view: UIView) {
        view.addGestureRecognizer(leftEdgePanGestureRecognizer)
        view.addGestureRecognizer(rightEdgePanGestureRecognizer)
    }

    private fun removeGestureListeners() {
        leftEdgePanGestureRecognizer.view?.removeGestureRecognizer(leftEdgePanGestureRecognizer)
        rightEdgePanGestureRecognizer.view?.removeGestureRecognizer(rightEdgePanGestureRecognizer)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        return handleBackKeyEvent(event, activeListener)
    }
}

@OptIn(BetaInteropApi::class, ExperimentalComposeUiApi::class)
private class UiKitScreenEdgePanGestureHandler(
    private val density: Density,
    private val getTopLeftOffsetInWindow: () -> IntOffset,
    private val getListener: () -> BackGestureListener?
) : NSObject() {
    private var listener: BackGestureListener? = null

    @ObjCAction
    fun handleEdgePan(recognizer: UIScreenEdgePanGestureRecognizer) {
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                listener = getListener()
                listener?.onStarted()
            }

            UIGestureRecognizerStateChanged -> {
                val touch = recognizer.locationOfTouch(0u, view).asDpOffset()
                val eventOffset = touch.toOffset(density) - getTopLeftOffsetInWindow().toOffset()
                val event = backEventCompat(
                    eventOffset = eventOffset,
                    leftEdge = recognizer.edges == UIRectEdgeLeft,
                    touch = touch,
                    bounds = view.bounds.asDpRect()
                )

                listener?.onProgressed(event)
            }

            UIGestureRecognizerStateEnded -> {
                val translation = recognizer.translationInView(view = view)
                val velocity = recognizer.velocityInView(view)
                velocity.useContents velocity@{
                    translation.useContents {
                        view.bounds.useContents {
                            val edge = recognizer.edges
                            val velX =
                                if (edge == UIRectEdgeLeft) this@velocity.x else -this@velocity.x
                            when {
                                //if movement is fast in the right direction
                                velX > BACK_GESTURE_VELOCITY -> listener?.onCompleted()
                                //if movement is backward
                                velX < -10 -> listener?.onCancelled()
                                //if there is no movement, or the movement is slow,
                                //but the touch is already more than BACK_GESTURE_SCREEN_SIZE
                                abs(x) >= size.width * BACK_GESTURE_SCREEN_SIZE -> listener?.onCompleted()
                                else -> listener?.onCancelled()
                            }
                        }
                    }
                }
                listener = null
            }

            UIGestureRecognizerStateCancelled -> {
                listener?.onCancelled()
                listener = null
            }

            UIGestureRecognizerStateFailed -> {
                listener?.onCancelled()
                listener = null
            }
        }
    }
}

/**
 * A special gesture recognizer that can cancel touches in the Compose scene.
 * See [androidx.compose.ui.window.TouchesGestureRecognizer.canBePreventedByGestureRecognizer]
 */
internal class UIKitBackGestureRecognizer(
    target: Any?, action: CPointer<out CPointed>?
) : CMPScreenEdgePanGestureRecognizer(target = target, action = action) {
    init {
        setDelaysTouchesBegan(true)
        setDelaysTouchesEnded(true)
        setCancelsTouchesInView(true)
    }

    override fun canBePreventedByGestureRecognizer(
        preventingGestureRecognizer: UIGestureRecognizer
    ): Boolean {
        return false
    }

    override fun canPreventGestureRecognizer(
        preventedGestureRecognizer: UIGestureRecognizer
    ): Boolean {
        return true
    }
}
