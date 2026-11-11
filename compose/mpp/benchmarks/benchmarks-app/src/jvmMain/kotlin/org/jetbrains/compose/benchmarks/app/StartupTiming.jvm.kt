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

package org.jetbrains.compose.benchmarks.app

import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource.Monotonic

// RuntimeMXBean reports JVM startup, which is an approximation of process startup.
internal actual fun getProcessStartTime(): Monotonic.ValueTimeMark {
    val runtime = ManagementFactory.getRuntimeMXBean()
    val now = Monotonic.markNow()
    return now - runtime.uptime.milliseconds
}
