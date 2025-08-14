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

package androidx.navigationevent.internal

import androidx.annotation.MainThread
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInputHandler

/**
 * A test implementation of [NavigationEventInputHandler] that records lifecycle events and
 * invocation counts.
 *
 * Use this class in tests to verify that `onAdded`, `onRemoved`, and `onHasEnabledCallbacksChanged`
 * are called correctly. It counts how many times each lifecycle method is invoked and stores a
 * reference to the most recently added dispatcher. It also provides helper methods to simulate
 * dispatching navigation events.
 *
 * @param onAdded An optional lambda to execute when [onAdded] is called.
 * @param onRemoved An optional lambda to execute when [onRemoved] is called.
 * @param onHasEnabledCallbacksChanged An optional lambda to execute when
 *   [onHasEnabledCallbacksChanged] is called.
 */
// TODO(mgalhardo): aosp/3732271 is renaming this to `Input`
internal class TestNavigationEventInput(
    private val onAdded: (dispatcher: NavigationEventDispatcher) -> Unit = {},
    private val onRemoved: () -> Unit = {},
    private val onHasEnabledCallbacksChanged: (hasEnabledCallbacks: Boolean) -> Unit = {},
) : NavigationEventInputHandler() {

    /** The number of times [onAdded] has been invoked. */
    var addedInvocations: Int = 0
        private set

    /** The number of times [onRemoved] has been invoked. */
    var removedInvocations: Int = 0
        private set

    /** The number of times [onHasEnabledCallbacksChanged] has been invoked. */
    var onHasEnabledCallbacksChangedInvocations: Int = 0
        private set

    /**
     * The most recently added [NavigationEventDispatcher].
     *
     * This is set by [onAdded] and cleared to `null` by [onRemoved].
     */
    var currentDispatcher: NavigationEventDispatcher? = null
        private set

    /**
     * Test helper to simulate the start of a navigation event.
     *
     * This directly calls `dispatchOnStarted`, notifying any registered callbacks. Use this to
     * trigger the beginning of a navigation flow in your tests.
     *
     * @param event The [NavigationEvent] to dispatch.
     */
    @MainThread
    fun handleOnStarted(event: NavigationEvent = NavigationEvent()) {
        dispatchOnStarted(event)
    }

    /**
     * Test helper to simulate the progress of a navigation event.
     *
     * This directly calls `dispatchOnProgressed`, notifying any registered callbacks.
     *
     * @param event The [NavigationEvent] to dispatch.
     */
    @MainThread
    fun handleOnProgressed(event: NavigationEvent = NavigationEvent()) {
        dispatchOnProgressed(event)
    }

    /**
     * Test helper to simulate the completion of a navigation event.
     *
     * This directly calls `dispatchOnCompleted`, notifying any registered callbacks.
     */
    @MainThread
    fun handleOnCompleted() {
        dispatchOnCompleted()
    }

    /**
     * Test helper to simulate the cancellation of a navigation event.
     *
     * This directly calls `dispatchOnCancelled`, notifying any registered callbacks.
     */
    @MainThread
    fun handleOnCancelled() {
        dispatchOnCancelled()
    }

    // TODO(mgalhardo): aosp/3732466 is renaming this to `onAdded`
    override fun onAttach(dispatcher: NavigationEventDispatcher) {
        addedInvocations++
        currentDispatcher = dispatcher
        onAdded.invoke(dispatcher)
    }

    // TODO(mgalhardo): aosp/3732466 is renaming this to `onRemoved`
    override fun onDetach() {
        currentDispatcher = null
        removedInvocations++
        onRemoved.invoke()
    }

    override fun onHasEnabledCallbacksChanged(hasEnabledCallbacks: Boolean) {
        onHasEnabledCallbacksChangedInvocations++
        onHasEnabledCallbacksChanged.invoke(hasEnabledCallbacks)
    }
}
