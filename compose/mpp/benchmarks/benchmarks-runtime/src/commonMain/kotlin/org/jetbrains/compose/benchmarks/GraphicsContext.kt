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

import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use

interface GraphicsContext {
    suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T
    suspend fun awaitGPUCompletion()
}

internal object EmptyGraphicsContext : GraphicsContext {
    override suspend fun <T> withSurface(width: Int, height: Int, action: suspend (Surface) -> T): T =
        Surface.makeNull(width, height).use { action(it) }
    override suspend fun awaitGPUCompletion() = Unit
}