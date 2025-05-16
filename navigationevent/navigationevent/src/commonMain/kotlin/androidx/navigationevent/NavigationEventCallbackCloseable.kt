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

/**
 * A closeable link between a [NavigationEventDispatcher] and a [NavigationEventCallback].
 *
 * This class encapsulates the logic for unregistering a callback from its dispatcher. It enables
 * [NavigationEventCallback]s to be automatically unsubscribed via [AutoCloseable] semantics,
 * typically as part of [NavigationEventCallback.remove].
 *
 * Equality and hash code are manually implemented based on [dispatcher] and [callback], allowing
 * this class to be used reliably in collections (e.g., sets) for tracking and removal of specific
 * callback registrations.
 */
internal class NavigationEventCallbackCloseable(
    private val dispatcher: NavigationEventDispatcher,
    private val callback: NavigationEventCallback,
) : AutoCloseable {

    /**
     * Unregisters the [NavigationEventCallback] from the linked [NavigationEventDispatcher].
     *
     * This stops further event dispatches to the callback and updates the internal dispatcher
     * state.
     */
    override fun close() {
        // Attempt to remove the callback from both overlay and normal callback lists.
        // It's okay if the callback is not present in one or both.
        dispatcher.overlayCallbacks -= callback
        dispatcher.normalCallbacks -= callback

        // If the callback is currently being processed (i.e., it's in `inProgressCallbacks`),
        // it needs to be notified of cancellation and then removed from the in-progress tracking.
        if (callback in dispatcher.inProgressCallbacks) {
            callback.onEventCancelled()
            dispatcher.inProgressCallbacks -= callback
        }

        // Remove this closeable reference from the callback to break dispatcher–callback link.
        callback.removeCloseable(closeable = this)

        // After removing a callback, the list of enabled callbacks might have changed.
        // This call ensures the `enabledCallbacks` list is updated and any dependent logic
        // (like input handlers or listeners for enabled state changes) is also refreshed.
        callback.enabledChangedCallback?.invoke()
        callback.enabledChangedCallback = null
    }

    /**
     * Determines equality based on the [dispatcher] and [callback] references.
     *
     * This allows instances of [NavigationEventCallbackCloseable] to be compared for structural
     * equivalence, which is useful when checking whether a registration already exists.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is NavigationEventCallbackCloseable) return false
        if (dispatcher != other.dispatcher) return false
        if (callback != other.callback) return false

        return true
    }

    /**
     * Computes a hash code based on [dispatcher] and [callback], ensuring compatibility with
     * [equals].
     */
    override fun hashCode(): Int {
        var result = dispatcher.hashCode()
        result = 31 * result + callback.hashCode()
        return result
    }
}
