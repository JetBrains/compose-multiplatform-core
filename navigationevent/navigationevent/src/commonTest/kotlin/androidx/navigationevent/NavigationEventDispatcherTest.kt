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

import androidx.kruth.assertThat
import androidx.kruth.assertThrows
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import kotlin.test.Test

class NavigationEventDispatcherTest {
    @Test
    fun cancel_is_sent_while_removing_a_callback_after_navigation_started() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "onEventStarted"
                }

                override fun onEventCancelled() {
                    history += "onEventCancelled"
                }
            }

        dispatcher.addCallback(callback)

        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        history += "before remove()"
        callback.remove()
        history += "after remove()"

        assertThat(history)
            .containsExactly(
                "onEventStarted",
                "before remove()",
                "onEventCancelled",
                "after remove()",
            )
            .inOrder()
    }

    @Test
    fun cancel_is_NOT_sent_while_disabling_a_callback_in_onEventStarted() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "onEventStarted start"
                    this.isEnabled = false
                    history += "callback disabled"
                    history += "onEventStarted finish"
                }

                override fun onEventCompleted() {
                    history += "onEventCompleted"
                }
            }

        dispatcher.addCallback(callback)

        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        dispatcher.dispatchOnCompleted()

        assertThat(history)
            .containsExactly(
                "onEventStarted start",
                "callback disabled",
                "onEventStarted finish",
                // Even though the callback is disabled we still sent events as it's kept in
                // `inProgressCallbacks`.
                "onEventCompleted",
            )
            .inOrder()
    }

    @Test
    fun cancel_is_NOT_sent_while_disabling_a_callback_after_navigation_started() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "onEventStarted"
                }

                override fun onEventCancelled() {
                    history += "onEventCancelled"
                }
            }

        dispatcher.addCallback(callback)

        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        history += "before disable"
        callback.isEnabled = false
        history += "after disable"

        assertThat(history)
            .containsExactly("onEventStarted", "before disable", "after disable")
            .inOrder()
    }

    @Test
    fun cancel_is_sent_while_removing_a_callback_in_onEventStarted() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "onEventStarted start"
                    this.remove()
                    history += "callback removed"
                    history += "onEventStarted finish"
                }

                override fun onEventCancelled() {
                    history += "onEventCancelled"
                }
            }

        dispatcher.addCallback(callback)

        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        assertThat(history)
            .containsExactly(
                "onEventStarted start",
                "onEventCancelled",
                "callback removed",
                "onEventStarted finish",
            )
            .inOrder()
    }

    @Test
    fun cancel_is_sent_after_double_start() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback1 =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback1 onEventStarted"
                }

                override fun onEventProgressed(event: NavigationEvent) {}

                override fun onEventCompleted() {}

                override fun onEventCancelled() {
                    history += "callback1 onEventCancelled"
                }
            }

        dispatcher.addCallback(callback1)

        history += "navigation1 starting"
        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        val callback2 =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback2 onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback2 onEventCompleted"
                }
            }

        dispatcher.addCallback(callback2)

        history += "navigation2 starting without cancelling/completing navigation1"
        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        dispatcher.dispatchOnCompleted()

        assertThat(history)
            .containsExactly(
                "navigation1 starting",
                "callback1 onEventStarted",
                "navigation2 starting without cancelling/completing navigation1",
                "callback1 onEventCancelled",
                "callback2 onEventStarted",
                "callback2 onEventCompleted",
            )
    }

    @Test
    fun callback_added_during_navigation_does_not_receive_events_from_current_navigation() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val callback1 =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback1 onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback1 onEventCompleted"
                }
            }

        dispatcher.addCallback(callback1)
        history += "callback1 added"

        history += "navigation1 starting"
        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        val callback2 =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback2 onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback2 onEventCompleted"
                }
            }
        dispatcher.addCallback(callback2)
        history += "callback2 added"

        dispatcher.dispatchOnCompleted()

        history += "navigation2 starting"
        dispatcher.dispatchOnStarted(NavigationEvent(0.1F, 0.1F, 0.1F, EDGE_LEFT))

        dispatcher.dispatchOnCompleted()

        assertThat(history)
            .containsExactly(
                "callback1 added",
                "navigation1 starting",
                "callback1 onEventStarted",
                "callback2 added",
                "callback1 onEventCompleted",
                "navigation2 starting",
                // callback1 was not called as callback2 was added later and is not a pass-through.
                "callback2 onEventStarted",
                "callback2 onEventCompleted",
            )
            .inOrder()
    }

    @Test
    fun fallback_is_called_when_there_are_no_enabled_callbacks() {
        val history = mutableListOf<String>()
        val dispatcher =
            NavigationEventDispatcher(fallbackOnBackPressed = { history += "fallback called" })

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback onEventCompleted"
                }
            }

        dispatcher.addCallback(callback)

        dispatcher.dispatchOnCompleted()

        callback.isEnabled = false

        dispatcher.dispatchOnCompleted()

        assertThat(history).containsExactly("callback onEventCompleted", "fallback called")
    }

    @Test
    fun fallback_is_not_called_when_all_enabled_callbacks_are_pass_through() {
        val history = mutableListOf<String>()
        val dispatcher =
            NavigationEventDispatcher(fallbackOnBackPressed = { history += "fallback called" })

        val callback1 =
            object : NavigationEventCallback(isEnabled = true, isPassThrough = true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback1 onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback1 onEventCompleted"
                }
            }

        dispatcher.addCallback(callback1)

        val callback2 =
            object : NavigationEventCallback(isEnabled = true, isPassThrough = true) {
                override fun onEventStarted(event: NavigationEvent) {
                    history += "callback2 onEventStarted"
                }

                override fun onEventCompleted() {
                    history += "callback2 onEventCompleted"
                }
            }

        dispatcher.addCallback(callback2)

        dispatcher.dispatchOnCompleted()

        assertThat(history)
            .containsExactly("callback2 onEventCompleted", "callback1 onEventCompleted")
    }

    @Test
    fun overlay_callbacks_gets_called_even_when_added_before_normal_callbacks() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val overlayCallback =
            object : NavigationEventCallback(isEnabled = true) {
                override fun onEventCompleted() {
                    history += "overlayCallback onEventCompleted"
                }
            }
        dispatcher.addCallback(overlayCallback, NavigationEventPriority.Overlay)

        val normalCallback =
            object : NavigationEventCallback(isEnabled = true) {
                override fun onEventCompleted() {
                    history += "normalCallback onEventCompleted"
                }
            }
        dispatcher.addCallback(normalCallback, NavigationEventPriority.Default)

        dispatcher.dispatchOnCompleted()

        assertThat(history).containsExactly("overlayCallback onEventCompleted")
    }

    @Test
    fun overlay_callbacks_pass_through_to_normal_callbacks() {
        val history = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher {}

        val normalCallback =
            object : NavigationEventCallback(isEnabled = true) {
                override fun onEventCompleted() {
                    history += "normalCallback onEventCompleted"
                }
            }
        dispatcher.addCallback(normalCallback, NavigationEventPriority.Default)

        val overlayCallback =
            object : NavigationEventCallback(isEnabled = true, isPassThrough = true) {
                override fun onEventCompleted() {
                    history += "overlayCallback onEventCompleted"
                }
            }
        dispatcher.addCallback(overlayCallback, NavigationEventPriority.Overlay)

        dispatcher.dispatchOnCompleted()

        assertThat(history)
            .containsExactly("overlayCallback onEventCompleted", "normalCallback onEventCompleted")
    }

    @Test
    fun adding_a_callback_to_more_dispatchers_throws_exception() {
        val history = mutableListOf<String>()

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventCompleted() {
                    history += "onEventCompleted"
                }
            }

        val dispatcher1 = NavigationEventDispatcher {}
        dispatcher1.addCallback(callback)

        val dispatcher2 = NavigationEventDispatcher {}
        assertThrows(IllegalStateException::class) { dispatcher2.addCallback(callback) }
            .hasMessageThat()
            .contains("is already registered with a dispatcher")
    }

    @Test
    fun adding_a_callback_to_the_same_dispatcher_twice_throws_exception() {
        val history = mutableListOf<String>()

        val callback =
            object : NavigationEventCallback(true) {
                override fun onEventCompleted() {
                    history += "onEventCompleted"
                }
            }

        val dispatcher = NavigationEventDispatcher {}
        dispatcher.addCallback(callback)
        assertThrows(IllegalStateException::class) { dispatcher.addCallback(callback) }
            .hasMessageThat()
            .contains("is already registered with a dispatcher")
    }
}
