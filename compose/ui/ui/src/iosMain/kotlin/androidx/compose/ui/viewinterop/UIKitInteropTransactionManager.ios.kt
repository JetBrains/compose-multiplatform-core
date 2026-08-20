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

import androidx.compose.ui.util.fastForEach

/**
 * Owns the mutable interop transaction and the lifecycle needed to either hand it to the renderer
 * or apply an unchanged direct view-update batch out of frame.
 */
internal class UIKitInteropTransactionManager {
    private var transaction = UIKitInteropMutableTransaction(isInteropActive = false)
    private var viewUpdateToken: UIKitInteropViewUpdateToken? = null
    private var nextViewUpdateGeneration = 0L

    var isInteropActive: Boolean = false
        set(value) {
            field = value
            transaction.isInteropActive = value
        }

    fun dispose() {
        viewUpdateToken = null
        retrieveTransaction().performTransaction()
    }

    fun retrieveTransaction(): UIKitInteropTransaction {
        val result = transaction
        transaction = UIKitInteropMutableTransaction(isInteropActive = isInteropActive)
        return result
    }

    fun scheduleFrameSynchronizedAction(action: UIKitInteropAction) {
        transaction.scheduleFrameSynchronizedAction(action)
    }

    fun scheduleViewUpdate(holder: InteropViewHolder) {
        transaction.scheduleViewUpdate(holder, nextViewUpdateGeneration++)
    }

    fun snapshotPendingViewUpdatesState(captureId: Long) {
        viewUpdateToken = if (transaction.hasPendingViewUpdatesOnly) {
            UIKitInteropViewUpdateToken(captureId, transaction.viewUpdateGeneration)
        } else {
            null
        }
    }

    fun performPendingViewUpdates(captureId: Long, canPerform: Boolean) {
        val token = viewUpdateToken ?: return
        if (token.captureId != captureId) return

        viewUpdateToken = null

        if (
            canPerform &&
            transaction.hasPendingViewUpdatesOnly &&
            token.generation == transaction.viewUpdateGeneration
        ) {
            retrieveTransaction().performTransaction()
        }
    }
}

private class UIKitInteropViewUpdateToken(
    val captureId: Long,
    val generation: Long,
)

private class UIKitInteropMutableTransaction(
    override var isInteropActive: Boolean
) : UIKitInteropTransaction {
    private val actions = mutableListOf<UIKitInteropAction>()
    private val holdersWithPendingViewUpdates = mutableSetOf<InteropViewHolder>()
    private var _viewUpdateGeneration = 0L

    private var requiresFrameSynchronization = false

    override val hasPendingActions: Boolean
        get() = actions.isNotEmpty()

    val viewUpdateGeneration get() = _viewUpdateGeneration

    val hasPendingViewUpdatesOnly: Boolean
        get() = hasPendingActions && !requiresFrameSynchronization

    override fun performTransaction() {
        actions.fastForEach { it.invoke() }
    }

    /**
     * Schedules an action that must be applied together with the Compose frame.
     */
    fun scheduleFrameSynchronizedAction(action: UIKitInteropAction) {
        actions.add(action)
        requiresFrameSynchronization = true
    }

    /**
     * Schedules a user-provided `UIKitView.update` or `UIKitViewController.update` callback.
     */
    fun scheduleViewUpdate(holder: InteropViewHolder, generation: Long) {
        _viewUpdateGeneration = generation
        if (holdersWithPendingViewUpdates.add(holder)) {
            actions.add { holder.update() }
        }
    }
}

private typealias UIKitInteropAction = () -> Unit
