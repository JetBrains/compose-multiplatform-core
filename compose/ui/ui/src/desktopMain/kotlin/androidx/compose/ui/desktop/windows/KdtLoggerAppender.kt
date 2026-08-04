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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.desktop.logging.KLogger
import org.jetbrains.desktop.win32.AppenderInterface

/**
 * Adapts this fork's [KLogger]/`BaseLogger` logging shim to KDT's win32 `AppenderInterface` so
 * native KDT log output routes through the same logging pipeline as the rest of the JVM side.
 * Pure delegation: every method and enabled-flag forwards straight to [impl].
 */
internal class KdtLoggerAppender(private val impl: KLogger) : AppenderInterface {
    override val isTraceEnabled: Boolean
        get() = impl.isTraceEnabled
    override val isDebugEnabled: Boolean
        get() = impl.isDebugEnabled
    override val isInfoEnabled: Boolean
        get() = impl.isInfoEnabled
    override val isWarnEnabled: Boolean
        get() = impl.isWarnEnabled
    override val isErrorEnabled: Boolean
        get() = impl.isErrorEnabled

    override fun trace(message: String) {
        impl.trace(message)
    }

    override fun debug(message: String) {
        impl.debug(message)
    }

    override fun info(message: String) {
        impl.info(message)
    }

    override fun warn(message: String) {
        impl.warn(message)
    }

    override fun error(message: String) {
        impl.error(message)
    }

    override fun trace(t: Throwable, message: String) {
        impl.trace(t, message)
    }

    override fun debug(t: Throwable, message: String) {
        impl.debug(t, message)
    }

    override fun info(t: Throwable, message: String) {
        impl.info(t, message)
    }

    override fun warn(t: Throwable, message: String) {
        impl.warn(t, message)
    }

    override fun error(t: Throwable, message: String) {
        impl.error(t, message)
    }
}
