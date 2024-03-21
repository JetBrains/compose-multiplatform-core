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
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import noria.impl.*
import androidx.compose.runtime.remember

/**
 * Context providing methods for incremental computations:
 * [cell], [state], [memo], [sideEffect]
 *
 * Each computation has an identity-key which is derived from its position
 * in callstack by compiler plugin. Sometimes you need to manually specify
 * key for computation, for example when multiple computations are instantiated
 * in a loop, you can use [scope] for that.
 **/
interface NoriaContext : ClosureContext {
    val noriaState: NoriaState? get() = null

    val <T> CompositionLocal<T>.current: T
        @ReadOnlyComposable
        @Composable
        get() = this.current

    companion object : NoriaContext
}

class Storage<T : Any>(var value: T?)

/**
 * @return a positional mutable slot of memory
 **/
@Composable
fun <T : Any> NoriaContext.storage(): Storage<T> {
    return remember { Storage(null) }
}

/**
 * @return a positional mutable slot of memory which gets reset when the [keys] change
 **/
@Composable
fun <T : Any> NoriaContext.storage(vararg keys: Any): Storage<T> {
    return remember(keys) { Storage(null) }
}

/**
 * same as [remember], but guarded against calling during measure and other stuff.
 *
 * It is the only safe way to perform side effects during noria fn execution.
 * The effect will be performed exactly once (per position) and only if the caller has allowed it.
 **/
@Composable
fun NoriaContext.sideEffect(vararg keys: Any?, effect: () -> Unit) {
    if (!isReadOnly()) {
        remember(keys = keys, effect)
    }
}

@Composable
@OptOutFromOuterScopes
inline fun <T : NoriaContext, R> T.catching(block: T.() -> R): Result<R> {
    return runCatching(block)
}

/**
 * Must be used to identify the position when the call site is shared.
 * For example, inside loops.
 * ```
 * // Note that this is *not* the index!
 * for (id in ids) {
 *   scope(id) {
 *     f(id)
 *   }
 * }
```
 **/
@Composable
@OptOutFromOuterScopes
inline fun <T : NoriaContext, R> T.scope(key: Any, content: @Composable T.() -> R): R {
    return key(key) {
        content()
    }
}

@Composable
@OptOutFromOuterScopes
inline fun <T : NoriaContext, R> T.scope(key: Int, content: @Composable T.() -> R): R {
    return key(key) {
        content()
    }
}

@Composable
@OptOutFromOuterScopes
inline fun <T : NoriaContext, R> T.scope(id: ID, content: @Composable T.() -> R): R {
    return key(id.id) {
        content()
    }
}

@Composable
@OptOutFromOuterScopes
inline fun <T : NoriaContext, R> T.scope(key: Long, content: @Composable T.() -> R): R {
    val keyHash = (key xor (key ushr 32)).toInt()
    return scope(keyHash, content)
}

@Composable
@OptOutFromOuterScopes
fun <T : NoriaContext, R> T.scope(content: @Composable T.() -> R): R {
    return content()
}

/**
 * Lambda is a fundamental unit for incremental computation.
 *
 * Once reached, noria will try to avoid entering lambda if the previous run had the same closure.
 **/
@Composable
fun NoriaContext.lambda(
    alwaysDirty: Boolean = false,
    block: @Composable NoriaContext.() -> Unit,
) {
    if (alwaysDirty) TODO("Not yet implemented")
    block()
}

@Composable
fun NoriaContext.wrap(
    body: @Composable NoriaContext.() -> Unit,
    wrap: @Composable NoriaContext.(@Composable NoriaContext.() -> Unit) -> Unit,
) {
    lambda(alwaysDirty = true) {
        wrap {
            lambda {
                body()
            }
        }
    }
}

interface Noria {
    fun update()
    fun destroy()
}

@Target(AnnotationTarget.FUNCTION)
annotation class OptOutFromOuterScopes

@Target(AnnotationTarget.FUNCTION)
annotation class OptOutFromInnerScopes
