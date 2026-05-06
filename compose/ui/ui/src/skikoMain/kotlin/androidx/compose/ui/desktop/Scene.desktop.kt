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

package androidx.compose.ui.desktop

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope


// TODO[wojciech.krystyniak] This should be internal, but we need it for TestWindow
class Scene<T> /* internal */ constructor(
    internal val coroutineScope: CoroutineScope,
    @PublishedApi internal val prepareMainThread: () -> T,
    @PublishedApi internal val restoreMainThread: (T) -> Unit,
) {
    @OptIn(ExperimentalContracts::class)
    inline fun <R> withPreparedMainThread(block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        val token = prepareMainThread()
        try {
            return block()
        } finally {
            restoreMainThread(token)
        }
    }
}

/* internal */ val ProvidableLocalScene = staticCompositionLocalOf<Scene<*>> {
    error("No Scene provided")
}

val LocalScene: CompositionLocal<Scene<*>> = ProvidableLocalScene
