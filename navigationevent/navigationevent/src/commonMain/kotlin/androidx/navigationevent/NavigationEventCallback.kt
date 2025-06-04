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
 * Call for handling [NavigationEventDispatcher] callbacks.
 *
 * This class maintains its own [isEnabled] state and will only receive callbacks when enabled.
 *
 * @param isEnabled The default enabled state for this callback.
 * @see NavigationEventDispatcher
 */
public abstract class NavigationEventCallback(
    /**
     * The enabled state of the callback. Only when this callback is enabled will it receive
     * callbacks to [onEventCompleted].
     */
    isEnabled: Boolean
) {

    public var isEnabled: Boolean = isEnabled
        set(value) {
            field = value
            enabledChangedCallback?.invoke()
        }

    internal var enabledChangedCallback: (() -> Unit)? = null

    /**
     * Whether this callback should consume the callback from the [NavigationEventDispatcher] or
     * allow it to continue.
     */
    public var isPassThrough: Boolean = false

    /**
     * A set of active [AutoCloseable] resources associated with this [NavigationEventCallback].
     *
     * Each closeable typically represents a registration with a [NavigationEventDispatcher] or
     * another resource that should be properly closed. These are automatically closed when [remove]
     * is called to avoid unintended event dispatches or resource leaks.
     */
    private val closeables = mutableSetOf<AutoCloseable>()

    /**
     * Cleans up all tracked [AutoCloseable] resources associated with this
     * [NavigationEventCallback].
     *
     * This method calls [AutoCloseable.close] on each tracked resource, ensuring that no lingering
     * event registrations or resources remain active.
     */
    public fun remove() {
        // Iterate over a copy of the closeables list to prevent `ConcurrentModificationException`,
        // as closing a closeable might lead to its removal from the original list during iteration.
        for (closeable in closeables.toList()) {
            closeable.close()
        }
        // Don't clear `closeables`; each closeable may remove itself via `removeCloseable`.
    }

    /** Callback for handling the [NavigationEventDispatcher.dispatchOnStarted] callback. */
    public open fun onEventStarted(event: NavigationEvent) {}

    /** Callback for handling the [NavigationEventDispatcher.dispatchOnProgressed] callback. */
    public open fun onEventProgressed(event: NavigationEvent) {}

    /** Callback for handling the [NavigationEventDispatcher.dispatchOnCompleted] callback. */
    public open fun onEventCompleted() {}

    /** Callback for handling the [NavigationEventDispatcher.dispatchOnCancelled] callback. */
    public open fun onEventCancelled() {}

    /**
     * Tracks an [AutoCloseable] resource associated with this [NavigationEventCallback].
     *
     * Tracked resources will be automatically closed when [remove] is called, preventing unintended
     * behavior or resource leaks.
     *
     * @param closeable The [AutoCloseable] to track for later cleanup.
     */
    internal fun addCloseable(closeable: AutoCloseable) {
        closeables += closeable
    }

    /**
     * Removes a specific [AutoCloseable] from the tracked set without closing it.
     *
     * This is useful if the resource has already been closed manually or should no longer be
     * managed by this [NavigationEventCallback].
     *
     * @param closeable The [AutoCloseable] to stop tracking.
     */
    internal fun removeCloseable(closeable: AutoCloseable) {
        closeables -= closeable
    }
}
