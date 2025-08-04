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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInputHandler

/**
 * Creates a new navigation scope by providing a [NavigationEventDispatcher] to descendant
 * composables.
 *
 * This composable creates a dispatcher that links to any parent dispatcher found in the
 * composition, forming a parent-child relationship. If no parent exists, it automatically becomes a
 * new root dispatcher, this is the top-most parent in a hierarchy. This is useful for isolating
 * navigation handling within specific UI sections, such as a self-contained feature screen or tab.
 *
 * The dispatcher's lifecycle is automatically managed. It is created only once and automatically
 * disposed of when the composable leaves the composition, preventing memory leaks.
 *
 * When used to create a root dispatcher, you must use a [NavigationEventInputHandler] to send it
 * events. Otherwise, the dispatcher will be detached and will not receive events.
 *
 * @param enabled A lambda to dynamically control if the dispatcher is active. When `false`, this
 *   dispatcher and any of its children will ignore navigation events. Defaults to `true`.
 * @param parentNavigationEventDispatcherOwner The parent owner to link to. Defaults to the owner
 *   found in the current composition (`LocalNavigationEventDispatcherOwner`).
 * @param content The child composable content that will receive the new dispatcher.
 */
@Composable
public fun NavigationEventDispatcherOwner(
    enabled: () -> Boolean = { true },
    parentNavigationEventDispatcherOwner: NavigationEventDispatcherOwner? =
        LocalNavigationEventDispatcherOwner.current,
    content: @Composable () -> Unit,
) {
    val localDispatcher = remember {
        // If a parent dispatcher exists, link to it. Otherwise, create a new root dispatcher.
        parentNavigationEventDispatcherOwner?.navigationEventDispatcher
            ?: NavigationEventDispatcher()
    }

    // Use LaunchedEffect to sync the enabled state only when it changes.
    val isEnabled = enabled()
    LaunchedEffect(isEnabled) { localDispatcher.isEnabled = isEnabled }

    // Clean up the dispatcher on dispose to prevent memory leaks.
    DisposableEffect(Unit) { onDispose { localDispatcher.dispose() } }

    // Provide this child dispatcher to all descendant.
    val localOwner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = localDispatcher
        }
    }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides localOwner,
        content = content,
    )
}
