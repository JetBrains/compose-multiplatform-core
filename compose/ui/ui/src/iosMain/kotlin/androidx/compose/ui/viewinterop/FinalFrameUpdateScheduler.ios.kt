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

package androidx.compose.ui.viewinterop

/**
 * Coalesces repeated final-frame callbacks into one pending action.
 *
 * Each consumer owns its own scheduler so a mask for one interop view can be coalesced
 * independently from the global scene-layer update.
 */
internal class FinalFrameUpdateScheduler(
    private val scheduleFinalFrameUpdate: (() -> Unit) -> Unit
) {
    private var isUpdateScheduled = false

    val isScheduled: Boolean
        get() = isUpdateScheduled

    fun schedule(action: () -> Unit) {
        if (isUpdateScheduled) return

        isUpdateScheduled = true
        scheduleFinalFrameUpdate {
            isUpdateScheduled = false
            action()
        }
    }

    fun cancel() {
        // A callback already submitted to the transaction can still run; owners ignore stale work.
        isUpdateScheduled = false
    }
}

/**
 * Creates a scheduler bound to this container's final-frame action queue.
 *
 * The returned scheduler has an independent pending-update guard. Create one scheduler for each
 * coalescing scope, such as one interop mask or one scene-layer coordinator.
 */
internal fun InteropContainer.createFinalFrameUpdateScheduler(): FinalFrameUpdateScheduler {
    val iosContainer = this as IosInteropContainer
    return FinalFrameUpdateScheduler { action -> iosContainer.scheduleFinalFrameUpdate(action) }
}
