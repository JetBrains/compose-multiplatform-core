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

package org.jetbrains.compose.kdtdispatcher

import androidx.compose.ui.ComposeUIDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
public class KdtMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int
        get() = 0

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return KdtMainDispatcher
    }
}

private object KdtMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = ImmediateKdtMainDispatcher

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        currentKdtMainDispatcher().dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return currentKdtMainDispatcher().isDispatchNeeded(context)
    }

    override fun toString(): String = currentKdtMainDispatcher().toString()
}

private object ImmediateKdtMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        currentKdtMainDispatcher().immediate.dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return currentKdtMainDispatcher().immediate.isDispatchNeeded(context)
    }

    override fun toString(): String = currentKdtMainDispatcher().immediate.toString()
}

// Dispatchers.Main routes here; ComposeUIDispatcher itself carries the live test/headless override
// (ComposeUIDispatcherOverride), so no second override layer is needed at this level.
private fun currentKdtMainDispatcher(): MainCoroutineDispatcher {
    return ComposeUIDispatcher
}
