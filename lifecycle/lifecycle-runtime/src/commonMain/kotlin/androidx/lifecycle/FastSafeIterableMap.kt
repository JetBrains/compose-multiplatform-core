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

internal expect class FastSafeIterableMap<K : Any, V : Any>() {

    fun contains(key: K): Boolean

    fun putIfAbsent(key: K, value: V): V?

    fun remove(key: K): V?

    fun ceil(key: K): Map.Entry<K, V>?

    fun first(): Map.Entry<K, V>

    fun last(): Map.Entry<K, V>

    fun lastOrNull(): Map.Entry<K, V>?

    fun size(): Int

    fun forEachWithAdditions(action: (Map.Entry<K, V>) -> Unit)

    fun forEachReversed(action: (Map.Entry<K, V>) -> Unit)
}
