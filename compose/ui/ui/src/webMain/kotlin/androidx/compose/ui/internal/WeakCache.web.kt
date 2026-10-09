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

package androidx.compose.ui.internal

import androidx.collection.mutableObjectListOf
import androidx.compose.ui.node.WeakReference
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsReference
import kotlin.js.get
import kotlin.js.toJsReference
import kotlin.js.unsafeCast
@OptIn(ExperimentalWasmJsInterop::class)
internal actual class WeakCache<T : Any> {
    private val values = mutableObjectListOf<WeakReference<T>>()

    // When an element is finalized, we'll receive a callback to remove its WeakReference
    // from the list. This avoids iterating the list on every operation, which would be
    // expensive on web due to the JS-interop boundary for each WeakRef.deref() call.
    private val registry = FinalizationRegistry { weakRefJsReference ->
        val weakRef: WeakReference<T> = weakRefJsReference.unsafeCast<JsReference<WeakReference<T>>>().get()
        values.remove(weakRef)
    }

    actual fun push(element: T) {
        val weakRef = WeakReference(element)
        values.add(weakRef)
        registry.register(element.toJsReference(), weakRef.toJsReference())
    }

    actual fun pop(): T? {
        while (values.isNotEmpty()) {
            val item = values.removeAt(values.lastIndex).get()
            if (item != null) {
                return item
            }
        }
        return null
    }

    actual val size: Int
        get() = values.size
}

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/FinalizationRegistry
@OptIn(ExperimentalWasmJsInterop::class)
internal external class FinalizationRegistry(cleanupCallback: (JsAny) -> Unit) {
    fun register(target: JsAny, heldValue: JsAny)
}
