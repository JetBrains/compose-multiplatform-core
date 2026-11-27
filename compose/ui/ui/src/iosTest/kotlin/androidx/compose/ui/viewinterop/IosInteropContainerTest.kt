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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.UIKit.UIView

class IosInteropContainerTest {
    @Test
    fun transactionEndUpdateFromRegularActionRunsInSameTransaction() {
        val container = container()
        var transactionEndUpdateRan = false

        container.scheduleScopedUpdate { transactionScope ->
            transactionScope.scheduleAtTransactionEnd { transactionEndUpdateRan = true }
        }

        val transaction = container.retrieveTransaction()
        transaction.performTransaction()

        assertTrue(
            transactionEndUpdateRan,
            "A transaction-end update discovered by a regular action must not wait for the next frame",
        )
        assertFalse(
            container.hasPendingUpdates,
            "The transaction-end update must not remain scheduled in the next transaction",
        )
    }

    @Test
    fun transactionEndUpdateFromTransactionEndUpdateRunsInSameTransaction() {
        val container = container()
        var firstUpdateRan = false
        var secondUpdateRan = false

        container.scheduleAtTransactionEnd { transactionScope ->
            firstUpdateRan = true
            transactionScope.scheduleAtTransactionEnd { secondUpdateRan = true }
        }

        val transaction = container.retrieveTransaction()
        transaction.performTransaction()

        assertTrue(firstUpdateRan)
        assertTrue(
            secondUpdateRan,
            "A transaction-end update scheduled by another transaction-end update must run in the same " +
                "transaction",
        )
        assertFalse(container.hasPendingUpdates)
    }

    private fun container() = IosInteropContainer(
        overlayContainer = UIView(),
        backgroundContainer = UIView(),
        requestRedraw = {},
    )
}
