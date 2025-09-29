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

package androidx.navigation3.runtime

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord

/**
 * A mutable back stack of [NavKey] elements that integrates with Compose state.
 *
 * This class wraps a [SnapshotStateList] so that updates to the stack automatically trigger
 * recomposition in any observing Composables. It also implements [StateObject], which allows it to
 * participate in Compose's snapshot system directly.
 *
 * Typically, you won’t construct a [NavBackStack] manually. Instead, prefer using
 * [rememberNavBackStack], which provides a stack that is automatically saved and restored across
 * process death and configuration changes.
 *
 * ### Example
 *
 * ```kotlin
 * val backStack = NavBackStack(Home("start"))
 * backStack += Details("item42") // pushes onto stack
 * backStack.removeLast()         // pops stack
 * ```
 *
 * @constructor Creates a new back stack backed by the provided [SnapshotStateList].
 * @see rememberNavBackStack for lifecycle-aware persistence.
 */
public expect class NavBackStack<T : NavKey> : MutableList<T>, StateObject, RandomAccess {

    public constructor(base: SnapshotStateList<T>)

    public constructor()

    public constructor(vararg elements: T)

    override fun add(element: T): Boolean

    override fun remove(element: T): Boolean

    override fun addAll(elements: Collection<T>): Boolean

    override fun addAll(index: Int, elements: Collection<T>): Boolean

    override fun removeAll(elements: Collection<T>): Boolean

    override fun retainAll(elements: Collection<T>): Boolean

    override fun clear()

    override fun set(index: Int, element: T): T

    override fun add(index: Int, element: T)

    override fun removeAt(index: Int): T

    override fun listIterator(): MutableListIterator<T>

    override fun listIterator(index: Int): MutableListIterator<T>

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T>

    override val size: Int

    override fun isEmpty(): Boolean

    override fun contains(element: T): Boolean

    override fun containsAll(elements: Collection<T>): Boolean

    override fun get(index: Int): T

    override fun indexOf(element: T): Int

    override fun lastIndexOf(element: T): Int

    override fun iterator(): MutableIterator<T>

    override val firstStateRecord: StateRecord

    override fun prependStateRecord(value: StateRecord)
}
