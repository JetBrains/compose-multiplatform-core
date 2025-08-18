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

package androidx.xr.glimmer.demos

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.InputDeviceCompat.SOURCE_TOUCH_NAVIGATION

/** Main [Activity] containing all Glimmer related demos. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        // TODO(b/438995221): Remove this line when this flag is turned on by default for all apps.
        @OptIn(ExperimentalComposeUiApi::class)
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        ComposeView(this)
            .also { setContentView(it) }
            .setContent { DemoApp(demoAppState = rememberDemoAppState(Demos)) }
    }

    private var secondaryPointerUpEventTime: Long? = null

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (handleTwoPointerBackTapNavigation(ev)) return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    /**
     * Handles a two-pointer tap to invoke back navigation.
     *
     * @return true if back navigation was invoked, false otherwise
     */
    private fun handleTwoPointerBackTapNavigation(motionEvent: MotionEvent): Boolean {
        if (motionEvent.isFromSource(SOURCE_TOUCH_NAVIGATION)) {
            if (
                motionEvent.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                    motionEvent.pointerCount == 2
            ) {
                // The secondary pointer was released (one is still touching), track event time
                secondaryPointerUpEventTime = motionEvent.eventTime
            }
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                if (secondaryPointerUpEventTime != null) {
                    // Last pointer was released. If we previously released a secondary pointer,
                    // and this pointer was released within a short time of that, invoke back
                    val timeBetweenUpEvents = motionEvent.eventTime - secondaryPointerUpEventTime!!
                    secondaryPointerUpEventTime = null
                    if (timeBetweenUpEvents < 500) {
                        onBackPressedDispatcher.onBackPressed()
                        return true
                    }
                }
            }
        }
        return false
    }
}
