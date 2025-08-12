/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.room

import androidx.kruth.assertThat
import androidx.room.ObservedTableStates.ObserveOp
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ObservedTableStatesTest {
    private lateinit var tableStates: ObservedTableStates

    @BeforeTest
    fun setup() {
        tableStates = ObservedTableStates(TABLE_COUNT)
    }

    @Test
    fun basicAdd() = runTest {
        assertThat(tableStates.onObserverAdded(intArrayOf(2, 3))).isTrue()
        tableStates.onSync { ops ->
            assertThat(ops)
                .isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD, 3 to ObserveOp.ADD)))
            return@onSync true
        }
    }

    @Test
    fun basicRemove() = runTest {
        tableStates.onObserverAdded(intArrayOf(2, 3))
        tableStates.onSync { true }

        assertThat(tableStates.onObserverRemoved(intArrayOf(3))).isTrue()
        tableStates.onSync { ops ->
            assertThat(ops).isEqualTo(createSyncResult(mapOf(3 to ObserveOp.REMOVE)))
            return@onSync true
        }
    }

    @Test
    fun noChange() = runTest {
        tableStates.onObserverAdded(intArrayOf(1, 3))
        tableStates.onSync { true }

        assertThat(tableStates.onObserverAdded(intArrayOf(3))).isFalse()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }
    }

    @Test
    fun addAndDeleteNetMoChange() = runTest {
        tableStates.onObserverAdded(intArrayOf(1, 3))
        tableStates.onSync { true }

        assertThat(tableStates.onObserverRemoved(intArrayOf(1, 3))).isTrue()
        assertThat(tableStates.onObserverAdded(intArrayOf(1, 3))).isTrue()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }
    }

    @Test
    fun multipleAddPendingChange() = runTest {
        assertThat(tableStates.onObserverAdded(intArrayOf(2))).isTrue()
        assertThat(tableStates.onObserverAdded(intArrayOf(2))).isTrue()
        tableStates.onSync { ops ->
            assertThat(ops).isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD)))
            return@onSync true
        }

        assertThat(tableStates.onObserverAdded(intArrayOf(2))).isFalse()
        tableStates.onSync { ops ->
            assertThat(ops).isNull()
            return@onSync true
        }
    }

    @Test
    fun multipleAdditionsDeletions() = runTest {
        tableStates.onObserverAdded(intArrayOf(2, 4))
        tableStates.onSync { true }

        assertThat(tableStates.onObserverAdded(intArrayOf(2))).isFalse()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }

        assertThat(tableStates.onObserverAdded(intArrayOf(2, 4))).isFalse()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }

        assertThat(tableStates.onObserverRemoved(intArrayOf(2))).isFalse()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }

        assertThat(tableStates.onObserverRemoved(intArrayOf(2, 4))).isFalse()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }

        assertThat(tableStates.onObserverAdded(intArrayOf(1, 3))).isTrue()
        assertThat(tableStates.onObserverRemoved(intArrayOf(2, 4))).isTrue()
        tableStates.onSync { ops ->
            assertThat(ops)
                .isEqualTo(
                    createSyncResult(
                        mapOf(
                            1 to ObserveOp.ADD,
                            2 to ObserveOp.REMOVE,
                            3 to ObserveOp.ADD,
                            4 to ObserveOp.REMOVE,
                        )
                    )
                )
            true
        }
    }

    @Test
    fun syncNotCommitted() = runTest {
        assertThat(tableStates.onObserverAdded(intArrayOf(2, 3))).isTrue()
        tableStates.onSync { ops ->
            assertThat(ops)
                .isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD, 3 to ObserveOp.ADD)))
            return@onSync false // don't commit sync
        }
        tableStates.onSync { ops ->
            assertThat(ops)
                .isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD, 3 to ObserveOp.ADD)))
            return@onSync true // commit it
        }
    }

    /**
     * Validates that a concurrent addition / removal of observers in-between a sync will cause the
     * sync to be outdated and not commit, letting the latest one do the actual sync.
     */
    @Test
    fun syncOutdated() = runTest {
        assertThat(tableStates.onObserverAdded(intArrayOf(2))).isTrue()
        val firstLatch = CompletableDeferred<Unit>()
        val secondLatch = CompletableDeferred<Unit>()
        val firstSync = launch {
            tableStates.onSync { ops ->
                assertThat(ops).isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD)))
                secondLatch.complete(Unit) // let 2nd sync go ahead
                firstLatch.await() // wait for 2nd sync to complete
                return@onSync true
            }
        }
        val secondSync = launch {
            secondLatch.await() // wait for 1st sync to start
            assertThat(tableStates.onObserverAdded(intArrayOf(2, 3))).isTrue()
            tableStates.onSync { ops ->
                assertThat(ops)
                    .isEqualTo(createSyncResult(mapOf(2 to ObserveOp.ADD, 3 to ObserveOp.ADD)))
                return@onSync true
            }
            firstLatch.complete(Unit) // let 1st sync complete
        }
        listOf(firstSync, secondSync).joinAll()
        tableStates.onSync { ops ->
            assertNull(ops)
            return@onSync true
        }
    }

    companion object {
        private const val TABLE_COUNT = 5

        private fun createSyncResult(tuples: Map<Int, ObserveOp>): Array<ObserveOp> {
            return Array(TABLE_COUNT) { i -> tuples[i] ?: ObserveOp.NO_OP }
        }
    }
}
