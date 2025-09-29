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

package androidx.navigationevent.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventTransitionState

/**
 * A composable that handles navigation events using simple lambda handlers, driven by a manually
 * hoisted [NavigationEventState].
 *
 * This is the core implementation of the navigation event handler. This overload must be used when
 * you need to hoist the [NavigationEventState] (by calling [rememberNavigationEventState] at a
 * higher level). Hoisting is necessary when other composables need to react to the gesture's
 * [NavigationEventTransitionState] (held within the `state` object), for example, to drive custom
 * animations.
 *
 * ## Precedence
 * When multiple [NavigationEventHandler] are present in the composition, the one that is composed
 * *last* among all enabled handlers will be invoked.
 *
 * ## Usage
 * It is important to call this composable **unconditionally**. Use [isBackEnabled] and
 * [isForwardEnabled] to control whether the handler is active. This is preferable to conditionally
 * calling [NavigationEventHandler] (e.g., inside an `if` block), as conditional calls can change
 * the order of composition, leading to unpredictable behavior where different handlers are invoked
 * after recomposition.
 *
 * ## Timing Consideration
 * There are cases where a predictive back or forward gesture may be dispatched within a rendering
 * frame before the corresponding `enabled` flag is updated, which can cause unexpected behavior
 * (see [b/375343407](https://issuetracker.google.com/375343407),
 * [b/384186542](https://issuetracker.google.com/384186542)). For example, if [isBackEnabled] is set
 * to `false`, a back gesture initiated in the same frame may still trigger this handler because the
 * system sees the stale `true` value.
 *
 * @param state The hoisted [NavigationEventState] (returned from [rememberNavigationEventState]) to
 *   be registered. This object links this handler's callbacks to the unique handler instance that
 *   is producing the state.
 * @param isForwardEnabled Controls whether forward navigation gestures are handled.
 * @param onForwardCancelled Called if a forward navigation gesture is cancelled.
 * @param onForwardCompleted Called when a forward navigation gesture completes.
 * @param isBackEnabled Controls whether back navigation gestures are handled.
 * @param onBackCancelled Called if a back navigation gesture is cancelled.
 * @param onBackCompleted Called when a back navigation gesture completes.
 */
@Composable
public fun NavigationEventHandler(
    state: NavigationEventState<*>,
    // ---- Forward Events ----
    isForwardEnabled: Boolean = true,
    onForwardCancelled: () -> Unit = {},
    onForwardCompleted: () -> Unit = {},
    // ---- Back Events ----
    isBackEnabled: Boolean = true,
    onBackCancelled: () -> Unit = {},
    onBackCompleted: () -> Unit = {},
) {
    val dispatcher =
        checkNotNull(LocalNavigationEventDispatcherOwner.current) {
                "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"
            }
            .navigationEventDispatcher

    SideEffect {
        state.handler.isForwardEnabled = isForwardEnabled
        state.handler.currentOnForwardCancelled = onForwardCancelled
        state.handler.currentOnForwardCompleted = onForwardCompleted

        state.handler.isBackEnabled = isBackEnabled
        state.handler.currentOnBackCancelled = onBackCancelled
        state.handler.currentOnBackCompleted = onBackCompleted
    }

    DisposableEffect(state) {
        dispatcher.addHandler(state.handler)
        onDispose { state.handler.remove() }
    }
}

/**
 * A composable that handles only back navigation gestures, driven by a manually hoisted
 * [NavigationEventState].
 *
 * This is a convenience wrapper around the core [NavigationEventHandler] overload for cases where
 * forward navigation is not relevant. Use this overload when hoisting state (e.g., for custom
 * animations).
 *
 * Refer to the primary [NavigationEventHandler] KDoc for details on precedence, unconditional
 * usage, and timing considerations.
 *
 * @param state The hoisted [NavigationEventState] (returned from [rememberNavigationEventState]) to
 *   be registered.
 * @param isBackEnabled Controls whether back navigation gestures are handled.
 * @param onBackCancelled Called if a back navigation gesture is cancelled.
 * @param onBackCompleted Called when a back navigation gesture completes and navigation occurs.
 */
@Composable
public fun NavigationBackHandler(
    state: NavigationEventState<*>,
    isBackEnabled: Boolean = true,
    onBackCancelled: () -> Unit = {},
    onBackCompleted: () -> Unit,
) {
    NavigationEventHandler(
        state = state,
        onForwardCancelled = {},
        onForwardCompleted = {},
        isForwardEnabled = false, // disable forward
        onBackCancelled = onBackCancelled,
        onBackCompleted = onBackCompleted,
        isBackEnabled = isBackEnabled,
    )
}

/**
 * A composable that handles only forward navigation gestures, driven by a manually hoisted
 * [NavigationEventState].
 *
 * This is a convenience wrapper around the core [NavigationEventHandler] overload for cases where
 * back navigation is not relevant. Use this overload when hoisting state.
 *
 * Refer to the primary [NavigationEventHandler] KDoc for details on precedence, unconditional
 * usage, and timing considerations.
 *
 * @param state The hoisted [NavigationEventState] (returned from [rememberNavigationEventState]) to
 *   be registered.
 * @param isForwardEnabled Controls whether forward navigation gestures are handled.
 * @param onForwardCancelled Called if a forward navigation gesture is cancelled.
 * @param onForwardCompleted Called when a forward navigation gesture completes and navigation
 *   occurs.
 */
@Composable
public fun NavigationForwardHandler(
    state: NavigationEventState<*>,
    isForwardEnabled: Boolean = true,
    onForwardCancelled: () -> Unit = {},
    onForwardCompleted: () -> Unit,
) {
    NavigationEventHandler(
        state = state,
        onForwardCancelled = onForwardCancelled,
        onForwardCompleted = onForwardCompleted,
        isForwardEnabled = isForwardEnabled,
        onBackCancelled = {},
        onBackCompleted = {},
        isBackEnabled = false, // disable back
    )
}
