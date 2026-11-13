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
package org.jetbrains.compose.benchmarks


internal actual object Platform {
    actual fun getGraphicsContext(): GraphicsContext = JvmGraphicsContextConfiguration.getJvmGraphicsContext()

    actual fun runGC() {
        System.gc()
    }
}

/**
 * Available JVM graphics backends depend on the Skiko version bundled with Compose.
 * The app selects a compatible benchmarks-skiko-init module, which installs this
 * provider before measurements start without making the runtime depend on version-specific APIs.
 */
object JvmGraphicsContextConfiguration {
    var getJvmGraphicsContext: () -> GraphicsContext  = { EmptyGraphicsContext }
}
