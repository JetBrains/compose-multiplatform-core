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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.compose.mpp.layoutinspector

import kotlin.test.Test
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fflush
import platform.posix.getenv
import platform.posix.stdout

/** Launched separately by the JVM integration test, not as part of the app's production code. */
class SimulatorAgentFixture {
    @Test
    fun serveHostClient() {
        val stopFile = getenv("LAYOUT_INSPECTOR_FIXTURE_STOP_FILE")?.toKString() ?: return
        runBlocking {
            val agent = ComposeLayoutInspector(52900..52909)
            try {
                println("INSPECTOR_READY:${agent.start()}")
                fflush(stdout)
                withTimeout(45_000) {
                    while (access(stopFile, F_OK) != 0) delay(25)
                }
            } finally { agent.stop() }
        }
    }
}
