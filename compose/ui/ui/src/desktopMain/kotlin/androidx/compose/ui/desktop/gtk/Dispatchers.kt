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

package androidx.compose.ui.desktop.gtk

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.jetbrains.desktop.gtk.Application

internal fun currentGtkNativeApplication(): Application = GtkApplication.current().nativeApplication

sealed class GtkKdtMainDispatcherBase : MainCoroutineDispatcher() {
    override val immediate: ImmediateGtkKdtMainDispatcher by lazy {
        ImmediateGtkKdtMainDispatcher()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        currentGtkNativeApplication().runOnEventLoopAsync {
            block.run()
        }
    }
}

class ImmediateGtkKdtMainDispatcher : GtkKdtMainDispatcherBase() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !currentGtkNativeApplication().isEventLoopThread()
    }

    override fun toString(): String {
        return "Dispatchers.MainKDT.immediate"
    }
}

class GtkKdtMainDispatcher : GtkKdtMainDispatcherBase() {
    override fun toString(): String {
        return "Dispatchers.MainKDT"
    }
}
