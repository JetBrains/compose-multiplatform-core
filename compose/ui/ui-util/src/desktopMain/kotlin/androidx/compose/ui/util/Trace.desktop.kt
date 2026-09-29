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

@file:JvmName("Trace_jbKt")
@file:JvmMultifileClass

package androidx.compose.ui.util

/**
 * Measures [block] and adds its time to the total for [sectionName].
 *
 * The measurement is off until you set the `compose.trace.frames` system property to `true`.
 * While the property is off, the only cost is one read of [ComposeFrameTrace.enabled]. See
 * [ComposeFrameTrace] for the report.
 */
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    if (!ComposeFrameTrace.enabled) {
        return block()
    }
    val startNanos = System.nanoTime()
    try {
        return block()
    } finally {
        ComposeFrameTrace.stop(sectionName, startNanos)
    }
}

actual fun traceValue(tag: String, value: Long) {
}
