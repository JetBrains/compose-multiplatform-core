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

package noria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import noria.impl.EffectCoroutineContextCompositionLocal
import noria.impl.NoriaState

fun interface Cell<out T> {
    data class Constant<T>(val c: T) : Cell<T> {
        override fun read(): T = c
    }

    fun read(): T
}

interface StateCell<T> : Cell<T> {
    data class Constant<T>(val t: T) : StateCell<T> {
        override fun read(): T = t

        override fun update(f: (T) -> T) {
        }
    }

    fun update(f: (T) -> T)
}

interface OutputCell<T> : StateCell<T> {
    fun setValue(noriaState: NoriaState, value: T)
    fun poison(x: Throwable)
}

fun <T> StateCell<T>.set(x: T) {
    update { x }
}

val WILDCARD: Any get() = Any()

/**
 * Evaluates compute in new and remembers its result value.
 *
 * Value of computation can be received using [Cell.read] method.
 * Reading computation adds it as a dependency to current
 * If you don't want to produce a dependency, or don't have a context you
 * can pass null receiver to read, this will return the most recent committed value.
 *
 * Result value is reevaluated when either bindings change, env changes,
 * or when any of its dependencies (added during compute) are reevaluated.
 * This results a chaining update of computation dependants.
 */
@Deprecated(
    "Please use Compose Compatible API: rememberCell + CellConsumer as a generic variant " +
        "or derivedStateOf if dealing with a highly-frequently changing state and comparably few corresponding changes in the UI",
    ReplaceWith("rememberCell(block)", "fleet.compose.runtime.rememberCell")
)
@Composable
inline fun <T> cell(block: @Composable () -> T): Cell<T> {
    return rememberLegacyCell(block)
}

@Composable
inline fun <T> activeCell(block: @Composable () -> T): Cell<T> {
    // TODO Some mechanism of always dirty involving a composition local that gets counted up every frame?
    return rememberLegacyCell(block)
}

@Composable
inline fun <T> state(crossinline init: () -> T): StateCell<T> {
    return state(null, init = init)
}

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> state(
    noinline reader: () -> T,
    noinline updater: ((T) -> T) -> Unit
): StateCell<T> {
    val readerState = rememberUpdatedState(reader)
    val updaterState = rememberUpdatedState(updater)
    return scope(2) {
        remember {
            object : StateCell<T> {
                override fun read(): T {
                    return readerState.value()
                }

                override fun update(f: (T) -> T) {
                    Snapshot.withoutReadObservation { updaterState.value }.invoke(f)
                }
            }
        }
    }
}

@Composable
inline fun <T> state(vararg inputs: Any?, crossinline init: () -> T): StateCell<T> {
    return remember(*inputs) {
        stateCellNoRemember(init)
    }
}

@OptIn(InternalComposeApi::class)
inline fun <T> stateCellNoRemember(
    init: () -> T,
): StateCell<T> {
    var backingState by mutableStateOf(init())
    return object : StateCell<T> {
        override fun read(): T = backingState

        override fun update(f: (T) -> T) {
            backingState = f(backingState)
        }
    }
}

/**
 * Memorizes result of compute adding a dependency to current context.
 *
 * Shortcut for `cell { ... }.read()`
 *
 * @see [cell]
 */
@Composable
inline fun <T> memo(crossinline block: @Composable () -> T): T {
    return rememberLegacyCell(block).read()
}

/**
 * read the Cell privately
 **/
fun <T> Cell<T>.readNonReactive(): T {
    return Snapshot.withoutReadObservation { read() }
}

fun <T> Cell<Cell<T>>.flatten(): Cell<T> {
    val self = this
    return Cell {
        self.read().read()
    }
}

fun <T> StateCell<T?>.notNull(default: () -> T): StateCell<T> {
    val self = this
    return object : StateCell<T> {
        var cachedDefaultVar: T? = null
        val cachedDefault: T
            get() {
                if (cachedDefaultVar == null) {
                    cachedDefaultVar = default()
                }
                return cachedDefaultVar!!
            }

        override fun read(): T {
            return self.read() ?: cachedDefault
        }

        override fun update(f: (T) -> T) {
            self.update { f(readNonReactive()) }
        }
    }
}

/**
 * creates a stable view for the part of the original [state] cell
 * update to this cell will result in update of the original [state] cell
 * [extract] extracts the part of an interest from the original [state] value
 * [imprint] updates the original [state] value given the new value of the part
 * */
@Composable
fun <T, U> lens(
    state: StateCell<T>,
    extract: (T) -> U,
    imprint: (T, U) -> T,
): StateCell<U> {
    val state1 = rememberUpdatedState(state)
    val extract1 = rememberUpdatedState(extract)
    val imprint1 = rememberUpdatedState(imprint)
    return remember {
        object : StateCell<U> {
            override fun read(): U {
                return extract1.value.invoke(state1.value.read())
            }

            override fun update(f: (U) -> U) {
                val state2 = Snapshot.withoutReadObservation { state1.value }
                state2.update { t ->
                    val extract2 = Snapshot.withoutReadObservation { extract1.value }
                    imprint1.value(t, f(extract2(t)))
                }
            }
        }
    }
}

/**
 * noRemember version of [lens]
 * */
fun <T, U> StateCell<T>.lensNoRemember(extract: (T) -> U, imprint: (T, U) -> T): StateCell<U> {
    val model = this
    return object : StateCell<U> {
        override fun read(): U {
            return extract(model.read())
        }

        override fun update(f: (U) -> U) {
            model.update { t ->
                imprint(t, f(extract(t)))
            }
        }
    }
}

/**
 * Invokes [onChange] on every computation change except for initial value
 */
@Composable
fun <T> listener(cell: Cell<T>, onChange: (data: T) -> Unit) {
    val initializedStorage = storage<Boolean>()
    val value = cell.read()
    remember(value) {
        if (initializedStorage.value == true) {
            onChange(value)
        } else {
            initializedStorage.value = true
        }
    }
}

@Composable
fun <T : Any> observe(cell: Cell<T>, onChange: (T, firstTime: Boolean) -> Unit) {
    if (!isReadOnly()) {
        val oldValue = storage<T>()
        val newValue = cell.read()
        if (oldValue.value != newValue) {
            onChange(newValue, oldValue.value == null)
        }
        oldValue.value = newValue
    }
}

@Composable
@PublishedApi
internal inline fun <T> rememberLegacyCell(block: @Composable () -> T): Cell<T> {
    var result by remember { mutableStateOf<T?>(null) }
    result = block()
    return remember {
        object : Cell<T> {
            override fun read(): T {
                @Suppress("UNCHECKED_CAST")
                return result as T
            }
        }
    }
}