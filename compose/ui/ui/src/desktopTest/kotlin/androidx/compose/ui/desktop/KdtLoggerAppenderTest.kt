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

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.logging.BaseLogger
import androidx.compose.ui.desktop.logging.KLogger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * [KdtLoggerAppender] adapts this fork's [KLogger]/[BaseLogger] logging shim to KDT's macOS
 * `AppenderInterface` (AIR-6085 WS2 task 7) so native KDT log output routes through the same
 * logging pipeline as the rest of the JVM side. This is pure delegation: every method and
 * enabled-flag just forwards to the wrapped [KLogger].
 */
@Category(HeadlessTest::class)
class KdtLoggerAppenderTest {

    private class RecordingBase(
        override val isTraceEnabled: Boolean = true,
        override val isDebugEnabled: Boolean = true,
        override val isInfoEnabled: Boolean = true,
        override val isWarnEnabled: Boolean = true,
        override val isErrorEnabled: Boolean = true,
    ) : BaseLogger {
        val events = mutableListOf<String>()

        override fun trace(message: Any?) {
            events += "trace:$message"
        }

        override fun debug(message: Any?) {
            events += "debug:$message"
        }

        override fun info(message: Any?) {
            events += "info:$message"
        }

        override fun warn(message: Any?) {
            events += "warn:$message"
        }

        override fun error(message: Any?) {
            events += "error:$message"
        }

        override fun trace(t: Throwable?, message: Any?) {
            events += "trace:${t?.message}:$message"
        }

        override fun debug(t: Throwable?, message: Any?) {
            events += "debug:${t?.message}:$message"
        }

        override fun info(t: Throwable?, message: Any?) {
            events += "info:${t?.message}:$message"
        }

        override fun warn(t: Throwable?, message: Any?) {
            events += "warn:${t?.message}:$message"
        }

        override fun error(t: Throwable?, message: Any?) {
            events += "error:${t?.message}:$message"
        }
    }

    @Test
    fun allTenMethodsForwardToTheKLoggerInOrder() {
        val base = RecordingBase()
        val appender = KdtLoggerAppender(KLogger(base))
        val throwable = RuntimeException("boom")

        appender.trace("trace message")
        appender.debug("debug message")
        appender.info("info message")
        appender.warn("warn message")
        appender.error("error message")
        appender.trace(throwable, "trace with throwable")
        appender.debug(throwable, "debug with throwable")
        appender.info(throwable, "info with throwable")
        appender.warn(throwable, "warn with throwable")
        appender.error(throwable, "error with throwable")

        assertEquals(
            listOf(
                "trace:trace message",
                "debug:debug message",
                "info:info message",
                "warn:warn message",
                "error:error message",
                "trace:boom:trace with throwable",
                "debug:boom:debug with throwable",
                "info:boom:info with throwable",
                "warn:boom:warn with throwable",
                "error:boom:error with throwable",
            ),
            base.events,
        )
    }

    @Test
    fun enabledFlagsDelegate() {
        val disabledAppender = KdtLoggerAppender(
            KLogger(
                RecordingBase(
                    isTraceEnabled = false,
                    isDebugEnabled = false,
                    isInfoEnabled = false,
                    isWarnEnabled = false,
                    isErrorEnabled = false,
                ),
            ),
        )

        assertFalse(disabledAppender.isTraceEnabled)
        assertFalse(disabledAppender.isDebugEnabled)
        assertFalse(disabledAppender.isInfoEnabled)
        assertFalse(disabledAppender.isWarnEnabled)
        assertFalse(disabledAppender.isErrorEnabled)

        val enabledAppender = KdtLoggerAppender(KLogger(RecordingBase()))

        assertTrue(enabledAppender.isTraceEnabled)
        assertTrue(enabledAppender.isDebugEnabled)
        assertTrue(enabledAppender.isInfoEnabled)
        assertTrue(enabledAppender.isWarnEnabled)
        assertTrue(enabledAppender.isErrorEnabled)
    }
}
