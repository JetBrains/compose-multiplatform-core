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

package androidx.compose.ui.graphics.platform

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.InternalComposeUiApi
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Process-wide registry holding the [PlatformGraphics] implementation supplied by the active
 * graphics backend. ui-graphics resolves its platform primitives through [requireCurrent]; the
 * backend installs itself once via [register] during its runtime initialization.
 */
@InternalComposeUiApi
object PlatformGraphicsRegistry {
    private var implementation: PlatformGraphics? = null
    private val lock = SynchronizedObject()

    /**
     * Registers the runtime implementation once per process/classloader.
     *
     * Call [clear] only from tests or controlled teardown before registering a replacement.
     */
    fun register(implementation: PlatformGraphics) {
        synchronized(lock) {
            val current = this.implementation
            when {
                current == null -> this.implementation = implementation
                current === implementation -> Unit
                else -> error(
                    "Compose UI graphics implementation is already registered with a different " +
                        "instance. Call clear() first if replacement is intentional."
                )
            }
        }
    }

    /** Asserts that [required] is the currently registered implementation. */
    fun checkIfRegistered(required: PlatformGraphics) =
        check(required === implementation) {
            "Registered implementation is $implementation, but required is $required"
        }

    internal fun requireCurrent(): PlatformGraphics =
        implementation ?: error("No Compose UI graphics implementation is registered.")

    /**
     * Clears the current implementation.
     *
     * Intended for tests or controlled teardown only.
     */
    @VisibleForTesting
    fun clear() {
        implementation = null
    }
}
