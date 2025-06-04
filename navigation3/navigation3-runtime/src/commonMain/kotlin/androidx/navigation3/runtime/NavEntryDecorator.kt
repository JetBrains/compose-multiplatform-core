/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.navigation3.runtime

import androidx.compose.runtime.Composable
import kotlin.jvm.JvmSuppressWildcards

/** Marker class to hold the onPop and decorator functions that will be invoked at runtime. */
public class NavEntryDecorator<T : Any>
internal constructor(
    internal val onPop: (key: Any) -> Unit,
    internal val navEntryDecorator: @Composable (entry: NavEntry<T>) -> Unit,
)

/**
 * Function to provide information to all the [NavEntry] that are integrated with a
 * [DecoratedNavEntryProvider].
 *
 * @param onPop a callback that provides the key of a [NavEntry] that has been popped from the
 *   backStack and is leaving composition. This optional callback should to be used to clean up
 *   states that were used to decorate the NavEntry3
 * @param decorator the composable function to provide information to a [NavEntry] [decorator]. Note
 *   that this function only gets invoked for NavEntries that are actually getting rendered (i.e. by
 *   invoking the [NavEntry.content].)
 */
public fun <T : Any> navEntryDecorator(
    onPop: (key: Any) -> Unit = {},
    decorator: @Composable (entry: NavEntry<T>) -> Unit,
): NavEntryDecorator<T> = NavEntryDecorator(onPop, decorator)

/**
 * Wraps a [NavEntry] with the list of [NavEntryDecorator] in the order that the decorators were
 * added to the list and invokes the content of the wrapped entry.
 */
@Composable
public fun <T : Any> DecorateNavEntry(
    entry: NavEntry<T>,
    entryDecorators: List<@JvmSuppressWildcards NavEntryDecorator<*>>,
) {
    @Suppress("UNCHECKED_CAST")
    (entryDecorators as List<@JvmSuppressWildcards NavEntryDecorator<T>>)
        .distinct()
        .foldRight(initial = entry) { decorator, wrappedEntry ->
            object : NavEntryWrapper<T>(wrappedEntry) {
                override val content: @Composable ((T) -> Unit) = {
                    decorator.navEntryDecorator(wrappedEntry)
                }
            }
        }
        .content
        .invoke(entry.key)
}
