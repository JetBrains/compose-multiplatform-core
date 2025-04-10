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

package androidx.compose.runtime.internal

internal actual class AtomicReference<V> actual constructor(value: V) {
    private var value: V = value

    actual fun get() = value

    actual fun set(value: V) {
        this.value = value
    }

    actual fun getAndSet(value: V): V {
        val oldValue = this.value
        this.value = value
        return oldValue
    }

    actual fun compareAndSet(expect: V, newValue: V): Boolean {
        return if (this.value == expect) {
            this.value = newValue
            true
        } else {
            false
        }
    }
}

internal actual class AtomicInt actual constructor(value: Int) {
    private var value: Int = value

    actual fun get(): Int = value

    actual fun set(value: Int) {
        this.value = value
    }

    actual fun add(amount: Int): Int {
        value += amount
        return value
    }

    actual fun compareAndSet(expect: Int, newValue: Int): Boolean {
        return if (value == expect) {
            value = newValue
            true
        } else {
            false
        }
    }
}
