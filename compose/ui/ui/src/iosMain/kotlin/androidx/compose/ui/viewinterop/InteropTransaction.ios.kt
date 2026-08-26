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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.util.fastForEach
import platform.QuartzCore.CATransaction

/**
 * Lambda containing changes to UIKit objects, which can be synchronized within [CATransaction]
 */
internal typealias InteropSyncAction = () -> Unit

/**
 * A batch of changes to UIKit objects.
 *
 * Frame-synchronized changes are performed within the renderer's [CATransaction], so UIKit and
 * Compose are visually simultaneous. Updates to already attached UIKit views may instead be
 * performed in an otherwise idle UIKit draw callback.
 *
 * [isInteropActive] defines if rendering strategy should be changed along with this transaction.
 */
internal interface InteropSyncTransaction {
    val hasPendingActions: Boolean
    val isInteropActive: Boolean

    fun performTransaction()

    companion object {
        /**
         * Merges multiple transactions into a single transaction.
         *
         * @param transactions a list of transactions to be merged
         */
        fun merge(
            transactions: List<InteropSyncTransaction>
        ): InteropSyncTransaction =
            object : InteropSyncTransaction {
                override val hasPendingActions = transactions.any { it.hasPendingActions }
                override val isInteropActive = transactions.any { it.isInteropActive }

                override fun performTransaction() {
                    transactions.fastForEach { it.performTransaction() }
                }
            }

        val Empty: InteropSyncTransaction = object : InteropSyncTransaction {
            override val hasPendingActions: Boolean = false
            override val isInteropActive: Boolean = false

            override fun performTransaction() = Unit
        }
    }
}

/**
 * A mutable transaction managed by [IosInteropContainer] to collect changes
 * to UIKit objects to be executed later.
 *
 * @see IosInteropContainer.scheduleUpdate
 */
internal class InteropMutableTransaction(
    override var isInteropActive: Boolean
) : InteropSyncTransaction {
    private val actions = mutableListOf<InteropSyncAction>()
    private val holdersWithPendingViewUpdates = mutableSetOf<InteropViewHolder>()

    private var requiresFrameSynchronization = false

    override val hasPendingActions: Boolean
        get() = actions.isNotEmpty()

    val hasPendingViewUpdatesOnly: Boolean
        get() = hasPendingActions && !requiresFrameSynchronization

    override fun performTransaction() {
        actions.fastForEach { it.invoke() }
    }

    /**
     * Schedules an action that must be applied together with the Compose frame.
     */
    fun scheduleFrameSynchronizedAction(action: InteropSyncAction) {
        actions.add(action)
        requiresFrameSynchronization = true
    }

    /**
     * Schedules a user-provided `UIKitView.update` or `UIKitViewController.update` callback.
     */
    fun scheduleViewUpdate(holder: InteropViewHolder) {
        if (holdersWithPendingViewUpdates.add(holder)) {
            actions.add { holder.update() }
        }
    }
}
