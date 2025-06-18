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

import androidx.annotation.MainThread

/**
 * Dispatcher that can be used to register [NavigationEventCallback] instances for handling the
 * in-app callbacks via composition.
 */
public class NavigationEventDispatcher(
    private val fallbackOnBackPressed: (() -> Unit)? = null,
    private val onHasEnabledCallbacksChanged: ((Boolean) -> Unit)? = null,
) {
    private val inProgressCallbacks: MutableList<NavigationEventCallback> = mutableListOf()
    /** Callbacks that should be processed with higher priority, before [normalCallbacks]. */
    private val overlayCallbacks = ArrayDeque<NavigationEventCallback>()

    /** Standard or default callbacks for navigation events. */
    private val normalCallbacks = ArrayDeque<NavigationEventCallback>()

    private var hasEnabledCallbacks: Boolean = false

    private var updateInputHandler: () -> Unit = {}

    /**
     * Recomputes and updates the current [hasEnabledCallbacks] state based on the enabled status of
     * all registered callbacks.
     *
     * If the enabled state changes, this method invokes [onHasEnabledCallbacksChanged] (when set)
     * and triggers [updateInputHandler] to update the active callbacks that should participate in
     * navigation handling.
     */
    internal fun updateEnabledCallbacks() {
        val hasEnabledCallbacks = (overlayCallbacks + normalCallbacks).any { it.isEnabled }
        if (hasEnabledCallbacks != this.hasEnabledCallbacks) {
            // Update `hasEnabledCallbacks` before notifying, since callbacks may access it directly
            // and would otherwise see a stale value.
            this.hasEnabledCallbacks = hasEnabledCallbacks

            onHasEnabledCallbacksChanged?.invoke(hasEnabledCallbacks)
            updateInputHandler.invoke()
        }
    }

    internal fun updateInput(update: () -> Unit) {
        updateInputHandler = update
    }

    /**
     * Returns `true` if there is at least one [NavigationEventCallback.isEnabled] callback
     * registered with this dispatcher.
     *
     * @return True if there is at least one enabled callback.
     */
    public fun hasEnabledCallbacks(): Boolean = hasEnabledCallbacks

    /**
     * Add a new [NavigationEventCallback]. Callbacks are invoked in the reverse order in which they
     * are added, so this newly added [NavigationEventCallback] will be the first callback to be
     * called.
     *
     * To remove a callback, use [NavigationEventCallback.remove].
     *
     * The callbacks provided will be invoked on the main thread.
     */
    @Suppress("PairedRegistration") // Callback is removed via `NavigationEventCallback.remove()`
    @MainThread
    public fun addCallback(
        callback: NavigationEventCallback,
        priority: NavigationEventPriority = NavigationEventPriority.Default,
    ) {
        check(callback.dispatcher == null) {
            "Callback '$callback' is already registered with a dispatcher"
        }
        when (priority) {
            NavigationEventPriority.Overlay -> overlayCallbacks.addFirst(callback)
            NavigationEventPriority.Default -> normalCallbacks.addFirst(callback)
        }

        callback.dispatcher = this

        updateEnabledCallbacks()
    }

    internal fun removeCallback(callback: NavigationEventCallback) {
        // If the callback is currently being processed (i.e., it's in `inProgressCallbacks`),
        // it needs to be notified of cancellation and then removed from the in-progress tracking.
        if (callback in inProgressCallbacks) {
            callback.onEventCancelled()
            inProgressCallbacks -= callback
        }

        // Attempt to remove the callback from both overlay and normal callback lists.
        // It's okay if the callback is not present.
        overlayCallbacks.remove(callback)
        normalCallbacks.remove(callback)

        callback.dispatcher = null

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
            // It's important to ensure that any ongoing operations from previous events are
            // properly cancelled before starting new ones to maintain a consistent state.
            dispatchOnCancelled()
        }

        for (callback in getEnabledCallbacks()) {
            // Add callback to `inProgressCallbacks` *before* execution. This ensures `onCancelled`
            // can be called even if the callback removes itself during `onEventStarted`.
            inProgressCallbacks += callback
            callback.onEventStarted(event)

            // If callback does not allow the event to pass through to other callbacks, stop.
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
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        // Do not clear in-progress, as `progressed` is not a terminal event.

        for (callback in callbacks) {
            callback.onEventProgressed(event)

            // If callback does not allow the event to pass through to other callbacks, stop.
            if (!callback.isPassThrough) break
        }
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventCompleted] event with the given event to the
     * proper callbacks
     */
    @MainThread
    public fun dispatchOnCompleted() {
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        inProgressCallbacks.clear() // Clear in-progress, as 'completed' is a terminal event.

        if (callbacks.isEmpty()) {
            fallbackOnBackPressed?.invoke()
        } else {
            for (callback in callbacks) {
                callback.onEventCompleted()

                // If callback does not allow the event to pass through to other callbacks, stop.
                if (!callback.isPassThrough) break
            }
        }
    }

    /**
     * Dispatch an [NavigationEventCallback.onEventCancelled] event with the given event to the
     * proper callbacks
     */
    @MainThread
    public fun dispatchOnCancelled() {
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        inProgressCallbacks.clear() // Clear in-progress, as 'cancelled' is a terminal event.

        for (callback in callbacks) {
            callback.onEventCancelled()

            // If callback does not allow the event to pass through to other callbacks, stop.
            if (!callback.isPassThrough) break
        }
    }

    private fun getEnabledCallbacks(): List<NavigationEventCallback> {
        return (overlayCallbacks + normalCallbacks).filter { callback -> callback.isEnabled }
    }
}
