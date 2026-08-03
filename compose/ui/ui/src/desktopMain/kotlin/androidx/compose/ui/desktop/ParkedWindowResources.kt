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

/**
 * Generic park/take/dispose bookkeeping for native window resources kept alive across a
 * lightweight-window reuse cycle: when a window is disposed but its native handle should
 * survive for reuse by the next window at the same [LightweightWindowId], the platform
 * `Application` parks the resources here instead of destroying them immediately, and either
 * [take]s them back for the new window or [disposeWith] / [drainWith]s them later.
 *
 * Platform-neutral and dependency-free (no KDT/native types) so it is unit-testable on its own;
 * extracted from `MacOsApplication.reusableNativeWindowResources` (AIR-6085 WS2 task 6) and
 * intended for reuse by other desktop platforms' Application implementations.
 *
 * Not thread-safe: callers are expected to confine all access to a single platform main thread,
 * matching the confinement of the `Application`/`Window` implementations that own an instance.
 */
internal class ParkedWindowResources<R : Any>(
    private val warn: (String) -> Unit,
) {
    private val parked = mutableMapOf<LightweightWindowId, R>()

    /** Stashes [resources] for [id], overwriting any entry already parked for it. */
    fun park(id: LightweightWindowId, resources: R) {
        parked[id] = resources
    }

    /**
     * Removes and returns the resources parked for [id]. If nothing is parked for [id], returns
     * null and [warn]s with a message that includes the currently parked ids, for triage.
     */
    fun take(id: LightweightWindowId): R? {
        val resources = parked.remove(id)
        if (resources == null) {
            warn("ParkedWindowResources.take: no resources parked for id=$id; parked=$keys")
        }
        return resources
    }

    /** True if [id] currently has parked resources. Does not remove them. */
    fun peekContains(id: LightweightWindowId): Boolean = parked.containsKey(id)

    /**
     * Removes the entry for [id], if any, and runs [destroyer] on it exactly once. Returns
     * false (without calling [destroyer]) if nothing was parked for [id].
     */
    fun disposeWith(id: LightweightWindowId, destroyer: (R) -> Unit): Boolean {
        val resources = parked.remove(id) ?: return false
        destroyer(resources)
        return true
    }

    /** True if no resources are currently parked. */
    val isEmpty: Boolean
        get() = parked.isEmpty()

    /** The ids currently holding parked resources. */
    val keys: Set<LightweightWindowId>
        get() = parked.keys

    /** Runs [destroyer] on every parked entry exactly once, then empties the registry. */
    fun drainWith(destroyer: (R) -> Unit) {
        parked.values.forEach(destroyer)
        parked.clear()
    }
}
