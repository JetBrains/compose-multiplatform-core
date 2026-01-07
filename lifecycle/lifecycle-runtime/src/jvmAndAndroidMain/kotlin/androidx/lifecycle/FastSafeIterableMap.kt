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

package androidx.lifecycle

@Suppress("RestrictedApi")
internal actual class FastSafeIterableMap<K : Any, V : Any> {

    private val delegate = androidx.arch.core.internal.FastSafeIterableMap<K, V>()

    actual fun contains(key: K): Boolean {
        return delegate.contains(key)
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        return delegate.putIfAbsent(key, value)
    }

    actual fun remove(key: K): V? {
        return delegate.remove(key)
    }

    actual fun ceil(key: K): Map.Entry<K, V>? {
        return delegate.ceil(key)
    }

    actual fun first(): Map.Entry<K, V> {
        return requireNotNull(delegate.eldest())
    }

    actual fun last(): Map.Entry<K, V> {
        return requireNotNull(delegate.newest())
    }

    actual fun lastOrNull(): Map.Entry<K, V>? {
        return delegate.newest()
    }

    actual fun size(): Int {
        return delegate.size()
    }

    actual fun forEachWithAdditions(action: (Map.Entry<K, V>) -> Unit) {
        delegate.iteratorWithAdditions().forEach(action)
    }

    actual fun forEachReversed(action: (Map.Entry<K, V>) -> Unit) {
        delegate.descendingIterator().forEach(action)
    }
}
