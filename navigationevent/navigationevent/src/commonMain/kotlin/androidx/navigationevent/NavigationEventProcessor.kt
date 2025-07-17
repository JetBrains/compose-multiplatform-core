/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.navigationevent

import androidx.annotation.MainThread
import androidx.navigationevent.NavigationEventPriority.Companion.Default
import androidx.navigationevent.NavigationEventPriority.Companion.Overlay

/**
 * Manages the lifecycle and dispatching of [NavigationEventCallback] instances across all
 * NavigationEventDispatcher instances. This class ensures consistent ordering, state management,
 * and prioritized dispatch for navigation events.
 */
internal class NavigationEventProcessor {

    /**
     * Stores high-priority callbacks that should be evaluated before default callbacks.
     *
     * `ArrayDeque` is used for efficient `addFirst()` and `remove()` operations, which is ideal for
     * maintaining a Last-In, First-Out (LIFO) dispatch order. This means the most recently added
     * overlay callback is the first to be checked.
     *
     * @see [defaultCallbacks]
     * @see [inProgressCallbacks]
     */
    private val overlayCallbacks = ArrayDeque<NavigationEventCallback>()

    /**
     * Stores standard-priority callbacks.
     *
     * Like `overlayCallbacks`, this uses `ArrayDeque` to efficiently manage a LIFO queue, ensuring
     * the most recently added default callback is checked first within its priority level.
     *
     * @see [overlayCallbacks]
     * @see [inProgressCallbacks]
     */
    private val defaultCallbacks = ArrayDeque<NavigationEventCallback>()

    /**
     * A list of callbacks for a navigation event that is currently in progress.
     *
     * Callbacks in this list have the highest dispatch priority, ensuring that terminal events
     * (like [dispatchOnCompleted] or [dispatchOnCancelled]) are delivered only to the participants
     * of the active navigation. The list is cleared after the event is terminated.
     *
     * Notably, if a callback is removed while in this list, it is implicitly treated as a terminal
     * event and receives an [dispatchOnCancelled] call before being removed.
     *
     * @see [overlayCallbacks]
     * @see [defaultCallbacks]
     */
    private val inProgressCallbacks = mutableListOf<NavigationEventCallback>()

    /**
     * Tracks listeners for changes in the overall enabled state of callbacks across all
     * dispatchers. This allows individual `NavigationEventDispatcher` instances to react when the
     * global state changes.
     *
     * TODO: We currently assume that each child dispatcher registers only one callback (via the
     *   constructor property [NavigationEventDispatcher.onHasEnabledCallbacksChanged]). This allows
     *   us to safely remove that callback when the dispatcher is disposed, preventing memory leaks.
     *   However, this assumption is fragile. If [addOnHasEnabledCallbacksChangedCallback] is ever
     *   called multiple times for the same dispatcher, it *will* result in memory leaks. We need a
     *   more robust mechanism to reliably track and remove *all* callbacks associated with a given
     *   child [NavigationEventDispatcher].
     */
    private val onHasEnabledCallbacksChangedCallbacks = mutableListOf<((Boolean) -> Unit)>()

    /**
     * Represents whether there is at least one enabled callback registered across all dispatchers.
     *
     * This property is updated automatically when callbacks are added, removed, or their enabled
     * state changes. Listeners registered via [addOnHasEnabledCallbacksChangedCallback] are
     * notified of changes to this state.
     */
    private var hasEnabledCallbacks: Boolean = false
        set(value) {
            // Only proceed if the enabled state is actually changing to avoid redundant work.
            if (field == value) return

            field = value
            for (callback in onHasEnabledCallbacksChangedCallbacks) {
                callback.invoke(value)
            }
        }

    /**
     * Recomputes and updates the current [hasEnabledCallbacks] state based on the enabled status of
     * all registered callbacks. This method should be called whenever a callback's enabled state or
     * its registration status (added/removed) changes.
     */
    fun updateEnabledCallbacks() {
        // `any` and `||` are efficient as they short-circuit on the first `true` result.
        hasEnabledCallbacks =
            overlayCallbacks.any { it.isEnabled } || defaultCallbacks.any { it.isEnabled }
    }

    /**
     * Adds a callback that will be notified when the overall enabled state of registered callbacks
     * changes.
     *
     * @param callback The callback to invoke when the enabled state changes.
     */
    fun addOnHasEnabledCallbacksChangedCallback(callback: (Boolean) -> Unit) {
        onHasEnabledCallbacksChangedCallbacks += callback
    }

    /**
     * Removes a callback previously added with [addOnHasEnabledCallbacksChangedCallback].
     *
     * @param callback The callback to remove.
     */
    fun removeOnHasEnabledCallbacksChangedCallback(callback: (Boolean) -> Unit) {
        onHasEnabledCallbacksChangedCallbacks -= callback
    }

    /**
     * Returns `true` if there is at least one [NavigationEventCallback.isEnabled] callback
     * registered globally within this processor.
     *
     * @return `true` if any callback is enabled, `false` otherwise.
     */
    fun hasEnabledCallbacks(): Boolean = hasEnabledCallbacks

    /**
     * Checks if there are any registered callbacks, either overlay or normal.
     *
     * @return `true` if there is at least one overlay callback or one normal callback registered,
     *   `false` otherwise.
     */
    fun hasCallbacks(): Boolean = overlayCallbacks.isNotEmpty() || defaultCallbacks.isNotEmpty()

    /**
     * Adds a new [NavigationEventCallback] to receive navigation events, associating it with its
     * [NavigationEventDispatcher].
     *
     * Callbacks are placed into priority-specific queues ([Overlay] or [Default]) and within those
     * queues, they are ordered in Last-In, First-Out (LIFO) manner. This ensures that the most
     * recently added callback of a given priority is considered first.
     *
     * All callbacks are invoked on the main thread. To stop receiving events, a callback must be
     * removed via [NavigationEventCallback.remove].
     *
     * @param dispatcher The [NavigationEventDispatcher] instance registering this callback. This
     *   link is stored on the callback itself to enable self-removal and state tracking.
     * @param callback The callback instance to be added.
     * @param priority The priority of the callback, determining its invocation order relative to
     *   others. See [NavigationEventPriority].
     * @throws IllegalArgumentException if the given callback is already registered with a different
     *   dispatcher.
     */
    @Suppress("PairedRegistration") // Callback is removed via `NavigationEventCallback.remove()`
    @MainThread
    fun addCallback(
        dispatcher: NavigationEventDispatcher,
        callback: NavigationEventCallback,
        priority: NavigationEventPriority = Default,
    ) {
        // Enforce that a callback is not already registered with another dispatcher.
        require(callback.dispatcher == null) {
            "Callback '$callback' is already registered with a dispatcher"
        }

        // Add to the front of the appropriate queue to achieve LIFO ordering.
        when (priority) {
            Overlay -> overlayCallbacks.addFirst(callback)
            Default -> defaultCallbacks.addFirst(callback)
        }

        // Store the dispatcher reference on the callback for self-management and internal tracking.
        callback.dispatcher = dispatcher
        updateEnabledCallbacks()
    }

    /**
     * Removes a [NavigationEventCallback] from the processor's registry.
     *
     * If the callback is currently part of an active event (i.e., in `inProgressCallbacks`), it
     * will be notified of cancellation before being removed. This method is idempotent and can be
     * called safely even if the callback is not currently registered.
     *
     * @param callback The [NavigationEventCallback] to remove.
     */
    @MainThread
    fun removeCallback(callback: NavigationEventCallback) {
        // If the callback is currently being processed (i.e., it's in `inProgressCallbacks`),
        // it needs to be notified of cancellation and then removed from the in-progress tracking.
        if (callback in inProgressCallbacks) {
            callback.onEventCancelled()
            inProgressCallbacks -= callback
        }

        // The `remove()` operation on ArrayDeque is efficient and simply returns `false` if the
        // element is not found. There's no need for a preceding `contains()` check.
        overlayCallbacks.remove(callback)
        defaultCallbacks.remove(callback)

        // Clear the dispatcher reference to mark the callback as unregistered and available for
        // re-registration.
        callback.dispatcher = null
        updateEnabledCallbacks()
    }

    /**
     * Dispatches an [NavigationEventCallback.onEventStarted] event with the given event to the
     * appropriate callbacks.
     *
     * If an event is currently in progress, it will be cancelled first to ensure a clean state for
     * the new event. Only enabled callbacks are notified.
     *
     * @param event [NavigationEvent] to dispatch to the callbacks.
     */
    @MainThread
    fun dispatchOnStarted(event: NavigationEvent) {
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
        }
    }

    /**
     * Dispatches an [NavigationEventCallback.onEventProgressed] event with the given event to the
     * appropriate callbacks.
     *
     * If there are callbacks currently in progress (from a [dispatchOnStarted] call), only those
     * will be notified. Otherwise, all currently enabled callbacks will receive the progress event.
     * This is not a terminal event, so `inProgressCallbacks` are not cleared.
     *
     * @param event [NavigationEvent] to dispatch to the callbacks.
     */
    @MainThread
    fun dispatchOnProgressed(event: NavigationEvent) {
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        // Progressed is not a terminal event, so `inProgressCallbacks` is not cleared.

        for (callback in callbacks) {
            callback.onEventProgressed(event)
        }
    }

    /**
     * Dispatches an [NavigationEventCallback.onEventCompleted] event to the appropriate callbacks.
     *
     * If there are callbacks currently in progress, only those will be notified. Otherwise, all
     * currently enabled callbacks will receive the completion event. This is a terminal event,
     * clearing `inProgressCallbacks`. If no callbacks handle the event, the `fallbackOnBackPressed`
     * action is invoked.
     *
     * @param fallbackOnBackPressed The action to invoke if no callbacks handle the completion.
     */
    @MainThread
    fun dispatchOnCompleted(fallbackOnBackPressed: (() -> Unit)?) {
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        inProgressCallbacks.clear() // Clear in-progress, as 'completed' is a terminal event.

        // If no callbacks are notified (either none were in progress or enabled), use fallback.
        if (callbacks.isEmpty()) {
            fallbackOnBackPressed?.invoke()
        } else {
            for (callback in callbacks) {
                callback.onEventCompleted()
            }
        }
    }

    /**
     * Dispatches an [NavigationEventCallback.onEventCancelled] event to the appropriate callbacks.
     *
     * If there are callbacks currently in progress, only those will be notified. Otherwise, all
     * currently enabled callbacks will receive the cancellation event. This is a terminal event,
     * clearing `inProgressCallbacks`.
     */
    @MainThread
    fun dispatchOnCancelled() {
        // If there is callbacks in progress, only those are notified.
        // Otherwise, all enabled callbacks are notified.
        val callbacks = inProgressCallbacks.toList().ifEmpty { getEnabledCallbacks() }
        inProgressCallbacks.clear() // Clear in-progress, as 'cancelled' is a terminal event.

        for (callback in callbacks) {
            callback.onEventCancelled()
        }
    }

    /**
     * Builds the prioritized list of callbacks for event dispatch.
     *
     * Callbacks are added in a strict priority order: [overlayCallbacks] first, then
     * [defaultCallbacks]. The process stops if a callback has
     * [NavigationEventCallback.isPassThrough] is `false`, allowing it to "consume" the event and
     * prevent further propagation.
     *
     * **Performance Considerations:** This method avoids unnecessary allocations by iterating
     * directly over the source collections. The early exit on a non-pass-through callback ensures
     * that only the relevant callbacks are included in the final result.
     *
     * @return The list of callbacks to dispatch to, truncated at the first consuming callback.
     */
    fun getEnabledCallbacks(): List<NavigationEventCallback> {
        val callbacksForDispatching = mutableListOf<NavigationEventCallback>()

        // Process higher-priority overlay callbacks first.
        for (callback in overlayCallbacks) {
            if (callback.isEnabled) {
                callbacksForDispatching += callback
                // This callback consumes the event, so we stop here.
                if (!callback.isPassThrough) {
                    return callbacksForDispatching
                }
            }
        }

        // Then, process default priority callbacks.
        for (callback in defaultCallbacks) {
            if (callback.isEnabled) {
                callbacksForDispatching += callback
                if (!callback.isPassThrough) {
                    return callbacksForDispatching
                }
            }
        }

        return callbacksForDispatching
    }
}
