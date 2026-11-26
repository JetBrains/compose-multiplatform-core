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

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(NativeRuntimeApi::class)
internal actual object Platform {
    actual fun getGraphicsContext(): GraphicsContext = AppleGraphicsContextConfiguration.getAppleGraphicsContext()

    actual fun runGC() {
        GC.collect()
    }
}

/** Installed by the init module matching the Skiko version bundled with Compose. */
object AppleGraphicsContextConfiguration {
    var getAppleGraphicsContext: () -> GraphicsContext = { EmptyGraphicsContext }
}
