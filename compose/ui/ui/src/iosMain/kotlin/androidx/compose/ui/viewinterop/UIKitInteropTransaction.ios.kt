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

import androidx.collection.ObjectList
import androidx.collection.mutableObjectListOf
import androidx.compose.ui.internal.flatMap
import androidx.compose.ui.util.fastForEach
import platform.QuartzCore.CATransaction

/**
 * Lambda containing changes to UIKit objects, which can be synchronized within [CATransaction]
 */
internal typealias UIKitInteropAction = () -> Unit

/**
 * A transaction containing changes to UIKit objects to be synchronized within [CATransaction] inside a
 * renderer to make sure that changes in UIKit and Compose are visually simultaneous.
 * [actions] contains a list of lambdas that will be executed in the same CATransaction.
 * [isInteropActive] defines if rendering strategy should be changed along with this transaction.
 */
internal interface UIKitInteropTransaction {
    val actions: ObjectList<UIKitInteropAction>
    val isInteropActive: Boolean

    fun performTransaction() {
        actions.forEach {
            it.invoke()
        }
    }

    companion object {
        /**
         * Merges multiple transactions into a single transaction.
         *
         * @param transactions a list of transactions to be merged
         */
        fun merge(
            transactions: ObjectList<UIKitInteropTransaction>
        ): UIKitInteropTransaction =
            object : UIKitInteropTransaction {
                override val actions = transactions.flatMap { it.actions }
                override val isInteropActive = transactions.any { it.isInteropActive }
            }
    }
}

/**
 * A mutable transaction managed by [UIKitInteropContainer] to collect changes
 * to UIKit objects to be executed later.
 *
 * @see UIKitInteropContainer.scheduleUpdate
 */
internal class UIKitInteropMutableTransaction(
    override var isInteropActive: Boolean
) : UIKitInteropTransaction {
    private val _actions = mutableObjectListOf<UIKitInteropAction>()

    override val actions
        get() = _actions

    fun add(action: UIKitInteropAction) {
        _actions.add(action)
    }
}
