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

/** Coalesces repeated transaction-end update callbacks into one pending action. */
internal class TransactionEndUpdateScheduler(
    private val scheduleByDefault: (() -> Unit) -> Unit
) {
    // True while a transaction-end update callback is pending. Later schedule requests are coalesced
    // into that callback, which must read the latest state when it runs.
    private var isUpdateScheduled = false

    val isScheduled: Boolean
        get() = isUpdateScheduled

    fun schedule(action: () -> Unit) = enqueue(scheduleByDefault, action)

    fun schedule(transactionScope: InteropTransactionScope, action: () -> Unit) =
        enqueue(transactionScope::scheduleAtTransactionEnd, action)

    fun cancel() {
        // A callback already submitted to the transaction can still run; owners ignore stale work.
        isUpdateScheduled = false
    }

    private fun enqueue(
        scheduleAtTransactionEnd: (() -> Unit) -> Unit,
        action: () -> Unit
    ) {
        if (isUpdateScheduled) return

        isUpdateScheduled = true
        scheduleAtTransactionEnd {
            isUpdateScheduled = false
            action()
        }
    }
}

internal fun InteropContainer.createTransactionEndUpdateScheduler(): TransactionEndUpdateScheduler {
    val iosContainer = this as IosInteropContainer
    return TransactionEndUpdateScheduler { action ->
        iosContainer.scheduleAtTransactionEnd { action() }
    }
}
