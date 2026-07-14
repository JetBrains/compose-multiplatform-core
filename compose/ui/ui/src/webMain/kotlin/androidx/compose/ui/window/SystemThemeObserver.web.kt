/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skiko.SystemTheme

internal interface SystemThemeObserver {
    val currentSystemTheme: State<SystemTheme>

    fun dispose()
}

private class SystemThemeObserverImpl : SystemThemeObserver {
    override val currentSystemTheme: State<SystemTheme>
        get() = _currentSystemTheme

    private val mediaQueryListener: MediaQueryListener = object : MediaQueryListener("(prefers-color-scheme: dark)") {
        override fun onChange(matches: Boolean) {
            _currentSystemTheme.value = if (matches) SystemTheme.DARK else SystemTheme.LIGHT
        }
    }

    private val _currentSystemTheme = mutableStateOf(
        when(mediaQueryListener.matches()) {
            true -> SystemTheme.DARK
            false -> SystemTheme.LIGHT
            else -> SystemTheme.UNKNOWN
        }
    )

    override fun dispose() {
        mediaQueryListener.dispose()
    }
}

internal fun getSystemThemeObserver(): SystemThemeObserver = SystemThemeObserverImpl()
