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

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * [ParkedWindowResources] is the generic park/take/dispose bookkeeping extracted from
 * `MacOsApplication.reusableNativeWindowResources` (AIR-6085 WS2 task 6) so it is unit-testable
 * without any native window/KDT dependency, and so WS3/WS4 platforms can reuse it verbatim.
 */
@Category(HeadlessTest::class)
class ParkedWindowResourcesTest {

    private class FakeResource(val label: String) {
        var destroyCount = 0
    }

    private val warnings = mutableListOf<String>()
    private val registry = ParkedWindowResources<FakeResource>(warn = { warnings += it })

    @Test
    fun parkTakeRoundTripRemovesTheEntry() {
        val id = LightweightWindowId(1)
        val resource = FakeResource("a")

        registry.park(id, resource)
        val taken = registry.take(id)

        assertEquals(resource, taken)
        assertFalse(registry.peekContains(id))
        assertTrue(registry.isEmpty)
        assertTrue(warnings.isEmpty(), "a successful round trip must not warn: $warnings")
    }

    @Test
    fun takeOnMissingIdWarnsAndReturnsNull() {
        val id = LightweightWindowId(2)

        val taken = registry.take(id)

        assertNull(taken)
        assertEquals(1, warnings.size, "exactly one warning expected: $warnings")
        val message = warnings.single()
        assertTrue(message.contains("$id"), "warning should name the missing id: $message")
        assertTrue(
            message.contains("parked") || message.contains("keys"),
            "warning should include a summary hint (parked ids) for triage: $message",
        )
    }

    @Test
    fun disposeWithDestroysExactlyOnceAndSecondCallIsFalseWithoutDestroy() {
        val id = LightweightWindowId(3)
        val resource = FakeResource("b")
        registry.park(id, resource)

        val firstResult = registry.disposeWith(id) { it.destroyCount++ }
        val secondResult = registry.disposeWith(id) { it.destroyCount++ }

        assertTrue(firstResult, "first disposeWith should find and remove the parked entry")
        assertFalse(secondResult, "second disposeWith on the same id must report a miss")
        assertEquals(1, resource.destroyCount, "destroyer must run exactly once")
        assertTrue(registry.isEmpty)
    }

    @Test
    fun drainWithDestroysAllAndEmpties() {
        val idA = LightweightWindowId(4)
        val idB = LightweightWindowId(5)
        val resourceA = FakeResource("c")
        val resourceB = FakeResource("d")
        registry.park(idA, resourceA)
        registry.park(idB, resourceB)

        val destroyed = mutableListOf<FakeResource>()
        registry.drainWith {
            it.destroyCount++
            destroyed += it
        }

        assertEquals(setOf(resourceA, resourceB), destroyed.toSet())
        assertEquals(1, resourceA.destroyCount)
        assertEquals(1, resourceB.destroyCount)
        assertTrue(registry.isEmpty)
        assertTrue(registry.keys.isEmpty())
    }

    @Test
    fun peekContainsDoesNotRemove() {
        val id = LightweightWindowId(6)
        val resource = FakeResource("e")
        registry.park(id, resource)

        val firstCheck = registry.peekContains(id)
        val secondCheck = registry.peekContains(id)

        assertTrue(firstCheck)
        assertTrue(secondCheck, "peekContains must not remove the entry it just found")
        assertEquals(resource, registry.take(id), "the entry must still be there for take()")
    }
}
