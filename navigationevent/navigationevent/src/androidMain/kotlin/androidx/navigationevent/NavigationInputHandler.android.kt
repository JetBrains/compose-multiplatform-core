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

package androidx.navigationevent

import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi

/**
 * Provides input to the given [NavigationEventDispatcher].
 *
 * TODO(mgalhardo): Consider moving this to `commonMain` once the design of `InputHandler` for other
 *   platforms is better understood.
 */
public class NavigationInputHandler(private val dispatcher: NavigationEventDispatcher) {
    private var invoker: OnBackInvokedDispatcher? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null
    private var backInvokedCallbackRegistered = false

    /**
     * Sets the [OnBackInvokedDispatcher] for handling system back for Android SDK T+.
     *
     * @param invoker the [OnBackInvokedDispatcher] to be set on this dispatcher
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public fun setOnBackInvokedDispatcher(invoker: OnBackInvokedDispatcher) {
        this.invoker = invoker
        updateBackInvokedCallbackState()
    }

    init {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedCallback =
                if (Build.VERSION.SDK_INT >= 34) {
                    Api34Impl.createOnBackAnimationCallback(
                        { navEvent -> dispatcher.dispatchOnStarted(navEvent) },
                        { navEvent -> dispatcher.dispatchOnProgressed(navEvent) },
                        { dispatcher.dispatchOnCompleted() },
                        { dispatcher.dispatchOnCancelled() },
                    )
                } else {
                    Api33Impl.createOnBackInvokedCallback { dispatcher.dispatchOnCompleted() }
                }
            dispatcher.updateInput { updateBackInvokedCallbackState() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun updateBackInvokedCallbackState() {
        val shouldBeRegistered = dispatcher.hasEnabledCallbacks()
        val dispatcher = invoker
        val onBackInvokedCallback = onBackInvokedCallback
        if (dispatcher != null && onBackInvokedCallback != null) {
            if (shouldBeRegistered && !backInvokedCallbackRegistered) {
                Api33Impl.registerOnBackInvokedCallback(
                    dispatcher,
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    onBackInvokedCallback,
                )
                backInvokedCallbackRegistered = true
            } else if (!shouldBeRegistered && backInvokedCallbackRegistered) {
                Api33Impl.unregisterOnBackInvokedCallback(dispatcher, onBackInvokedCallback)
                backInvokedCallbackRegistered = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal object Api33Impl {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun registerOnBackInvokedCallback(dispatcher: Any, priority: Int, callback: Any) {
            val onBackInvokedDispatcher = dispatcher as OnBackInvokedDispatcher
            val onBackInvokedCallback = callback as OnBackInvokedCallback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(priority, onBackInvokedCallback)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun unregisterOnBackInvokedCallback(dispatcher: Any, callback: Any) {
            val onBackInvokedDispatcher = dispatcher as OnBackInvokedDispatcher
            val onBackInvokedCallback = callback as OnBackInvokedCallback
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback)
        }

        fun createOnBackInvokedCallback(onBackInvoked: () -> Unit): OnBackInvokedCallback {
            return OnBackInvokedCallback { onBackInvoked() }
        }
    }

    @RequiresApi(34)
    internal object Api34Impl {
        fun createOnBackAnimationCallback(
            onBackStarted: (event: NavigationEvent) -> Unit,
            onBackProgressed: (event: NavigationEvent) -> Unit,
            onBackInvoked: () -> Unit,
            onBackCancelled: () -> Unit,
        ): OnBackInvokedCallback {
            return object : OnBackAnimationCallback {
                override fun onBackStarted(backEvent: BackEvent) {
                    onBackStarted(NavigationEvent(backEvent))
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    onBackProgressed(NavigationEvent(backEvent))
                }

                override fun onBackInvoked() {
                    onBackInvoked()
                }

                override fun onBackCancelled() {
                    onBackCancelled()
                }
            }
        }
    }
}
