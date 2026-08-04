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

package androidx.compose.ui.desktop.windows

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.jetbrains.desktop.win32.Application as Win32Application

internal fun currentWindowsNativeApplication(): Win32Application =
    WindowsApplication.current().nativeApplication

sealed class WindowsKdtMainDispatcherBase : MainCoroutineDispatcher() {
    override val immediate: ImmediateWindowsKdtMainDispatcher by lazy { ImmediateWindowsKdtMainDispatcher() }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        currentWindowsNativeApplication().invokeOnDispatcher {
            block.run()
        }
    }
}

class ImmediateWindowsKdtMainDispatcher : WindowsKdtMainDispatcherBase() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !currentWindowsNativeApplication().isDispatcherThread()
    }

    override fun toString(): String {
        return "Dispatchers.MainKDT.immediate"
    }
}

class WindowsKdtMainDispatcher : WindowsKdtMainDispatcherBase() {
    override fun toString(): String {
        return "Dispatchers.MainKDT"
    }

    companion object {
        /** Process-wide instance so call sites can avoid constructing one each time. */
        val INSTANCE: WindowsKdtMainDispatcher = WindowsKdtMainDispatcher()
    }
}
