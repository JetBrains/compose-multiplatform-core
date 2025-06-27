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

@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package androidx.room.concurrent

import androidx.annotation.RestrictTo

expect class AtomicInt {
    constructor(initialValue: Int)

    fun get(): Int

    fun set(value: Int)

    fun compareAndSet(expect: Int, update: Int): Boolean

    fun incrementAndGet(): Int

    fun getAndIncrement(): Int

    fun decrementAndGet(): Int
}

internal inline fun AtomicInt.loop(action: (Int) -> Unit): Nothing {
    while (true) {
        action(get())
    }
}

expect class AtomicBoolean {
    constructor(initialValue: Boolean)

    fun get(): Boolean

    fun compareAndSet(expect: Boolean, update: Boolean): Boolean
}
