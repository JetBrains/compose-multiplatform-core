/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformValueStorage.Key

/**
 * Provides access to platform-scoped values exposed by the current host container.
 *
 * On platforms backed by views, [get] and [set] should map to the current host view's value
 * store, while [findInNearestAncestor] should eventually traverse parent hosts the same way Android
 * traverses parent views for host-provided data. The expected native backing differs by platform:
 * Swing should use `JComponent.getClientProperty` / `putClientProperty`, UIKit and AppKit should
 * use ObjC associated objects, and the web host should attach values to the DOM root via a
 * stable JS-side store such as a `WeakMap`.
 */
@InternalComposeUiApi
interface PlatformValueStorage {
    /**
     * Returns the value stored for [key] on the current host only.
     */
    operator fun <T : Any> get(key: Key<T>): T?

    /**
     * Stores [value] for [key] on the current host only.
     *
     * Passing `null` clears the current host value.
     */
    operator fun <T : Any> set(key: Key<T>, value: T?)

    /**
     * Returns the value stored for [key] on a parent host, or `null` when no parent provides it.
     */
    fun <T : Any> findInNearestAncestor(key: Key<T>): T?

    /**
     * A typed key for a value stored in [PlatformValueStorage].
     */
    @InternalComposeUiApi
    class Key<T : Any>(val name: String)

    @InternalComposeUiApi
    class MapValueStorage(
        private val parent: PlatformValueStorage? = null
    ) : PlatformValueStorage {
        // TODO: Keep this as an offscreen/test fallback only. Real platform hosts should back
        // PlatformValueStorage with their native value store and parent traversal mechanism.
        private val map = mutableMapOf<Key<*>, Any>()

        @Suppress("UNCHECKED_CAST")
        override operator fun <T : Any> get(key: Key<T>): T? = map[key] as T?

        override operator fun <T : Any> set(key: Key<T>, value: T?) {
            if (value != null) {
                map[key] = value
            } else {
                map.remove(key)
            }
        }

        override fun <T : Any> findInNearestAncestor(key: Key<T>): T? =
            parent?.get(key) ?: parent?.findInNearestAncestor(key)
    }
}

private val CompositionContextKey =
    Key<CompositionContext>("CompositionContext")

@InternalComposeUiApi
var PlatformValueStorage.compositionContext: CompositionContext?
    get() = get(CompositionContextKey)
    set(value) = set(CompositionContextKey, value)

@InternalComposeUiApi
fun PlatformValueStorage.findCompositionContextInNearestAncestor(): CompositionContext? =
    findInNearestAncestor(CompositionContextKey)
