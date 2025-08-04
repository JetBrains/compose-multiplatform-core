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

package androidx.lifecycle.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Creates a new [LifecycleOwner] with a lifecycle that is a child of the parent composition's
 * lifecycle.
 *
 * This is useful for creating components (e.g., a map view) that need a lifecycle shorter than the
 * host screen. The child lifecycle will never be in a state greater than its parent, and it can be
 * further restricted by the [maxLifecycle] parameter. For example, you can ensure a component's
 * lifecycle never goes beyond [STARTED], even if the parent Fragment is [RESUMED].
 *
 * @param maxLifecycle The maximum [Lifecycle.State] this child lifecycle is allowed to enter.
 *   Defaults to [RESUMED].
 * @param parentLifecycleOwner The [LifecycleOwner] to use as the parent. Defaults to the
 *   [LocalLifecycleOwner].
 * @param content The composable content that will be scoped to the new child lifecycle.
 */
@Composable
public fun LifecycleOwner(
    maxLifecycle: State = RESUMED,
    parentLifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    content: @Composable () -> Unit,
) {
    val childLifecycleOwner = remember(parentLifecycleOwner) { ChildLifecycleOwner() }

    // Pass LifecycleEvents from the parent down to the child.
    DisposableEffect(childLifecycleOwner, parentLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            childLifecycleOwner.handleLifecycleEvent(event)
        }

        parentLifecycleOwner.lifecycle.addObserver(observer)

        onDispose { parentLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ensure that the child lifecycle is capped at the maxLifecycle.
    LaunchedEffect(childLifecycleOwner, maxLifecycle) {
        childLifecycleOwner.maxLifecycleState = maxLifecycle
    }

    // Now install the LifecycleOwner as a composition local.
    CompositionLocalProvider(LocalLifecycleOwner provides childLifecycleOwner, content = content)
}

/**
 * A private [LifecycleOwner] that is controlled by a parent's lifecycle and capped by a maximum
 * state.
 */
private class ChildLifecycleOwner : LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle = lifecycleRegistry

    // Tracks the last known state from the parent lifecycle.
    private var parentLifecycleState: State = State.INITIALIZED

    // The maximum state this lifecycle can enter.
    var maxLifecycleState: State = State.INITIALIZED
        set(value) {
            field = value
            updateLifecycleState()
        }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        parentLifecycleState = event.targetState
        updateLifecycleState()
    }

    private fun updateLifecycleState() {
        // The child's state is capped at the minimum of the parent's state and the max state.
        // For example, if parent is RESUMED and max is STARTED, the child state becomes STARTED.
        lifecycleRegistry.currentState =
            if (parentLifecycleState.ordinal < maxLifecycleState.ordinal) {
                parentLifecycleState
            } else {
                maxLifecycleState
            }
    }
}
