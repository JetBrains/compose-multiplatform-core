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

package androidx.xr.compose.spatial

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.updateLayoutParams
import kotlin.math.roundToInt

/** Handle clicks outside of the parent panel. */
@Composable
internal fun OutsideInputHandler(enabled: Boolean = true, onOutsideInput: () -> Unit) {
    if (enabled) {
        val view = LocalView.current
        val currentOnOutsideInput = rememberUpdatedState(onOutsideInput)
        remember(view) { InputCaptureView(view, currentOnOutsideInput) }
    }
}

/**
 * A View that captures input events and forwards pointer and touch events to a target view.
 *
 * This allows us to detect when touch events happen outside of the bounds of the target view.
 *
 * This default View constructor is used by tooling (as per a warning in Android Studio). In
 * practice, use the constructor that takes a targetView and onOutsideInput.
 */
private class InputCaptureView private constructor(context: Context) :
    View(context), RememberObserver, View.OnLayoutChangeListener {
    constructor(targetView: View, onOutsideInput: State<() -> Unit>) : this(targetView.context) {
        this.targetView = targetView
        this.onOutsideInput = onOutsideInput
    }

    init {
        // Assign the layout parameters for this view. See documentation for more details:
        // https://developer.android.com/reference/android/view/View#setLayoutParams(android.view.ViewGroup.LayoutParams)
        layoutParams =
            WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var targetView: View? = null
        set(value) {
            if (field != value && value != null) {
                field?.removeOnLayoutChangeListener(this)
                value.addOnLayoutChangeListener(this)
                updateLayoutParams<WindowManager.LayoutParams> {
                    // Get the Window token from the parent view
                    token = value.applicationWindowToken

                    // Match the size of the target view.
                    width = value.width
                    height = value.height
                }
                if (isAttachedToWindow) {
                    windowManager.updateViewLayout(this, layoutParams)
                }
            }
            field = value
        }

    private var onOutsideInput: State<() -> Unit>? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || targetView == null) {
            return super.onTouchEvent(event)
        }
        if (isMotionEventOutsideTargetView(event)) {
            onOutsideInput?.value?.invoke()
        } else {
            // This click is inside of the target view bounds, so dispatch it to the target view.
            targetView?.dispatchTouchEvent(event)
        }
        return true
    }

    private fun isMotionEventOutsideTargetView(event: MotionEvent): Boolean {
        val view = this.targetView ?: return true
        // If the action is ACTION_DOWN, we need to check if the touch event is outside of the view
        // bounds.
        if (event.action == MotionEvent.ACTION_DOWN) {
            return event.x.roundToInt() !in view.left..view.right ||
                event.y.roundToInt() !in view.top..view.bottom
        }
        return event.action == MotionEvent.ACTION_OUTSIDE
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        targetView?.dispatchGenericMotionEvent(event)
        return true
    }

    override fun onAbandoned() {
        // Do nothing. Nothing is set up until onRemembered is called.
    }

    override fun onForgotten() {
        targetView?.removeOnLayoutChangeListener(this)
        windowManager.removeView(this)
    }

    override fun onRemembered() {
        windowManager.addView(this, layoutParams)
    }

    /**
     * Update the layout parameters of this view to match the size of the [targetView].
     *
     * This is called when the [targetView] is laid out.
     */
    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        updateLayoutParams<WindowManager.LayoutParams> {
            width = right - left
            height = bottom - top
        }
        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, layoutParams)
        }
    }
}
