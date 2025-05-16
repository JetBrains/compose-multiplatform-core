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
import androidx.annotation.MainThread
import java.util.function.Consumer

/**
 * Dispatcher that can be used to register [NavigationEventCallback] instances for handling the
 * in-app callbacks via composition.
 */
public class NavigationEventDispatcher(
    private var fallbackOnBackPressed: Runnable?,
    private val onHasEnabledCallbacksChanged: Consumer<Boolean>?
) {
    /**
     * Dispatcher that can be used to register [NavigationEventCallback] instances for handling the
     * in-app callbacks via composition.
     */
    public constructor(fallbackOnBackPressed: Runnable?) : this(fallbackOnBackPressed, null)

    private var inProgressCallbacks: MutableList<NavigationEventCallback> = mutableListOf()

    internal val overlayCallbacks = ArrayDeque<NavigationEventCallback>()
    internal val normalCallbacks = ArrayDeque<NavigationEventCallback>()

    internal var hasEnabledCallbacks: Boolean = false

    private var updateInputHandler: () -> Unit = {}

    internal fun updateEnabledCallbacks() {
        val hadEnabledCallbacks = hasEnabledCallbacks
        val hasEnabledCallbacks = (overlayCallbacks + normalCallbacks).any { it.enabled }
        this.hasEnabledCallbacks = hasEnabledCallbacks
        if (hasEnabledCallbacks != hadEnabledCallbacks) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onHasEnabledCallbacksChanged?.accept(hasEnabledCallbacks)
            }
            updateInputHandler()
        }
    }

    internal fun updateInput(update: () -> Unit) {
        updateInputHandler = update
    }

    /**
     * Add a new [NavigationEventCallback]. Callbacks are invoked in the reverse order in which they
     * are added, so this newly added [NavigationEventCallback] will be the first callback to be
     * called.
     *
     * The callbacks provided will be invoked on the main thread.
     */
    @MainThread
    public fun addCallback(
        callback: NavigationEventCallback,
        priority: NavigationEventPriority = NavigationEventPriority.Default
    ) {
        when (priority) {
            NavigationEventPriority.Overlay -> overlayCallbacks.addFirst(callback)
            NavigationEventPriority.Default -> normalCallbacks.addFirst(callback)
        }
        updateEnabledCallbacks()
        callback.enabledChangedCallback = ::updateEnabledCallbacks
    }

    /** Remove the given [NavigationEventCallback]. */
    @MainThread
    public fun removeCallback(callback: NavigationEventCallback) {
        overlayCallbacks.remove(callback)
        normalCallbacks.remove(callback)
        updateEnabledCallbacks()
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventStarted] event with the given event to the proper
     * callbacks
     *
     * @param event [NavigationEvent] to dispatch to the callbacks.
     */
    @MainThread
    public fun dispatchOnStarted(event: NavigationEvent) {
        if (inProgressCallbacks.isNotEmpty()) {
            dispatchOnCancelled()
        }
        for (callback in getEnabledCallbackSequence()) {
            callback.onEventStarted(event)
            inProgressCallbacks.add(callback)
            if (!callback.isPassThrough) break
        }
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventProgressed] event with the given event to the
     * proper callbacks
     *
     * @param event [NavigationEvent] to dispatch to the callbacks.
     */
    @MainThread
    public fun dispatchOnProgressed(event: NavigationEvent) {
        val callbacks: Sequence<NavigationEventCallback> =
            inProgressCallbacks.asSequence().ifEmpty { getEnabledCallbackSequence() }

        for (callback in callbacks) {
            callback.onEventProgressed(event)
            if (!callback.isPassThrough) break
        }
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventCompleted] event with the given event to the
     * proper callbacks
     */
    @MainThread
    public fun dispatchOnCompleted() {
        val callbacks: Sequence<NavigationEventCallback> =
            inProgressCallbacks.asSequence().ifEmpty { getEnabledCallbackSequence() }
        inProgressCallbacks.clear()

        for (callback in callbacks) {
            callback.onEventCompleted()
            if (!callback.isPassThrough) return
        }

        fallbackOnBackPressed?.run()
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventCancelled] event with the given event to the
     * proper callbacks
     */
    @MainThread
    public fun dispatchOnCancelled() {
        val callbacks: Sequence<NavigationEventCallback> =
            inProgressCallbacks.asSequence().ifEmpty { getEnabledCallbackSequence() }
        inProgressCallbacks.clear()

        for (callback in callbacks) {
            callback.onEventCancelled()
            if (!callback.isPassThrough) break
        }
    }

    internal fun getEnabledCallbackSequence(): Sequence<NavigationEventCallback> {
        return overlayCallbacks.asSequence().filter { it.enabled } +
            normalCallbacks.asSequence().filter { it.enabled }
    }
}
