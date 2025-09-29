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

package androidx.tracing.driver

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Useful in the context of structured concurrency to keep track of flows. */
@PublishedApi
internal suspend fun obtainPlatformThreadContextElement(): PlatformThreadContextElement<*>? {
    return coroutineContext[PlatformThreadContextElement.KEY]
}

/**
 * We use the [PlatformThreadContextElement] construct to know when a coroutine has suspended, and
 * about to resume on a `Thread`.
 */
@PublishedApi
internal abstract class PlatformThreadContextElement<S>
internal constructor(public open val name: String, public open val flowIds: List<Long>) :
    AbstractCoroutineContextElement(key = KEY) {
    // Always starts in a begin state.
    @PublishedApi internal val started: AtomicInteger = AtomicInteger(STATE_BEGIN)

    /**
     * This method is called **before a coroutine is resumed** on a thread that belongs to a
     * dispatcher.
     */
    @PublishedApi internal abstract fun updateThreadContext(context: CoroutineContext): S

    /** This method is called **after** a coroutine is suspend on the current thread. */
    @PublishedApi internal abstract fun restoreThreadContext(context: CoroutineContext, oldState: S)

    @PublishedApi
    internal companion object {
        // Used to represent that the current slice has begun.
        @PublishedApi internal const val STATE_BEGIN: Int = 1
        // Used to represent that the current slice has ended.
        @PublishedApi internal const val STATE_END: Int = 0
        @PublishedApi
        internal val KEY: CoroutineContext.Key<PlatformThreadContextElement<*>> =
            object : CoroutineContext.Key<PlatformThreadContextElement<*>> {}
    }
}

/** Builds an instance of the Platform specific [PlatformThreadContextElement]. */
@PublishedApi
internal expect fun buildThreadContextElement(
    name: String,
    flowIds: List<Long>,
    updateThreadContextBlock: (context: CoroutineContext) -> Unit,
    restoreThreadContextBlock: (context: CoroutineContext) -> Unit,
): PlatformThreadContextElement<Unit>
