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

package androidx.compose.runtime.collection

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList

internal inline fun <T, R> ObjectList<T>.fastMap(transform: (T) -> R): ObjectList<R> {
    val target = MutableObjectList<R>(size)
    forEach { target += transform(it) }
    return target
}

internal inline fun <T> ObjectList<T>.fastFilter(predicate: (T) -> Boolean): ObjectList<T> {
    if (all(predicate)) return this
    val target = MutableObjectList<T>()
    forEach { if (predicate(it)) target += it }
    return target
}

internal inline fun <T> ObjectList<T>.all(predicate: (T) -> Boolean): Boolean {
    forEach { if (!predicate(it)) return false }
    return true
}

internal fun <T> ObjectList<T>.toMutableObjectList(): MutableObjectList<T> {
    val target = MutableObjectList<T>(size)
    forEach { target += it }
    return target
}

internal fun <T, K : Comparable<K>> ObjectList<T>.sortedBy(selector: (T) -> K?): ObjectList<T> =
    if (isSorted(selector)) this else toMutableObjectList().also { it.sortBy(selector) }

internal fun <T, K : Comparable<K>> ObjectList<T>.isSorted(selector: (T) -> K?): Boolean {
    if (size <= 1) return true
    val previousValue = get(0)
    var previousKey = selector(previousValue) ?: return false
    for (i in 1 until size) {
        val value = get(i)
        val key = selector(value) ?: return false
        if (previousKey > key) return false
        previousKey = key
    }
    return true
}

internal fun <T, K : Comparable<K>> MutableObjectList<T>.sortBy(selector: (T) -> K?) {
    @Suppress("AsCollectionCall") // Needed to call sortBy
    asMutableList().sortBy(selector)
}

internal fun <T> MutableObjectList<T>.removeLast(): T {
    if (isEmpty()) throw NoSuchElementException("List is empty.")
    val last = size - 1
    return this[last].also { removeAt(last) }
}
