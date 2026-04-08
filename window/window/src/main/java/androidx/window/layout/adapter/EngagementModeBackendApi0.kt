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

package androidx.window.layout.adapter

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.GuardedBy
import androidx.annotation.UiContext
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import androidx.window.layout.WindowLayoutInfo.EngagementMode
import androidx.window.layout.util.CallbackWrapper
import androidx.window.layout.util.EngagementModeHelper
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class EngagementModeBackendApi0
@VisibleForTesting
internal constructor(
    private val engagementModeHelper: EngagementModeHelper,
    private val inputDeviceTracker: InputDeviceTracker = InputDeviceTracker(engagementModeHelper),
) : EngagementModeBackend, InputDeviceTracker.Listener {
    private val lock = ReentrantLock()
    /** List of all registered callbacks for engagement layout mode. */
    @GuardedBy("lock")
    private val engagementChangeCallbacks = mutableListOf<EngagementModeChangeCallbackWrapper>()
    @GuardedBy("lock")
    private val contextRegistrations = mutableMapOf<Context, ConfigChangeCallback>()

    override fun engagementMode(@UiContext context: Context): EngagementMode {
        return calculateEngagementMode(context)
    }

    override fun addEngagementLayoutChangeCallback(
        @UiContext context: Context,
        executor: Executor,
        callback: Consumer<EngagementMode>,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback.accept(EngagementMode.ENGAGEMENT_TOUCH)
            return
        }
        var callbackWrapper: EngagementModeChangeCallbackWrapper
        lock.withLock {
            if (engagementChangeCallbacks.any { it.callback === callback }) {
                // Return early if the same callback is already registered.
                return
            }
            val isNotRegistered = engagementChangeCallbacks.isEmpty()
            callbackWrapper = EngagementModeChangeCallbackWrapper(context, executor, callback)
            engagementChangeCallbacks.add(callbackWrapper)

            val registration =
                contextRegistrations.getOrPut(context) {
                    ConfigChangeCallback().also { context.registerComponentCallbacks(it) }
                }
            registration.count++

            if (isNotRegistered) {
                inputDeviceTracker.addListener(this)
            }
        }
        callbackWrapper.accept(calculateEngagementMode(context))
    }

    override fun removeEngagementLayoutChangeCallback(callback: Consumer<EngagementMode>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        lock.withLock {
            // The same callback may be registered for multiple different window tokens, and
            // vice versa. First collect all items to be removed.
            val itemsToRemove = mutableListOf<EngagementModeChangeCallbackWrapper>()
            for (callbackWrapper in engagementChangeCallbacks) {
                val registeredCallback = callbackWrapper.callback
                if (registeredCallback === callback) {
                    itemsToRemove.add(callbackWrapper)
                }
            }

            for (item in itemsToRemove) {
                val context = item.context
                contextRegistrations[context]?.let { registration ->
                    registration.count--
                    if (registration.count == 0) {
                        context.unregisterComponentCallbacks(registration)
                        contextRegistrations.remove(context)
                    }
                }
            }

            engagementChangeCallbacks.removeAll(itemsToRemove.toSet())
            if (engagementChangeCallbacks.isEmpty()) {
                inputDeviceTracker.removeListener(this)
            }
        }
    }

    override fun onInputDeviceConnectionChanged(isMouseAndKeyboardConnected: Boolean) {
        updateAllCallbacks()
    }

    private inner class ConfigChangeCallback : ComponentCallbacks2 {
        var count = 0

        override fun onConfigurationChanged(newConfig: Configuration) {
            updateAllCallbacks()
        }

        override fun onLowMemory() {}

        override fun onTrimMemory(level: Int) {}
    }

    override fun getCurrentEngagementLayoutMode(@UiContext uiContext: Context): EngagementMode {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                engagementModeHelper.isPointerMode(uiContext)
        ) {
            return EngagementMode.ENGAGEMENT_PRECISE_POINTER
        }
        return EngagementMode.ENGAGEMENT_TOUCH
    }

    private fun updateAllCallbacks() {
        val callbacksToNotify =
            mutableListOf<Pair<EngagementModeChangeCallbackWrapper, EngagementMode>>()
        lock.withLock {
            engagementChangeCallbacks.forEach { wrapper ->
                callbacksToNotify.add(wrapper to calculateEngagementMode(wrapper.context))
            }
        }
        callbacksToNotify.forEach { (wrapper, mode) -> wrapper.accept(mode) }
    }

    private fun calculateEngagementMode(context: Context): EngagementMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return EngagementMode.ENGAGEMENT_TOUCH
        }
        val isConnected = inputDeviceTracker.isMouseAndKeyboardConnected()
        val isLargeEnough = engagementModeHelper.hasLargeEnoughDisplay(context)
        return if (isConnected && isLargeEnough) {
            EngagementMode.ENGAGEMENT_PRECISE_POINTER
        } else {
            EngagementMode.ENGAGEMENT_TOUCH
        }
    }

    /**
     * Wrapper around [Consumer<EngagementMode>] that also includes the [Executor] on which the
     * callback should run and the [Context].
     */
    internal class EngagementModeChangeCallbackWrapper(
        val context: Context,
        executor: Executor,
        val callback: Consumer<EngagementMode>,
    ) : CallbackWrapper<EngagementMode>(executor, callback)

    companion object {
        @Volatile private var globalInstance: EngagementModeBackend? = null
        private val globalLock = ReentrantLock()

        /** Returns the global instance of [EngagementModeBackend]. */
        @JvmStatic
        fun getInstance(context: Context): EngagementModeBackend {
            if (globalInstance == null) {
                globalLock.withLock {
                    if (globalInstance == null) {
                        globalInstance =
                            EngagementModeBackendApi0(EngagementModeHelper.getInstance(context))
                    }
                }
            }
            return globalInstance!!
        }

        @VisibleForTesting
        fun resetInstance() {
            globalInstance = null
        }
    }
}
