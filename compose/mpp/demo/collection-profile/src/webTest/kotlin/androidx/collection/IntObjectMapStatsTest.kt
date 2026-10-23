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

package androidx.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntObjectMapStatsTest {
    @Test
    fun oneSamplePerInstanceIncludingEmptyMaps() {
        // Initialize the shared empty singleton before taking a checkpoint.
        emptyIntObjectMap<Unit>()
        val firstInstance = IntObjectMapStats.instanceCount

        for (size in listOf(0, 2, 4, 8, 16)) {
            val map = MutableIntObjectMap<Unit>()
            repeat(size) { map[it] = Unit }
        }

        val stats = IntObjectMapStats.snapshot(firstInstance)
        assertEquals(listOf(0, 2, 4, 8, 16), stats.peakSizes)
        assertEquals(5, stats.count)
        assertEquals(16, stats.max)
        assertEquals(4.0, stats.median)
        assertEquals(8.0, stats.p75)
        println("Known map sizes: $stats")
    }

    @Test
    fun peakSurvivesRemovalClearAndReplacement() {
        val source = intObjectMapOf(10, Unit, 11, Unit)
        val firstInstance = IntObjectMapStats.instanceCount
        val map = MutableIntObjectMap<Unit>(initialCapacity = 128)
        assertEquals(listOf(0), IntObjectMapStats.snapshot(firstInstance).peakSizes)

        repeat(8) { map[it] = Unit }
        repeat(100) { map.put(0, Unit) }
        map.remove(0)
        map.removeIf { key, _ -> key % 2 == 0 }
        map.clear()
        map.putAll(source)
        assertEquals(2, map.size)

        val stats = IntObjectMapStats.snapshot(firstInstance)
        assertEquals(listOf(8), stats.peakSizes)
        assertEquals(8, stats.max)
        assertEquals(8.0, stats.median)
        assertEquals(8.0, stats.p75)
    }

    @Test
    fun interpolatesPercentilesAndExcludesInstancesBeforeCheckpoint() {
        val olderMap = MutableIntObjectMap<Unit>()
        val firstInstance = IntObjectMapStats.instanceCount

        for (size in listOf(0, 2, 6, 10)) {
            val map = MutableIntObjectMap<Unit>()
            repeat(size) { map[it] = Unit }
        }
        repeat(100) { olderMap[it] = Unit }

        val stats = IntObjectMapStats.snapshot(firstInstance)
        assertEquals(4, stats.count)
        assertEquals(10, stats.max)
        assertEquals(4.0, stats.median)
        assertEquals(7.0, stats.p75)
    }

    @Test
    fun emptyMeasurementHasNoPercentiles() {
        val stats = IntObjectMapStats.snapshot(IntObjectMapStats.instanceCount)
        assertEquals(0, stats.count)
        assertNull(stats.max)
        assertNull(stats.median)
        assertNull(stats.p75)
    }

    @Test
    fun callStacksAreOptionalAndAttributeCreationAndGrowth() {
        emptyIntObjectMap<Unit>()
        val firstInstance = IntObjectMapStats.instanceCount
        MutableIntObjectMap<Unit>()[0] = Unit
        IntObjectMapStats.captureCallStacks = true
        try {
            MutableIntObjectMap<Unit>()
            val populated = MutableIntObjectMap<Unit>()
            populated[1] = Unit
            val firstWrite = IntObjectMapStats.allocations(firstInstance).last().peakStack
            assertNotNull(firstWrite)
            populated[1] = Unit
            populated.clear()
            assertEquals(firstWrite, IntObjectMapStats.allocations(firstInstance).last().peakStack)

            val allocations = IntObjectMapStats.allocations(firstInstance)
            assertEquals(listOf(1, 0, 1), allocations.map { it.peakSize })
            assertNull(allocations[0].creationStack)
            assertNull(allocations[0].peakStack)
            assertTrue(assertNotNull(allocations[1].creationStack).isNotBlank())
            assertNull(allocations[1].peakStack)
            assertTrue(assertNotNull(allocations[2].creationStack).isNotBlank())
        } finally {
            IntObjectMapStats.captureCallStacks = false
        }
    }
}
