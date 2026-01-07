/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.lifecycle

internal actual class FastSafeIterableMap<K : Any, V : Any> {

    private val delegate = linkedMapOf<K, V>()

    actual fun contains(key: K): Boolean {
        return delegate.containsKey(key)
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        val existing = delegate[key]
        if (existing != null) {
            return existing
        }
        delegate[key] = value
        return null
    }

    actual fun remove(key: K): V? {
        return delegate.remove(key)
    }

    /**
     * In FastSafeIterableMap (Android), `ceil` returns the PREVIOUS entry. We replicate that
     * behavior here.
     */
    actual fun ceil(key: K): Map.Entry<K, V>? {
        if (!contains(key)) return null

        var previous: Map.Entry<K, V>? = null

        // Iterate over keys to avoid holding invalidatable Entry references.
        for (currentKey in delegate.keys) {
            if (currentKey == key) {
                return previous
            }
            // Snapshot the value to ensure we return a stable entry
            val value = delegate[currentKey]
            if (value != null) {
                previous = Entry(currentKey, value)
            }
        }
        return null
    }

    actual fun first(): Map.Entry<K, V> {
        return delegate.entries.first()
    }

    actual fun last(): Map.Entry<K, V> {
        return delegate.entries.last()
    }

    actual fun lastOrNull(): Map.Entry<K, V>? {
        return delegate.entries.lastOrNull()
    }

    actual fun size(): Int {
        return delegate.size
    }

    actual fun forEachWithAdditions(action: (Map.Entry<K, V>) -> Unit) {
        val visited = mutableSetOf<K>()

        // Snapshot KEYS, not entries. Keys are safe immutable references.
        // Copying to a list prevents CME on the iterator itself.
        var candidates = delegate.keys.toList()

        while (candidates.isNotEmpty()) {
            for (key in candidates) {
                // Check if we already visited this key (optimization)
                if (visited.add(key)) {
                    // Re-fetch value from the live map.
                    // If returns null, the item was removed during the loop -> skip it.
                    val value = delegate[key]
                    if (value != null) {
                        action(Entry(key, value))
                    }
                }
            }

            // If the map grew while we were looping, we need to process the new additions.
            if (delegate.size > visited.size) {
                candidates = delegate.keys.filter { !visited.contains(it) }
            } else {
                break
            }
        }
    }

    actual fun forEachReversed(action: (Map.Entry<K, V>) -> Unit) {
        // Start with a safe snapshot of the current keys
        val keys = delegate.keys.toList()

        // Iterate by index to avoid iterator allocation
        var index = keys.size - 1
        while (index >= 0) {
            val key = keys[index]

            // Re-fetch value safely and guard against removal during iteration
            val value = delegate[key]
            if (value != null) {
                action(Entry(key, value))
            }

            index--
        }
    }

    /**
     * A simple immutable implementation of Map.Entry. Used to pass safe snapshots to callers,
     * preventing crashes if the backing map changes.
     */
    private data class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>
}
